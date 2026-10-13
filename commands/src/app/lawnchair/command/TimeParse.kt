package app.lawnchair.command

import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents
import java.text.DateFormatSymbols
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Calendar
import java.util.Locale

/**
 * Times and durations as they are typed in the command bar: "10m", "1h30", "90s" for how long,
 * "18h", "18:30", "0630" for a clock time, "seg-sex" for the days of an alarm. Pure, so it can be
 * unit tested.
 */
object TimeParse {

    private val units = listOf(
        "h", "hr", "hrs", "hora", "horas", "hour", "hours",
        "m", "min", "mins", "minuto", "minutos", "minute", "minutes",
        "s", "seg", "sec", "secs", "segundo", "segundos", "second", "seconds",
    ).sortedByDescending { it.length }

    // A unit ends where letters end, so "1h30" splits into "1h" and "30".
    private val part = Regex("(\\d+)\\s*(" + units.joinToString("|") + ")?(?![a-z])")

    /**
     * Seconds in "10m", "1h30", "1h 30m", "90s", "2 h". A number without a unit after hours is
     * minutes ("1h30"); a lone number is minutes too, when [bareMinutes]. Null when it is not a
     * duration or it is zero.
     */
    fun duration(text: String, bareMinutes: Boolean = true): Int? {
        val t = text.trim().lowercase().foldAccents()
        if (t.isEmpty()) return null
        var i = 0
        var total = 0L
        var lastUnit: Char? = null
        var parts = 0
        while (i < t.length) {
            while (i < t.length && t[i].isWhitespace()) i++
            if (i >= t.length) break
            val m = part.matchAt(t, i) ?: return null
            if (m.value.isEmpty()) return null
            val n = m.groupValues[1].toLong()
            val unitText = m.groupValues[2]
            val unit = when {
                unitText.isEmpty() -> when (lastUnit) {
                    'h' -> 'm'
                    'm' -> 's'
                    null -> if (bareMinutes) 'm' else return null
                    else -> return null
                }

                unitText.startsWith("h") -> 'h'

                unitText.startsWith("s") -> 's'

                else -> 'm'
            }
            if (lastUnit != null && rank(unit) <= rank(lastUnit)) return null
            total += n * when (unit) {
                'h' -> 3600
                'm' -> 60
                else -> 1
            }
            lastUnit = unit
            parts++
            i += m.value.length
        }
        if (parts == 0 || total <= 0 || total > MAX_SECONDS) return null
        return total.toInt()
    }

    private fun rank(unit: Char) = when (unit) {
        'h' -> 0
        'm' -> 1
        else -> 2
    }

    /** True when [text] names minutes or seconds explicitly ("30m", "45s"), so it can only be a duration. */
    fun isExplicitDuration(text: String): Boolean {
        val t = text.trim().lowercase().foldAccents()
        return duration(t, bareMinutes = false) != null && minuteOrSecond.containsMatchIn(t)
    }

    /** A number with a minute or second unit: what makes "1h30m" a duration while "1h30" is a time. */
    private val minuteOrSecond = Regex("\\d\\s*(" + units.filter { !it.startsWith("h") }.joinToString("|") + ")(?![a-z])")

    /** "18 horas", "18 hours": the hour word after a clock time is dropped. */
    private val hourWord = Regex("\\s+(h|hora|horas|hour|hours)$")

    private val clockPattern = Regex("^(\\d{1,2})(?:\\s*[:h.]\\s*(\\d{2}))?\\s*h?$")

    /** "18h", "18:30", "18h30", "6", "0630", "1830" as a clock time; null when it is not one. */
    fun clock(text: String): LocalTime? {
        val t = text.trim().lowercase().foldAccents().replace(hourWord, "")
        if (t.isEmpty()) return null
        if (t.all { it.isDigit() } && t.length in 3..4) {
            val hh = t.dropLast(2).toInt()
            val mm = t.takeLast(2).toInt()
            return if (hh in 0..23 && mm in 0..59) LocalTime.of(hh, mm) else null
        }
        val m = clockPattern.matchEntire(t) ?: return null
        val hh = m.groupValues[1].toInt()
        val mm = m.groupValues[2].ifEmpty { "0" }.toInt()
        return if (hh in 0..23 && mm in 0..59) LocalTime.of(hh, mm) else null
    }

    /** The next moment the clock shows [time] after [now], or that time tomorrow when [tomorrow]. */
    fun nextAt(time: LocalTime, now: LocalDateTime, tomorrow: Boolean = false): LocalDateTime {
        val today = now.toLocalDate().atTime(time)
        return when {
            tomorrow -> today.plusDays(1)
            today.isAfter(now) -> today
            else -> today.plusDays(1)
        }
    }

    /** "amanhã" and "tomorrow", and the word of the app language. */
    fun isTomorrow(word: String): Boolean = word.foldAccents() in words(R.string.cmd_word_tomorrow) + setOf("amanha", "tomorrow")

