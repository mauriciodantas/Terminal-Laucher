package app.lawnchair.command

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The AI services "ia" talks to. Each speaks its own HTTP dialect, which [AiProtocol] translates,
 * so the rest of the bar never knows which one the user picked. [defaultModel] is used while the
 * user leaves the model blank.
 */
enum class AiProvider(val label: String, val defaultModel: String, val keyHint: String) {
    OPENAI("OpenAI", "gpt-5-mini", "sk-…"),
    ANTHROPIC("Claude", "claude-opus-5-5", "sk-ant-…"),

    // An alias Google moves to each new Flash, so the default does not go stale.
    GEMINI("Gemini", "gemini-flash-latest", "AIza…"),
    ;

    companion object {
        /** The service a key belongs to, by its prefix; null when the key does not tell. */
        fun guess(key: String): AiProvider? = key.trim().let {
            when {
                it.startsWith("sk-ant-") -> ANTHROPIC
                it.startsWith("AIza") -> GEMINI
                it.startsWith("sk-") -> OPENAI
                else -> null
            }
        }
    }
}

/**
 * The service, the user's key and the model; a blank [model] means the service's default.
 * [timeoutSeconds] is how long the bar waits for an answer before giving up.
 */
data class AiConfig(
    val provider: AiProvider,
    val key: String,
    val model: String = "",
    val timeoutSeconds: Int = DEFAULT_TIMEOUT,
) {
    companion object {
        const val DEFAULT_TIMEOUT = 30
        val TIMEOUT_RANGE = 5..300
    }

    val modelName: String get() = model.trim().ifEmpty { provider.defaultModel }

    /** "Claude · claude-opus-5-5", for the settings and the preview. */
    val label: String get() = provider.label + " · " + modelName
}

/**
 * One message of a conversation. [raw] is the service's own reply content, sent back unchanged on
 * the next request when the service asks for that (Claude does); null for the user's messages.
 */
data class AiTurn(val fromUser: Boolean, val text: String, val raw: String? = null)

/** An HTTP call ready to send. */
data class AiHttpRequest(val url: String, val headers: Map<String, String>, val body: String)

/** What came back: the text to show and what to keep in the conversation. */
data class AiReply(val text: String, val turn: AiTurn)

/** [seconds] is the wait that ran out, for a timeout. */
class AiException(message: String, val seconds: Int = 0) : Exception(message)

/** The request and response shapes of each service. Pure, so it can be unit tested. */
object AiProtocol {

    private const val MAX_TOKENS = 16000
    private const val ANTHROPIC_VERSION = "2023-06-01"
    private const val ANTHROPIC_FALLBACK_BETA = "server-side-fallback-2026-07-01"

    /** With [stream], the service sends the reply in pieces as server-sent events, read by [AiStream]. */
    fun request(config: AiConfig, system: String, turns: List<AiTurn>, stream: Boolean = false): AiHttpRequest = when (config.provider) {
        AiProvider.OPENAI -> AiHttpRequest(
            "https://api.openai.com/v1/chat/completions",
            mapOf("Authorization" to "Bearer " + config.key.trim()),
            JSONObject()
                .put("model", config.modelName)
                .put(
                    "messages",
                    JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", system))
                        turns.forEach { put(JSONObject().put("role", if (it.fromUser) "user" else "assistant").put("content", it.text)) }
                    },
                )
                .apply { if (stream) put("stream", true) }
                .toString(),
        )

        AiProvider.ANTHROPIC -> {
            val model = config.modelName
            val headers = buildMap {
                put("x-api-key", config.key.trim())
                put("anthropic-version", ANTHROPIC_VERSION)
                if (hasServerFallback(model)) put("anthropic-beta", ANTHROPIC_FALLBACK_BETA)
            }
            AiHttpRequest(
                "https://api.anthropic.com/v1/messages",
                headers,
                JSONObject()
                    .put("model", model)
                    .put("max_tokens", MAX_TOKENS)
                    .put("system", system)
                    .put(
                        "messages",
                        JSONArray().apply {
                            turns.forEach { turn ->
                                // The reply goes back as it came, thinking blocks included: the service rejects an edited history.
                                val content: Any = if (!turn.fromUser && turn.raw != null) JSONArray(turn.raw) else turn.text
                                put(JSONObject().put("role", if (turn.fromUser) "user" else "assistant").put("content", content))
                            }
                        },
                    )
                    .apply { if (hasServerFallback(model)) put("fallbacks", "default") }
                    .apply { if (stream) put("stream", true) }
                    .toString(),
            )
        }

