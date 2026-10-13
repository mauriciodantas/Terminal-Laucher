package app.lawnchair.command

/**
 * Whether text typed in the bar is a phone number: "99999-9999", "(11) 99999-9999", "11999999999",
 * "+55 11 99999-9999". Pure, so it can be unit tested. A short number ("42", "1000-250") or a time
 * is not a phone.
 */
object PhoneNumber {

    private val INTERNATIONAL = Regex("^\\+\\d[\\d\\s().-]*\\d$")

    /** Optional country code, optional area code (in parentheses or not), then 4-5 + 4 digits. */
    private val LOCAL = Regex("^(?:\\d{2}[\\s.-]?)?(?:\\(?\\d{2}\\)?[\\s.-]?)?\\d{4,5}[\\s.-]?\\d{4}$")

    /** [text] trimmed when it is a phone number, or null. */
    fun of(text: String): String? {
        val t = text.trim()
        val digits = t.count { it.isDigit() }
        if (digits !in 8..15) return null
        return t.takeIf { INTERNATIONAL.matches(it) || LOCAL.matches(it) }
    }

    /**
     * Splits "11 99999-9999 chego logo" into the number and the message, or null when [arg] does
     * not start with a number. A ":" is an explicit separator, as with a contact name.
     */
    fun split(arg: String): Pair<String, String>? {
        val trimmed = arg.trim()
        val colon = Regex(":(?!//)").find(trimmed)?.range?.first
        if (colon != null) return of(trimmed.substring(0, colon))?.let { it to trimmed.substring(colon + 1).trim() }
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (k in minOf(words.size, MAX_WORDS) downTo 1) {
            of(words.take(k).joinToString(" "))?.let { return it to words.drop(k).joinToString(" ") }
        }
        return null
    }

    /** "+55 11 99999 9999" is the most words a number spans. */
    private const val MAX_WORDS = 4
}
