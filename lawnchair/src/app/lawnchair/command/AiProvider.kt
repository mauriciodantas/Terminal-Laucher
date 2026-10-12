package app.lawnchair.command

import android.content.Context
import com.android.launcher3.R
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

    fun request(config: AiConfig, system: String, turns: List<AiTurn>): AiHttpRequest = when (config.provider) {
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
                    .toString(),
            )
        }

        AiProvider.GEMINI -> AiHttpRequest(
            "https://generativelanguage.googleapis.com/v1beta/models/" + config.modelName + ":generateContent",
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

/** Sends conversations to the service the user set up. */
object AiClient {

    suspend fun send(config: AiConfig, system: String, turns: List<AiTurn>): AiReply = withContext(Dispatchers.IO) {
        val request = AiProtocol.request(config, system, turns)
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
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw AiException(AiProtocol.errorMessage(code, body))
            AiProtocol.parse(config.provider, body)
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