        AiProvider.GEMINI -> AiHttpRequest(
            "https://generativelanguage.googleapis.com/v1beta/models/" + config.modelName +
                if (stream) ":streamGenerateContent?alt=sse" else ":generateContent",
            mapOf("x-goog-api-key" to config.key.trim()),
            JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put(
                    "contents",
                    JSONArray().apply {
                        turns.forEach {
                            put(
                                JSONObject()
                                    .put("role", if (it.fromUser) "user" else "model")
                                    .put("parts", JSONArray().put(JSONObject().put("text", it.text))),
                            )
                        }
                    },
                )
                .toString(),
        )
    }

    /** The Claude models whose refusals another model can take over on the server. */
    private fun hasServerFallback(model: String): Boolean = model.startsWith("claude-opus-5") || model.startsWith("claude-fable-5") || model.startsWith("claude-sonnet-5-5")

    /** The reply in [body]; throws [AiException] when it holds no text. */
    fun parse(provider: AiProvider, body: String): AiReply {
        val o = JSONObject(body)
        return when (provider) {
            AiProvider.OPENAI -> {
                val text = o.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
                reply(text)
            }

            AiProvider.ANTHROPIC -> {
                if (o.optString("stop_reason") == "refusal") throw AiException("refusal")
                val content = o.getJSONArray("content")
                val blocks = (0 until content.length()).map { content.getJSONObject(it) }
                val text = blocks.filter { it.optString("type") == "text" }.joinToString("") { it.optString("text") }
                // After a fallback, only text blocks from before the switch go back to the service.
                val lastFallback = blocks.indexOfLast { it.optString("type") == "fallback" }
                val echo = blocks.filterIndexed { i, block -> i > lastFallback || block.optString("type") == "text" }
                reply(text, JSONArray(echo).toString())
            }

            AiProvider.GEMINI -> {
                val candidate = o.getJSONArray("candidates").getJSONObject(0)
                val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
                val text = (0 until parts.length()).map { parts.getJSONObject(it) }
                    .filter { !it.optBoolean("thought") }
                    .joinToString("") { it.optString("text") }
                if (text.isBlank() && candidate.optString("finishReason") == "SAFETY") throw AiException("refusal")
                reply(text)
            }
        }
    }

    private fun reply(text: String, raw: String? = null): AiReply {
        val clean = text.trim()
        if (clean.isEmpty()) throw AiException("empty")
        return AiReply(clean, AiTurn(fromUser = false, text = clean, raw = raw))
    }

    /** The message an error response carries, which all three services put under "error". */
    fun errorMessage(code: Int, body: String): String {
        val message = runCatching {
            val error = JSONObject(body).opt("error")
            when (error) {
                is JSONObject -> error.optString("message")
                is String -> error
                else -> ""
            }
        }.getOrNull().orEmpty()
        return "HTTP $code" + if (message.isNotBlank()) " · " + message.trim().lineSequence().first() else ""
    }
}

/**
 * Reads a reply sent as server-sent events. The pieces are put back together into the body the
 * service would have sent in one go, and [finish] hands that to [AiProtocol.parse], so a streamed
 * reply ends exactly like one that was not: same text, same refusals, and for Claude the same
 * blocks (thinking and signatures included) to send back on the next question. Pure, so it can be
 * unit tested.
 */
class AiStream(private val provider: AiProvider) {

    private var data = StringBuilder()

    // OpenAI: the text so far.
    private val openAiText = StringBuilder()

    // Claude: the content blocks by index, and why the reply stopped.
    private val blocks = sortedMapOf<Int, JSONObject>()
    private var stopReason = ""

