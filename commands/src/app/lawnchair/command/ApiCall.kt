package app.lawnchair.command

import app.lawnchair.util.Texts
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * How an [ActionKind.API] action shows what its address answers. The action's template is an https
 * address fetched with GET, `{text}` filled with what was typed and `{year}` with the current year;
 * the answer must be JSON.
 *
 * [format] is the text shown: `{field}` is the value at that path of the answer ("{city}",
 * "{address.street}", "{0.name}") and `{field|date}` shows an ISO date as the short local date and
 * weekday. A part between " · " whose fields are all empty is left out, and so is a line.
 * [list] is the path of an array in the answer, each item shown with [format], at most [max]. With
 * [upcoming], the field of each item holding an ISO date, items before today are left out.
 */
data class ApiSpec(
    val format: String,
    val list: String = "",
    val max: Int = 5,
    val upcoming: String = "",
)

/** Turns a JSON answer into the text of an [ApiSpec]. Pure, so it can be unit tested. */
object ApiFormat {

    const val MAX_ITEMS = 20

    private val FIELD = Regex("\\{([^{}|\\s]+)(?:\\|([a-z]+))?\\}")

    private const val SEPARATOR = " · "

    /** The text [spec] makes of [body], or null when the answer is not JSON or has nothing to show. */
    fun render(body: String, spec: ApiSpec, today: LocalDate, locale: Locale = Locale.getDefault()): String? {
        val root = parse(body) ?: return null
        val items: List<Any?> = if (spec.list.isEmpty()) {
            (root as? JSONArray)?.toList() ?: listOf(root)
        } else {
            (at(root, spec.list) as? JSONArray)?.toList() ?: return null
        }
        val kept = if (spec.upcoming.isEmpty()) {
            items
        } else {
            items.filter { item -> date(at(item, spec.upcoming))?.let { !it.isBefore(today) } ?: true }
        }
        return kept.take(spec.max.coerceIn(1, MAX_ITEMS))
            .mapNotNull { renderItem(it, spec.format, locale) }
            .joinToString("\n")
            .takeIf { it.isNotBlank() }
    }

    /** [format] filled from [item], or null when every field it names is empty. */
    fun renderItem(item: Any?, format: String, locale: Locale = Locale.getDefault()): String? = format.lines().mapNotNull { line ->
        line.split(SEPARATOR).mapNotNull { part ->
            var fields = 0
            var filled = 0
            val text = FIELD.replace(part) { m ->
                fields++
                val value = show(at(item, m.groupValues[1]), m.groupValues[2], locale)
                if (value.isNotEmpty()) filled++
                value
            }.trim()
            text.takeIf { (fields == 0 || filled > 0) && it.isNotEmpty() }
        }.joinToString(SEPARATOR).takeIf { it.isNotEmpty() }
    }.joinToString("\n").takeIf { it.isNotEmpty() }

    /** The JSON value of [body], or null when it is not JSON. */
    private fun parse(body: String): Any? = runCatching { JSONTokener(body.trim()).nextValue() }.getOrNull()
        ?.takeIf { it is JSONObject || it is JSONArray }

    /** The value at a dotted [path] ("address.city", "0.name"), or null. */
    fun at(node: Any?, path: String): Any? = path.split('.').filter { it.isNotEmpty() }.fold(node) { current, key ->
        when (current) {
            is JSONObject -> current.opt(key)
            is JSONArray -> key.toIntOrNull()?.let { current.opt(it) }
            else -> null
        }?.takeUnless { it == JSONObject.NULL }
    }

    private fun show(value: Any?, filter: String, locale: Locale): String = when {
        value == null || value is JSONObject || value is JSONArray -> ""

        filter == "date" -> date(value)?.let { formatDate(it, locale) } ?: value.toString()

        // Decimals come as Double on Android and may come as BigDecimal elsewhere.
        value is Double || value is Float || value is java.math.BigDecimal -> CalcEvaluator.format((value as Number).toDouble())

        else -> value.toString().trim()
    }

    private fun date(value: Any?): LocalDate? = (value as? String)?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** "15/11/2026 dom" in Portuguese, "11/15/26 Sun" in English. */
    fun formatDate(date: LocalDate, locale: Locale): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)) +
        " " + date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).trimEnd('.')

    private fun JSONArray.toList(): List<Any?> = (0 until length()).map { opt(it) }
}

/**
 * Runs an [ActionKind.API] action. With a provider, the template is a path on it and the provider
 * attaches the credentials; without one (actions saved before providers existed) the template is a
 * full https address asked with no credentials at all.
 */
object ApiCall {

    /** Any `{...}` of a template, filled with a sample so the address can be checked before it runs. */
    private val PLACEHOLDER = Regex("\\{[^{}]*\\}")

    /** True when [url] is an https address with a host. */
    fun isAllowed(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty()
    }.getOrDefault(false)

    /** Whether [action] can run at all: it says what it shows, and reaches a provider or an https address. */
    fun isUsable(action: CustomAction): Boolean = action.api != null &&
        (action.provider.isNotEmpty() || isAllowed(action.template.trim().replace(PLACEHOLDER, "x")))

