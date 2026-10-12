package app.lawnchair.command

/**
 * Whether a line typed in the bar is a web address ("github.com", "www.g1.com.br/tecnologia",
 * "https://x.com/a?b=1", "192.168.0.1:8080"), and the link to open for it. Pure, so it can be unit
 * tested. A bare number ("1.5"), an e-mail or anything with spaces is not an address.
 */
object WebAddress {

    private val SCHEME = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)

    /** host.tld with an optional port, path, query or fragment; the last label has letters only. */
    private val DOMAIN = Regex(
        "^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,24}(?::\\d{1,5})?(?:[/?#]\\S*)?$",
        RegexOption.IGNORE_CASE,
    )

    private val IPV4 = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?(?:[/?#]\\S*)?$")

    /** The link [text] stands for, with https:// added when it has no scheme; null when it is not an address. */
    fun of(text: String): String? {
        val t = text.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() } || '@' in t.substringBefore('/')) return null
        if (SCHEME.matches(t)) return t
        val host = t.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
        val address = when {
            DOMAIN.matches(t) -> true
            IPV4.matches(t) -> host.split('.').all { (it.toIntOrNull() ?: 256) <= 255 }
            else -> false
        }
        // A local address is reached over plain http; any other site over https.
        return if (!address) {
            null
        } else if (IPV4.matches(t)) {
            "http://$t"
        } else {
            "https://$t"
        }
    }
}
