package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
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

    @Test fun modes() {
        assertEquals(CommandAction.Ai(AiMode.CHAT, "quanto é 1 pé em metros"), run("ia quanto é 1 pé em metros").action)
        assertEquals(CommandAction.Ai(AiMode.RUN, "silêncio até as 8"), run("ia fazer silêncio até as 8").action)
        assertEquals(CommandAction.Ai(AiMode.CREATE, "busca no imdb"), run("ai create busca no imdb").action)
        assertNull(run("ia fazer").action)
        assertNull(run("ia").action)
    }

    @Test fun offersTheModes() {
        assertEquals(2, run("ia").suggestions.size)
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
