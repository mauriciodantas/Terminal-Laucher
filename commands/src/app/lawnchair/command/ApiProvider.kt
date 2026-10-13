package app.lawnchair.command

import app.lawnchair.util.Texts
import java.net.URI
import java.net.URLEncoder
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** Where an API key goes: in a header ("X-Api-Key: …") or in the query ("?appid=…"). */
enum class KeyPlacement { HEADER, QUERY }

/** How a provider proves who is asking. The secret itself is never part of it: see [ProviderRequests]. */
sealed interface AuthSpec {

    /** Open APIs: ViaCEP, BrasilAPI, Open-Meteo. */
    data object None : AuthSpec

    /** A key under [name], in a header or in the query, after [prefix] ("Token "). */
    data class ApiKey(val name: String, val placement: KeyPlacement = KeyPlacement.HEADER, val prefix: String = "") : AuthSpec

    /** `Authorization: Bearer <secret>`, for personal access tokens. */
    data object Bearer : AuthSpec

    /** `Authorization: Basic`, with [user] here and the password as the secret. */
    data class Basic(val user: String) : AuthSpec

    val needsSecret: Boolean get() = this != None
}

/**
 * A service the API actions talk to. Commands name it by [id] and give only a path; the provider
 * holds where it lives and how to authenticate, so the secret is attached by the provider alone and
 * only for its own [hosts]. A shared pack carries the provider without the secret.
 */
data class ApiProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    /** Other hosts the provider answers from; the host of [baseUrl] is always one. */
    val hosts: List<String> = emptyList(),
    val auth: AuthSpec = AuthSpec.None,
    /** Fixed headers, never secret ("Accept-Language: pt-BR"). */
    val headers: Map<String, String> = emptyMap(),
    /** What "test connection" asks; empty asks [baseUrl]. */
    val testPath: String = "",
    /** Where to get a key, shown when one is asked for. */
    val keyHelp: String = "",
    /** The built-in pack the provider came with; empty for the user's own. */
    val pack: String = "",
) {
    val host: String get() = Providers.hostOf(baseUrl).orEmpty()

    /** Every host that may receive the request, lower case. */
    val allowedHosts: Set<String> get() = (hosts + host).map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
}

object Providers {

    /** Open-Meteo, which "clima" uses: the forecast and the geocoding answer from two hosts. */
    val OPEN_METEO = ApiProvider(
        id = "open-meteo",
        name = "Open-Meteo",
        baseUrl = "https://api.open-meteo.com",
        hosts = listOf("geocoding-api.open-meteo.com"),
        testPath = "/v1/forecast?latitude=0&longitude=0&current=temperature_2m",
    )

    /** Providers the app's own commands need; they cannot be removed or changed. */
    val BUILT_IN: List<ApiProvider> = listOf(OPEN_METEO)

    fun isBuiltIn(id: String): Boolean = BUILT_IN.any { it.id == id }

    /** The built-in providers and the saved ones; a saved one cannot take a built-in id. */
    fun all(saved: List<ApiProvider>): List<ApiProvider> = BUILT_IN + saved.filter { !isBuiltIn(it.id) }

    fun of(id: String, providers: List<ApiProvider>): ApiProvider? = providers.firstOrNull { it.id == id }

    fun hostOf(url: String): String? = runCatching { URI(url.trim()).host?.lowercase() }.getOrNull()

    private val ID = Regex("^[a-z0-9][a-z0-9_-]{0,39}$")

    /** Null when [id] can name a new provider, otherwise why not. [ignore] is the one being edited. */
    fun validateId(id: String, existing: List<ApiProvider>, ignore: String? = null): String? = when {
        !ID.matches(id) -> Texts.get(R.string.cmd_provider_err_id)
        isBuiltIn(id) || existing.any { it.id == id && it.id != ignore } -> Texts.get(R.string.cmd_err_in_use, id)
        else -> null
    }

    /** Null when [provider] can be saved or imported, otherwise why not. */
    fun validate(provider: ApiProvider): String? {
        if (!ID.matches(provider.id)) return Texts.get(R.string.cmd_provider_err_id)
        if (provider.name.isBlank()) return Texts.get(R.string.cmd_provider_err_name)
        val uri = runCatching { URI(provider.baseUrl.trim()) }.getOrNull()
        if (uri == null || !uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrEmpty()) return Texts.get(R.string.cmd_provider_err_url)
        if (provider.hosts.any { !it.trim().matches(Regex("^[a-zA-Z0-9.-]+$")) }) return Texts.get(R.string.cmd_provider_err_hosts)
        val auth = provider.auth
        if (auth is AuthSpec.ApiKey && auth.name.isBlank()) return Texts.get(R.string.cmd_provider_err_key_name)
        if (auth is AuthSpec.Basic && auth.user.isBlank()) return Texts.get(R.string.cmd_provider_err_user)
        // A header could carry what the auth should, unseen: secrets only go through the auth.
        if (provider.headers.keys.any { it.equals("Authorization", ignoreCase = true) || it.equals("Cookie", ignoreCase = true) }) {
            return Texts.get(R.string.cmd_provider_err_headers)
        }
        return null
    }

    /** The kind of authentication in a word, for lists and the import screen. */
    fun authLabel(auth: AuthSpec): String = when (auth) {
        AuthSpec.None -> Texts.get(R.string.cmd_provider_auth_none)
        is AuthSpec.ApiKey -> Texts.get(R.string.cmd_provider_auth_key) + " · " + auth.name + " (" + auth.placement.name.lowercase() + ")"
        AuthSpec.Bearer -> "Bearer token"
        is AuthSpec.Basic -> "Basic · " + auth.user
    }
}