    // Gemini: the parts in the order they came, and why the reply stopped.
    private val parts = mutableListOf<JSONObject>()
    private var finishReason = ""

    /** The text shown so far. */
    val text: String
        get() = when (provider) {
            AiProvider.OPENAI -> openAiText.toString()
            AiProvider.ANTHROPIC -> blocks.values.filter { it.optString("type") == "text" }.joinToString("") { it.optString("text") }
            AiProvider.GEMINI -> parts.filter { !it.optBoolean("thought") }.joinToString("") { it.optString("text") }
        }

    /**
     * Takes one line of the stream. Returns true when the text changed. Throws [AiException] when
     * the service reports an error in the middle of the reply.
     */
    fun feed(line: String): Boolean = when {
        line.startsWith("data:") -> {
            if (data.isNotEmpty()) data.append('\n')
            data.append(line.removePrefix("data:").removePrefix(" "))
            false
        }

        line.isEmpty() -> dispatch()

        // "event:", "id:" and ":" comments: the data says all that is needed.
        else -> false
    }

    /** The whole reply, once the stream is over. */
    fun finish(): AiReply {
        dispatch()
        val body = when (provider) {
            AiProvider.OPENAI -> JSONObject().put(
                "choices",
                JSONArray().put(JSONObject().put("message", JSONObject().put("content", openAiText.toString()))),
            )

            AiProvider.ANTHROPIC -> JSONObject()
                .put("stop_reason", stopReason)
                .put("content", JSONArray(blocks.values.toList()))

            AiProvider.GEMINI -> JSONObject().put(
                "candidates",
                JSONArray().put(
                    JSONObject()
                        .put("content", JSONObject().put("parts", JSONArray(parts)))
                        .put("finishReason", finishReason),
                ),
            )
        }
        return AiProtocol.parse(provider, body.toString())
    }

    private fun dispatch(): Boolean {
        val payload = data.toString().trim()
        data = StringBuilder()
        if (payload.isEmpty() || payload == "[DONE]") return false
        val event = runCatching { JSONObject(payload) }.getOrNull() ?: return false
        event.opt("error")?.let { error ->
            val message = (error as? JSONObject)?.let { it.optString("message").ifBlank { it.optString("type") } } ?: error.toString()
            throw AiException(message.ifBlank { "error" })
        }
        val before = text.length
        when (provider) {
            AiProvider.OPENAI -> event.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
                ?.takeIf { it.has("content") && !it.isNull("content") }
                ?.let { openAiText.append(it.optString("content")) }

            AiProvider.ANTHROPIC -> anthropic(event)

            AiProvider.GEMINI -> {
                val candidate = event.optJSONArray("candidates")?.optJSONObject(0) ?: return false
                candidate.optString("finishReason").takeIf { it.isNotEmpty() }?.let { finishReason = it }
                val list = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
                (0 until list.length()).forEach { parts += list.getJSONObject(it) }
            }
        }
        return text.length != before
    }

    private fun anthropic(event: JSONObject) {
        when (event.optString("type")) {
            // Some replies (a fallback) may already carry blocks when they start.
            "message_start" -> event.optJSONObject("message")?.optJSONArray("content")?.let { content ->
                (0 until content.length()).forEach { blocks[it] = content.getJSONObject(it) }
            }

            "content_block_start" -> blocks[event.optInt("index")] = event.getJSONObject("content_block")

            "content_block_delta" -> {
                val block = blocks.getOrPut(event.optInt("index")) { JSONObject() }
                val delta = event.optJSONObject("delta") ?: return
                when (delta.optString("type")) {
                    "text_delta" -> block.concat("text", delta.optString("text"))

                    "thinking_delta" -> block.concat("thinking", delta.optString("thinking"))

                    "signature_delta" -> block.concat("signature", delta.optString("signature"))

                    "citations_delta" -> {
                        val citations = block.optJSONArray("citations") ?: JSONArray().also { block.put("citations", it) }
                        delta.optJSONObject("citation")?.let { citations.put(it) }
                    }
                }
            }

            "message_delta" -> event.optJSONObject("delta")?.optString("stop_reason")
                ?.takeIf { it.isNotEmpty() && it != "null" }
                ?.let { stopReason = it }
        }
    }