    /** "em" in "em 30m", "in" in "in 30m". */
    fun isIn(word: String): Boolean = word.foldAccents() in words(R.string.cmd_word_in) + setOf("em", "daqui", "in")

    /** "às" in "às 22h", "at" in "at 10pm". */
    fun isAt(word: String): Boolean = word.foldAccents() in words(R.string.cmd_word_at) + setOf("as", "at")

    /** "quando" in "quando carregar: wifi". */
    fun isWhen(word: String): Boolean = word.foldAccents() in words(R.string.cmd_word_when) + setOf("quando", "when")

    internal fun words(id: Int): Set<String> = Texts.get(id).split(',').map { it.trim().foldAccents() }.filter { it.isNotEmpty() }.toSet()

    /** "1h 30min", "45s": how long [seconds] is, short. */
    fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return listOfNotNull(
            h.takeIf { it > 0 }?.let { "${it}h" },
            m.takeIf { it > 0 }?.let { "${it}min" },
            s.takeIf { it > 0 }?.let { "${it}s" },
        ).joinToString(" ")
    }

    fun formatClock(time: LocalDateTime, now: LocalDateTime): String {
        val clock = "%02d:%02d".format(time.hour, time.minute)
        return when (time.toLocalDate()) {
            now.toLocalDate() -> clock
            now.toLocalDate().plusDays(1) -> Texts.get(R.string.cmd_time_tomorrow, clock)
            else -> "%02d/%02d %s".format(time.dayOfMonth, time.monthValue, clock)
        }
    }

    // ---- days of the week ----

    private fun dayNames(): Map<String, Int> {
        val names = LinkedHashMap<String, Int>()
        for (locale in listOf(Locale.getDefault(), Locale.forLanguageTag("pt"), Locale.ENGLISH)) {
            val symbols = DateFormatSymbols.getInstance(locale)
            for (day in Calendar.SUNDAY..Calendar.SATURDAY) {
                listOf(symbols.weekdays[day], symbols.shortWeekdays[day]).forEach { raw ->
                    val name = raw.orEmpty().lowercase().foldAccents().trimEnd('.')
                    if (name.isNotEmpty()) {
                        names.putIfAbsent(name, day)
                        // "segunda-feira" is typed "segunda" or "seg".
                        names.putIfAbsent(name.substringBefore('-'), day)
                        if (name.length > 3) names.putIfAbsent(name.take(3), day)
                    }
                }
            }
        }
        return names
    }

    private val WEEKDAYS = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
    private val WEEKEND = listOf(Calendar.SATURDAY, Calendar.SUNDAY)
    private val EVERY_DAY = WEEKDAYS + WEEKEND

    /**
     * The days of "seg-sex", "seg,qua,sex", "dias úteis", "fds", "todo dia", as [Calendar] day
     * numbers in week order from Monday. Null when [text] does not name days.
     */
    fun days(text: String): List<Int>? {
        val t = text.trim().lowercase().foldAccents()
        if (t.isEmpty()) return null
        if (t in words(R.string.cmd_days_weekdays) + setOf("dias uteis", "weekdays")) return WEEKDAYS
        if (t in words(R.string.cmd_days_weekend) + setOf("fds", "fim de semana", "weekend")) return WEEKEND
        if (t in words(R.string.cmd_days_daily) + setOf("todo dia", "todos os dias", "diario", "daily", "every day")) return EVERY_DAY
        val names = dayNames()
        val result = linkedSetOf<Int>()
        val spaced = t.replace(Regex("\\s*-\\s*|\\s+(a|to|ate)\\s+"), "-")
        for (piece in spaced.split(Regex("[,\\s]+")).filter { it.isNotEmpty() && it !in CONNECTORS }) {
            val range = piece.split('-').takeIf { '-' in piece }
            if (range != null && range.size == 2) {
                val from = names[range[0]] ?: return null
                val to = names[range[1]] ?: return null
                val order = EVERY_DAY
                var k = order.indexOf(from)
                while (true) {
                    result += order[k]
                    if (order[k] == to) break
                    k = (k + 1) % order.size
                }
            } else {
                result += names[piece] ?: return null
            }
        }
        return EVERY_DAY.filter { it in result }.ifEmpty { null }
    }

    /** "seg, qua, sex" in the app language. */
    fun formatDays(days: List<Int>): String {
        if (days == EVERY_DAY) return Texts.get(R.string.cmd_days_every)
        val symbols = DateFormatSymbols.getInstance(Locale.getDefault())
        return days.joinToString(", ") { symbols.shortWeekdays[it].trimEnd('.').lowercase() }
    }

    private val CONNECTORS = setOf("e", "and")

    private const val MAX_SECONDS = 7L * 24 * 3600
}