/** A request ready to send, and the address as it may be shown, with the secret hidden. */
data class HttpRequest(val url: String, val headers: Map<String, String>, val shown: String)

/** What a provider makes of a path: a request, a missing secret, or an address it must not reach. */
sealed interface Prepared {
    data class Ready(val request: HttpRequest) : Prepared
    data class NeedsSecret(val provider: ApiProvider) : Prepared
    data class Blocked(val reason: String) : Prepared
}

/**
 * Builds the requests of a provider. Pure, so the rule that matters is unit tested: the secret is
 * only ever attached to an https address on one of the provider's own hosts.
 */
object ProviderRequests {

    const val MASK = "•••"

    /** [path] on [provider]: relative to its base address, or a full https address on one of its hosts. */
    fun urlOf(provider: ApiProvider, path: String): String {
        val p = path.trim()
        if (p.startsWith("https://", ignoreCase = true) || p.startsWith("http://", ignoreCase = true)) return p
        if (p.isEmpty()) return provider.baseUrl.trim()
        return provider.baseUrl.trim().trimEnd('/') + "/" + p.trimStart('/')
    }

    /** Whether [url] is an address [provider] may send to. */
    fun isAllowed(provider: ApiProvider, url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        return host in provider.allowedHosts
    }

    fun prepare(provider: ApiProvider, path: String, secret: String?): Prepared {
        val url = urlOf(provider, path)
        if (!isAllowed(provider, url)) return Prepared.Blocked(Texts.get(R.string.cmd_provider_host_blocked, Providers.hostOf(url) ?: url, provider.name))
        val auth = provider.auth
        if (auth.needsSecret && secret.isNullOrEmpty()) return Prepared.NeedsSecret(provider)
        val headers = LinkedHashMap(provider.headers)
        var full = url
        var shown = url
        when (auth) {
            AuthSpec.None -> {}

            is AuthSpec.ApiKey -> when (auth.placement) {
                KeyPlacement.HEADER -> headers[auth.name] = auth.prefix + secret

                KeyPlacement.QUERY -> {
                    val separator = if ('?' in url) "&" else "?"
                    val name = encode(auth.name)
                    full = url + separator + name + "=" + encode(auth.prefix + secret)
                    shown = url + separator + name + "=" + MASK
                }
            }

            AuthSpec.Bearer -> headers["Authorization"] = "Bearer $secret"

            is AuthSpec.Basic -> headers["Authorization"] = "Basic " + Base64.getEncoder().encodeToString("${auth.user}:$secret".toByteArray())
        }
        return Prepared.Ready(HttpRequest(full, headers, shown))
    }

    /** The address as the bar shows it before running: nothing secret, whatever the auth. */
    fun shown(provider: ApiProvider, path: String): String = when (val p = prepare(provider, path, MASK)) {
        is Prepared.Ready -> p.request.shown
        else -> urlOf(provider, path)
    }

    /**
     * Whether a redirect to [location] may be followed. With a secret attached it must stay on the
     * provider's hosts; an open provider may go to any https address.
     */
    fun mayFollow(provider: ApiProvider, location: String): Boolean = if (provider.auth.needsSecret) {
        isAllowed(provider, location)
    } else {
        runCatching { URI(location).scheme.equals("https", ignoreCase = true) }.getOrDefault(false)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

/** Providers as JSON, for the store and for packs. A secret is never written, nor read back. */
object ProviderCodec {

    fun toJson(providers: List<ApiProvider>): JSONArray = JSONArray().also { array ->
        providers.forEach { p ->
            array.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("base", p.baseUrl)
                    .put("hosts", JSONArray(p.hosts))
                    .put("auth", authJson(p.auth))
                    .put("headers", JSONObject(p.headers as Map<*, *>))
                    .put("test", p.testPath)
                    .put("help", p.keyHelp)
                    .put("pack", p.pack),
            )
        }
    }

    fun fromJson(array: JSONArray): List<ApiProvider> = (0 until array.length()).mapNotNull { i ->
        runCatching {
            val o = array.getJSONObject(i)
            val hosts = o.optJSONArray("hosts") ?: JSONArray()
            val headers = o.optJSONObject("headers") ?: JSONObject()
            ApiProvider(
                id = o.getString("id").trim().lowercase(),
                name = o.getString("name").trim(),
                baseUrl = o.getString("base").trim(),
                hosts = (0 until hosts.length()).map { hosts.getString(it) },
                auth = auth(o.optJSONObject("auth")),
                headers = headers.keys().asSequence().associateWith { headers.getString(it) },
                testPath = o.optString("test", ""),
                keyHelp = o.optString("help", "").takeIf { it.startsWith("https://") }.orEmpty(),
                pack = o.optString("pack", ""),
            )
        }.getOrNull()
    }

    private fun authJson(auth: AuthSpec): JSONObject = when (auth) {
        AuthSpec.None -> JSONObject().put("type", "none")
        is AuthSpec.ApiKey -> JSONObject().put("type", "key").put("name", auth.name).put("in", auth.placement.name.lowercase()).put("prefix", auth.prefix)
        AuthSpec.Bearer -> JSONObject().put("type", "bearer")
        is AuthSpec.Basic -> JSONObject().put("type", "basic").put("user", auth.user)
    }

    private fun auth(o: JSONObject?): AuthSpec = when (o?.optString("type")) {
        "key" -> AuthSpec.ApiKey(
            o.getString("name"),
            if (o.optString("in") == "query") KeyPlacement.QUERY else KeyPlacement.HEADER,
            o.optString("prefix", ""),
        )

        "bearer" -> AuthSpec.Bearer

        "basic" -> AuthSpec.Basic(o.getString("user"))

        else -> AuthSpec.None
    }
}