    private fun JSONObject.concat(key: String, piece: String) {
        put(key, optString(key) + piece)
    }
}

/** Sends conversations to the service the user set up. */
object AiClient {

    suspend fun send(config: AiConfig, system: String, turns: List<AiTurn>): AiReply = call(config, AiProtocol.request(config, system, turns)) { body ->
        AiProtocol.parse(config.provider, body.readText())
    }

    /**
     * Like [send], with the reply in pieces: [onText] gets the whole text so far each time it grows,
     * on the main thread. Only for the conversation: a command or a pack is read once it is complete.
     * The time limit then counts between two pieces, so a long answer that keeps coming is not cut off.
     */
    suspend fun stream(config: AiConfig, system: String, turns: List<AiTurn>, onText: (String) -> Unit): AiReply = call(config, AiProtocol.request(config, system, turns, stream = true)) { body ->
        val reader = AiStream(config.provider)
        body.lineSequence().forEach { line ->
            if (reader.feed(line)) {
                val text = reader.text
                withContext(Dispatchers.Main) { onText(text) }
            }
        }
        reader.finish()
    }

    private suspend fun call(config: AiConfig, request: AiHttpRequest, read: suspend (java.io.BufferedReader) -> AiReply): AiReply = withContext(Dispatchers.IO) {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            // A busy service can hold the connection for minutes before giving up, so the bar gives up first.
            connection.readTimeout = config.timeoutSeconds.coerceIn(AiConfig.TIMEOUT_RANGE) * 1000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.outputStream.use { it.write(request.body.toByteArray()) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw AiException(AiProtocol.errorMessage(code, body))
            }
            connection.inputStream.bufferedReader().use { read(it) }
        } catch (e: java.net.SocketTimeoutException) {
            throw AiException(TIMEOUT, config.timeoutSeconds)
        } finally {
            connection.disconnect()
        }
    }

    const val TIMEOUT = "timeout"

    /** What went wrong, in words for the user: a refusal, an empty answer, a timeout or the service's own message. */
    fun describe(context: Context, error: Throwable): String = when (error.message) {
        "refusal" -> context.getString(R.string.cmd_ai_refused)
        "empty" -> context.getString(R.string.cmd_ai_empty_reply)
        TIMEOUT -> context.getString(R.string.cmd_ai_timeout, (error as? AiException)?.seconds ?: AiConfig.DEFAULT_TIMEOUT)
        else -> context.getString(R.string.cmd_ai_failed, error.message ?: error.javaClass.simpleName)
    }
}

/**
 * The service, key and model the user set up. They live in a file of their own that the system
 * backup leaves out, so the key never leaves the device except to the service itself.
 */
object AiStore {
    private const val STORE = "nostromo_ai"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_KEY = "key"
    private const val KEY_MODEL = "model"
    private const val KEY_TIMEOUT = "timeout"

    private fun prefs(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    /** The setup, or null until a key is saved: then "ia" stays locked. */
    fun load(context: Context): AiConfig? {
        val p = prefs(context)
        val key = p.getString(KEY_KEY, null)?.takeIf { it.isNotBlank() } ?: return null
        val provider = p.getString(KEY_PROVIDER, null)?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() }
            ?: AiProvider.guess(key)
            ?: AiProvider.OPENAI
        return AiConfig(provider, key, p.getString(KEY_MODEL, null).orEmpty(), p.getInt(KEY_TIMEOUT, AiConfig.DEFAULT_TIMEOUT))
    }

    fun save(context: Context, config: AiConfig?) {
        prefs(context).edit().apply {
            if (config == null) {
                clear()
            } else {
                putString(KEY_PROVIDER, config.provider.name)
                putString(KEY_KEY, config.key.trim())
                putString(KEY_MODEL, config.model.trim())
                putInt(KEY_TIMEOUT, config.timeoutSeconds.coerceIn(AiConfig.TIMEOUT_RANGE))
            }
        }.apply()
    }
}
