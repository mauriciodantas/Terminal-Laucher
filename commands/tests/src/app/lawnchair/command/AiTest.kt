package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The "ia" command, the request and response shapes of each service and the reading of replies. */
class AiTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private val apps = listOf("Chrome", "Kindle", "Maps").map { AppEntry(it, "pkg/$it") }
    private val contacts = listOf(ContactEntry("Ana Souza", "111"))
    private val ready = CommandExtras(ai = "Claude · claude-opus-5-5")

    private fun run(text: String, x: CommandExtras = ready) = CommandEngine.analyze(text, apps, contacts, true, CustomActions.DEFAULTS, emptyList(), x)

    // ---- the command ----

    @Test fun lockedUntilAKeyIsSaved() {
        val a = run("ia oi", CommandExtras())
        assertEquals(Tone.WARN, a.tone)
        assertEquals(CommandAction.Ai(AiMode.SETUP, ""), a.action)
        // Not offered while locked, offered once a key is saved.
        assertTrue(run("?", CommandExtras()).suggestions.none { it.completion.trim() == BuiltInCommand.AI.word })
        assertTrue(run("?").suggestions.any { it.completion.trim() == BuiltInCommand.AI.word })
    }

    @Test fun providerSetupWorksWhileLocked() {
        val locked = CommandExtras()
        val a = run("ia provedor claude sk-ant-abcdef123456 claude-opus-5-5", locked)
        assertEquals(Tone.OK, a.tone)
        assertEquals(CommandAction.AiSetup(AiProvider.ANTHROPIC, "sk-ant-abcdef123456", "claude-opus-5-5"), a.action)
        // The preview shows the key masked, never whole.
        assertFalse(a.preview.contains("sk-ant-abcdef123456"))
        // A key alone tells its service by the prefix.
        assertEquals(CommandAction.AiSetup(AiProvider.GEMINI, "AIzaXYZ12345", null), run("ai provider AIzaXYZ12345", locked).action)
        // A service without a key is not enough while none is saved.
        assertNull(run("ia provedor claude", locked).action)
        // With a key saved, a service or a model alone keeps the key.
        assertEquals(CommandAction.AiSetup(AiProvider.OPENAI, null, null), run("ia provedor openai").action)
        assertEquals(CommandAction.AiSetup(null, null, "gpt-5"), run("ia provedor gpt-5").action)
        // The key is a secret: the action never prints it.
        assertFalse(CommandAction.AiSetup(null, "sk-secret", null).toString().contains("sk-secret"))
    }

    @Test fun providerSetupRemoveAndOffers() {
        assertEquals(CommandAction.AiSetup(null, null, null, remove = true), run("ia provedor remover").action)
        assertNull(run("ia provedor remover", CommandExtras()).action)
        assertTrue(run("ia provedor ").suggestions.any { it.label == "Claude" })
        assertTrue(run("ia p").suggestions.any { it.completion.startsWith("ia provedor") || it.completion.startsWith("ai provider") })
        assertEquals(Tone.ERROR, run("ia provedor claude sk-ant-abc123 modelo extra").tone)
    }

    @Test fun modes() {
        assertEquals(CommandAction.Ai(AiMode.CHAT, "quanto é 1 pé em metros"), run("ia quanto é 1 pé em metros").action)
        assertEquals(CommandAction.Ai(AiMode.RUN, "silêncio até as 8"), run("ia fazer silêncio até as 8").action)
        assertEquals(CommandAction.Ai(AiMode.CREATE, "busca no imdb"), run("ai create busca no imdb").action)
        assertNull(run("ia fazer").action)
        assertNull(run("ia").action)
    }

    @Test fun offersTheModes() {
        assertEquals(5, run("ia").suggestions.size)
        assertEquals(1, run("ia fa").suggestions.size)
    }

    @Test fun variablesAreFilledBeforeSending() {
        assertEquals(CommandAction.Ai(AiMode.CHAT, "resuma texto copiado"), run("ia resuma {clip}", ready.copy(clipboard = "texto copiado")).action)
    }

    // ---- the services ----

    private val turns = listOf(AiTurn(true, "oi"), AiTurn(false, "olá", raw = "[{\"type\":\"text\",\"text\":\"olá\"}]"), AiTurn(true, "tudo bem?"))

    @Test fun openAiRequest() {
        val r = AiProtocol.request(AiConfig(AiProvider.OPENAI, " sk-abc "), "sys", turns)
        assertEquals("Bearer sk-abc", r.headers["Authorization"])
        val body = JSONObject(r.body)
        assertEquals(AiProvider.OPENAI.defaultModel, body.getString("model"))
        val messages = body.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("assistant", messages.getJSONObject(2).getString("role"))
        assertEquals(4, messages.length())
    }

    @Test fun anthropicRequestSendsTheReplyBackUnchanged() {
        val r = AiProtocol.request(AiConfig(AiProvider.ANTHROPIC, "sk-ant-x", "claude-opus-5-5"), "sys", turns)
        assertEquals("sk-ant-x", r.headers["x-api-key"])
        assertNotNull(r.headers["anthropic-version"])
        val body = JSONObject(r.body)
        assertEquals("sys", body.getString("system"))
        assertEquals("default", body.getString("fallbacks"))
        val assistant = body.getJSONArray("messages").getJSONObject(1)
        assertEquals("olá", assistant.getJSONArray("content").getJSONObject(0).getString("text"))
        // A model without server fallback gets neither the parameter nor the header.
        val haiku = AiProtocol.request(AiConfig(AiProvider.ANTHROPIC, "k", "claude-haiku-5-5"), "s", turns)
        assertFalse(JSONObject(haiku.body).has("fallbacks"))
        assertNull(haiku.headers["anthropic-beta"])
    }

    @Test fun geminiRequest() {
        val r = AiProtocol.request(AiConfig(AiProvider.GEMINI, "AIza1", "gemini-x"), "sys", turns)
        assertTrue(r.url.endsWith("/models/gemini-x:generateContent"))
        val body = JSONObject(r.body)
        assertEquals("model", body.getJSONArray("contents").getJSONObject(1).getString("role"))
        assertEquals("sys", body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
    }

    @Test fun parsesReplies() {
        assertEquals("oi", AiProtocol.parse(AiProvider.OPENAI, """{"choices":[{"message":{"content":" oi "}}]}""").text)
        val claude = AiProtocol.parse(
            AiProvider.ANTHROPIC,
            """{"stop_reason":"end_turn","content":[{"type":"thinking","thinking":"","signature":"s"},{"type":"text","text":"oi"}]}""",
        )
        assertEquals("oi", claude.text)
        // The thinking block goes back with the reply.
        assertTrue(claude.turn.raw!!.contains("thinking"))
        assertEquals("oi", AiProtocol.parse(AiProvider.GEMINI, """{"candidates":[{"content":{"parts":[{"text":"oi"}]}}]}""").text)
    }

    @Test(expected = AiException::class)
    fun refusalIsAnError() {
        AiProtocol.parse(AiProvider.ANTHROPIC, """{"stop_reason":"refusal","content":[]}""")
    }

    // ---- the conversation in pieces ----

    private fun streamed(provider: AiProvider, events: List<String>, seen: MutableList<String> = mutableListOf()): AiReply {
        val stream = AiStream(provider)
        events.forEach { event ->
            ("$event\n\n").lines().forEach { if (stream.feed(it)) seen += stream.text }
        }
        return stream.finish()
    }

    @Test fun streamRequests() {
        assertTrue(JSONObject(AiProtocol.request(AiConfig(AiProvider.OPENAI, "sk-x"), "s", turns, stream = true).body).getBoolean("stream"))
        assertTrue(JSONObject(AiProtocol.request(AiConfig(AiProvider.ANTHROPIC, "sk-ant-x"), "s", turns, stream = true).body).getBoolean("stream"))
        assertTrue(AiProtocol.request(AiConfig(AiProvider.GEMINI, "AIza", "gemini-x"), "s", turns, stream = true).url.endsWith(":streamGenerateContent?alt=sse"))
        assertFalse(JSONObject(AiProtocol.request(AiConfig(AiProvider.OPENAI, "sk-x"), "s", turns).body).has("stream"))
    }

    @Test fun openAiStreamEndsLikeTheWholeReply() {
        val seen = mutableListOf<String>()
        val reply = streamed(
            AiProvider.OPENAI,
            listOf(
                """data: {"choices":[{"delta":{"role":"assistant","content":""}}]}""",
                """data: {"choices":[{"delta":{"content":" o"}}]}""",
                """data: {"choices":[{"delta":{"content":"i "}}]}""",
                """data: {"choices":[{"delta":{},"finish_reason":"stop"}]}""",
                "data: [DONE]",
            ),
            seen,
        )
        assertEquals(listOf(" o", " oi "), seen)
        assertEquals(AiProtocol.parse(AiProvider.OPENAI, """{"choices":[{"message":{"content":" oi "}}]}"""), reply)
    }

    @Test fun claudeStreamKeepsThinkingAndSignatureForTheNextQuestion() {
        val seen = mutableListOf<String>()
        val reply = streamed(
            AiProvider.ANTHROPIC,
            listOf(
                "event: message_start\ndata: {\"type\":\"message_start\",\"message\":{\"content\":[]}}",
                """data: {"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"","signature":""}}""",
                """data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"pensa"}}""",
                """data: {"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"ndo"}}""",
                """data: {"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"sig=="}}""",
                """data: {"type":"content_block_stop","index":0}""",
                "event: ping\ndata: {\"type\":\"ping\"}",
                """data: {"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}""",
                """data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"o"}}""",
                """data: {"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"i"}}""",
                """data: {"type":"content_block_stop","index":1}""",
                """data: {"type":"message_delta","delta":{"stop_reason":"end_turn"}}""",
                """data: {"type":"message_stop"}""",
            ),
            seen,
        )
        assertEquals(listOf("o", "oi"), seen)
        val whole = AiProtocol.parse(
            AiProvider.ANTHROPIC,
            """{"stop_reason":"end_turn","content":[{"type":"thinking","thinking":"pensando","signature":"sig=="},{"type":"text","text":"oi"}]}""",
        )
        assertEquals(whole.text, reply.text)
        assertEquals(JSONArray(whole.turn.raw).toString(), JSONArray(reply.turn.raw).toString())
        // And it goes back on the next request as it came.
        val next = AiProtocol.request(AiConfig(AiProvider.ANTHROPIC, "k"), "s", listOf(AiTurn(true, "a"), reply.turn, AiTurn(true, "b")))
        val block = JSONObject(next.body).getJSONArray("messages").getJSONObject(1).getJSONArray("content").getJSONObject(0)
        assertEquals("sig==", block.getString("signature"))
        assertEquals("pensando", block.getString("thinking"))
    }

    @Test(expected = AiException::class)
    fun claudeStreamRefusalIsAnError() {
        streamed(
            AiProvider.ANTHROPIC,
            listOf(
                """data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
                """data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Eu"}}""",
                """data: {"type":"message_delta","delta":{"stop_reason":"refusal"}}""",
            ),
        )
    }

    @Test fun errorInTheMiddleOfTheStream() {
        val error = runCatching {
            streamed(AiProvider.ANTHROPIC, listOf("""data: {"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""))
        }.exceptionOrNull()
        assertEquals("Overloaded", error?.message)
    }

    @Test fun geminiStreamLeavesThoughtsOut() {
        val reply = streamed(
            AiProvider.GEMINI,
            listOf(
                """data: {"candidates":[{"content":{"role":"model","parts":[{"text":"hmm","thought":true}]}}]}""",
                """data: {"candidates":[{"content":{"role":"model","parts":[{"text":"o"}]}}]}""",
                """data: {"candidates":[{"content":{"role":"model","parts":[{"text":"i"}]},"finishReason":"STOP"}]}""",
            ),
        )
        assertEquals(AiProtocol.parse(AiProvider.GEMINI, """{"candidates":[{"content":{"parts":[{"text":"oi"}]}}]}"""), reply)
    }

    @Test fun errorMessages() {
        assertEquals("HTTP 401 · Incorrect API key", AiProtocol.errorMessage(401, """{"error":{"message":"Incorrect API key"}}"""))
        assertEquals("HTTP 500", AiProtocol.errorMessage(500, "oops"))
    }

    @Test fun guessesTheServiceFromTheKey() {
        assertEquals(AiProvider.ANTHROPIC, AiProvider.guess("sk-ant-api03-x"))
        assertEquals(AiProvider.OPENAI, AiProvider.guess("sk-proj-x"))
        assertEquals(AiProvider.GEMINI, AiProvider.guess("AIzaSy"))
        assertNull(AiProvider.guess("abc"))
    }

    // ---- the prompts ----

    @Test fun promptLeavesPrivateDataOut() {
        val system = AiPrompts.system(AiMode.RUN, AiContext(aliases = listOf(CommandAlias("mae", "ligar maria")), apps = listOf("Kindle")))
        assertTrue(system.contains("Kindle"))
        assertTrue(system.contains("mae"))
        assertFalse(system.contains("maria"))
        assertFalse(system.contains("Ana Souza"))
    }

    @Test fun readsAProposedCommand() {
        assertEquals(AiCommand("tema escuro", "escurece"), AiPrompts.command("```json\n{\"command\": \"tema escuro\", \"note\": \"escurece\"}\n```"))
        assertEquals(AiCommand(null, "não dá"), AiPrompts.command("{\"command\": null, \"note\": \"não dá\"}"))
        assertNull(AiPrompts.command("sem json").command)
    }

    @Test fun readsANewCommandAsAPack() {
        val reply = """Aqui: {"actions": [{"letter": "imdb", "label": "IMDb", "kind": "INTENT", "template": "https://www.imdb.com/find/?q={text}",
            "packages": [], "arg": "TEXT"}], "aliases": [{"name": "noite", "expansion": "tema escuro"}], "note": "busca"}"""
        val (text, note) = AiPrompts.pack(reply)!!
        assertEquals("busca", note)
        val pack = CommandPacks.decode(text)
        assertEquals("imdb", pack.actions.single().letter)
        assertEquals("noite", pack.aliases.single().name)
        assertNull(AiPrompts.pack("{\"actions\": [], \"aliases\": []}"))
    }
}
