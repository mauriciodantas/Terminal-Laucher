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

/** Runs an [ActionKind.API] action: fills its address, fetches it and shows the answer. */
object ApiCall {

    const val MAX_BYTES = 256 * 1024
    private const val TIMEOUT_MS = 10_000

    /** Any `{...}` of a template, filled with a sample so the address can be checked before it runs. */
    private val PLACEHOLDER = Regex("\\{[^{}]*\\}")

    /** True when [url] is an https address with a host: an API action reaches nothing else. */
    fun isAllowed(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty()
    }.getOrDefault(false)

    /** Whether the template of [action] can be run at all, whatever is typed. */
    fun isUsable(action: CustomAction): Boolean = action.api != null && isAllowed(action.template.trim().replace(PLACEHOLDER, "x"))

    /** The host [action] asks, for the preview and the import screen ("viacep.com.br"). */
    fun host(action: CustomAction): String = runCatching { URI(action.template.trim().replace(PLACEHOLDER, "x")).host }.getOrNull().orEmpty()

    /** The address [action] fetches for [text], or null when it is not an https address. */
    fun url(action: CustomAction, text: String, today: LocalDate): String? = action.template.trim()
        .replace("{text}", encode(text.trim()))
        .replace("{year}", today.year.toString())
        .takeIf { isAllowed(it) }

    /** Fetches [action] for [text] and returns what the bar shows: the answer, or why there is none. Blocking. */
    fun run(action: CustomAction, text: String, today: LocalDate = LocalDate.now()): String {
        val spec = action.api ?: return Texts.get(R.string.cmd_api_invalid)
        val url = url(action, text, today) ?: return Texts.get(R.string.cmd_api_invalid)
        val host = host(action)
        return runCatching {
            val (code, body) = get(url)
            if (code !in 200..299) return Texts.get(R.string.cmd_api_failed, host, "HTTP $code")
            ApiFormat.render(body, spec, today) ?: Texts.get(R.string.cmd_api_empty)
        }.getOrElse { Texts.get(R.string.cmd_api_failed, host, it.message ?: it.javaClass.simpleName) }
    }

    /** The status and the body of a GET of [url], at most [MAX_BYTES]. Blocking. */
    fun get(url: String): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "TerminalLauncher")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    check(out.size() <= MAX_BYTES) { Texts.get(R.string.cmd_api_too_big) }
                }
                out.toByteArray().decodeToString()
            }.orEmpty()
            return code to body
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
