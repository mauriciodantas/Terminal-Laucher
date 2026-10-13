package app.lawnchair.command

import app.lawnchair.util.Texts

/**
 * The pattern of a custom action: a regular expression that text typed alone, without the letter,
 * has to match in full for the action to take it ("[A-Z]{2}\d{9}BR" for a tracking code). Patterns
 * come from users, shared packs and the AI, so they run with a size and a time limit: one that
 * backtracks for too long counts as no match instead of freezing the bar at every key.
 */
object CommandPattern {

    const val MAX_LENGTH = 200

    /** Longer lines are not matched at all; nothing worth a pattern is that long. */
    private const val MAX_INPUT = 300

    private const val TIMEOUT_MS = 25L

    /** Ordinary lines a pattern must leave alone, or every word typed would go to its action. */
    private val ORDINARY = listOf("", " ", "a", "1", "abrir chrome", "hello world", "tema escuro", "12*3")

    private val cache = object : LinkedHashMap<String, Regex?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Regex?>?) = size > 32
    }

    /** The compiled [pattern], or null when it is empty or not a valid regular expression. */
    fun compile(pattern: String): Regex? {
        if (pattern.isBlank() || pattern.length > MAX_LENGTH) return null
        return synchronized(cache) { cache.getOrPut(pattern) { runCatching { Regex(pattern) }.getOrNull() } }
    }

    /** Null when [pattern] can be saved (an empty one is fine), otherwise why not. */
    fun validate(pattern: String): String? {
        if (pattern.isEmpty()) return null
        if (pattern.length > MAX_LENGTH) return Texts.get(R.string.cmd_err_pattern_long, MAX_LENGTH)
        val regex = compile(pattern) ?: return Texts.get(R.string.cmd_err_pattern_invalid)
        if (ORDINARY.any { matches(regex, it) }) return Texts.get(R.string.cmd_err_pattern_broad)
        return null
    }

    /** [pattern] when it can be used, otherwise empty: an imported action keeps coming in without it. */
    fun usable(pattern: String): String = pattern.takeIf { validate(it) == null }.orEmpty()

    /** Whether [regex] matches all of [text]; false when it takes longer than the limit. */
    fun matches(regex: Regex, text: String): Boolean {
        if (text.length > MAX_INPUT) return false
        return try {
            regex.matches(Deadline(text, System.nanoTime() + TIMEOUT_MS * 1_000_000))
        } catch (_: TimedOut) {
            false
        }
    }

    /** Whether [pattern] takes [text] typed alone. */
    fun takes(pattern: String, text: String): Boolean = compile(pattern)?.let { matches(it, text.trim()) } == true

    private class TimedOut : RuntimeException()

    /** Text that stops a match running past [deadline]: the regex engine reads it a character at a time. */
    private class Deadline(private val text: CharSequence, private val deadline: Long) : CharSequence {
        override val length: Int get() = text.length

        override fun get(index: Int): Char {
            if (System.nanoTime() > deadline) throw TimedOut()
            return text[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = Deadline(text.subSequence(startIndex, endIndex), deadline)

        override fun toString(): String = text.toString()
    }
}