    /**
     * The provider [action] goes through: the one it names, or for an address of its own an open
     * provider for just that host. Null when it names one that does not exist.
     */
    fun providerOf(action: CustomAction, providers: List<ApiProvider>): ApiProvider? {
        if (action.provider.isNotEmpty()) return Providers.of(action.provider, providers)
        val url = action.template.trim().replace(PLACEHOLDER, "x")
        val host = Providers.hostOf(url) ?: return null
        return ApiProvider(id = "", name = host, baseUrl = "https://$host")
    }

    /** The host [action] asks, for the preview and the import screen ("viacep.com.br"). */
    fun host(action: CustomAction, providers: List<ApiProvider> = Providers.BUILT_IN): String = providerOf(action, providers)?.host
        ?: action.provider

    /** The path or address of [action] with what was typed and the year filled in. */
    fun path(action: CustomAction, text: String, today: LocalDate): String = action.template.trim()
        .replace("{text}", encode(text.trim()))
        .replace("{year}", today.year.toString())

    /** The address [action] asks for [text], without any secret; null when it reaches nothing allowed. */
    fun url(action: CustomAction, text: String, today: LocalDate, providers: List<ApiProvider> = Providers.BUILT_IN): String? {
        val provider = providerOf(action, providers) ?: return null
        val path = path(action, text, today)
        return ProviderRequests.urlOf(provider, path).takeIf { ProviderRequests.isAllowed(provider, it) }
            ?.let { ProviderRequests.shown(provider, path) }
    }

    /**
     * Fetches [action] for [text] and returns what the bar shows: the answer, or why there is none.
     * [secret] gives the saved secret of a provider id. Blocking.
     */
    fun run(
        action: CustomAction,
        text: String,
        providers: List<ApiProvider>,
        secret: (String) -> String?,
        today: LocalDate = LocalDate.now(),
    ): String {
        val spec = action.api ?: return Texts.get(R.string.cmd_api_invalid)
        val provider = providerOf(action, providers) ?: return Texts.get(R.string.cmd_provider_missing, action.provider)
        val result = HttpRunner.get(provider, path(action, text, today), secret(provider.id))
        return when (result) {
            is HttpResult.Answer -> ApiFormat.render(result.body, spec, today) ?: Texts.get(R.string.cmd_api_empty)
            else -> HttpRunner.describe(result, provider)
        }
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

/** How a request to a provider ended. */
sealed interface HttpResult {
    data class Answer(val code: Int, val body: String) : HttpResult
    data class Failed(val code: Int) : HttpResult

    /** 401 or 403: the provider turned the credentials down. */
    data class Refused(val code: Int) : HttpResult
    data class NeedsSecret(val provider: ApiProvider) : HttpResult
    data class Blocked(val reason: String) : HttpResult
    data class Error(val message: String) : HttpResult
}

/**
 * Sends GET requests for a provider. Redirects are followed by hand, so the credentials never go to
 * a host the provider does not own. Blocking.
 */
object HttpRunner {

    const val MAX_BYTES = 256 * 1024
    private const val TIMEOUT_MS = 10_000
    private const val MAX_REDIRECTS = 3

    fun get(provider: ApiProvider, path: String, secret: String?): HttpResult {
        val request = when (val p = ProviderRequests.prepare(provider, path, secret)) {
            is Prepared.Ready -> p.request
            is Prepared.NeedsSecret -> return HttpResult.NeedsSecret(p.provider)
            is Prepared.Blocked -> return HttpResult.Blocked(p.reason)
        }
        return runCatching { send(provider, request) }.getOrElse { HttpResult.Error(it.message ?: it.javaClass.simpleName) }
    }

    private fun send(provider: ApiProvider, first: HttpRequest): HttpResult {
        var url = first.url
        repeat(MAX_REDIRECTS + 1) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "TerminalLauncher")
                first.headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return HttpResult.Failed(code)
                    val next = URL(URL(url), location).toString()
                    if (!ProviderRequests.mayFollow(provider, next)) {
                        return HttpResult.Blocked(Texts.get(R.string.cmd_provider_host_blocked, Providers.hostOf(next) ?: next, provider.name))
                    }
                    url = next
                    return@repeat
                }
                if (code == 401 || code == 403) return HttpResult.Refused(code)
                if (code !in 200..299) return HttpResult.Failed(code)
                return HttpResult.Answer(code, read(connection))
            } finally {
                connection.disconnect()
            }
        }
        return HttpResult.Error(Texts.get(R.string.cmd_provider_redirects))
    }

    private fun read(connection: HttpURLConnection): String = connection.inputStream.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            check(out.size() <= MAX_BYTES) { Texts.get(R.string.cmd_api_too_big) }
        }
        out.toByteArray().decodeToString()
    }

    /** What the bar shows for a request that brought no answer. */
    fun describe(result: HttpResult, provider: ApiProvider): String = when (result) {
        is HttpResult.Answer -> "HTTP ${result.code}"
        is HttpResult.Failed -> Texts.get(R.string.cmd_api_failed, provider.host, "HTTP ${result.code}")
        is HttpResult.Refused -> Texts.get(R.string.cmd_provider_refused, provider.name.uppercase(), result.code)
        is HttpResult.NeedsSecret -> Texts.get(R.string.cmd_provider_no_key, provider.name.uppercase())
        is HttpResult.Blocked -> result.reason
        is HttpResult.Error -> Texts.get(R.string.cmd_api_failed, provider.host, result.message)
    }
}
