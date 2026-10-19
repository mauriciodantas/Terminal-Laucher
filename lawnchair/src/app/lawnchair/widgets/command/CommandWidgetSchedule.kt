package app.lawnchair.widgets.command

import java.util.Calendar
import java.util.TimeZone

/**
 * When a command widget runs its command again. Free of Android types so the rules can be unit
 * tested. [Kind.INTERVAL] counts from the last run; the calendar kinds ([Kind.DAILY],
 * [Kind.WEEKLY], [Kind.MONTHLY]) run at [hour]:[minute] on the days they name, and a run missed
 * while the widget was not on screen is made up for as soon as it is.
 */
data class CommandWidgetSchedule(
    val kind: Kind = Kind.MANUAL,
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    val hour: Int = 8,
    val minute: Int = 0,
    /** [Calendar] day-of-week numbers, for [Kind.WEEKLY]. */
    val weekdays: Set<Int> = setOf(Calendar.MONDAY),
    /** 1 to 31, for [Kind.MONTHLY]; a month without that day runs on its last. */
    val dayOfMonth: Int = 1,
) {
    enum class Kind { MANUAL, INTERVAL, DAILY, WEEKLY, MONTHLY }

    val isCalendar: Boolean get() = kind == Kind.DAILY || kind == Kind.WEEKLY || kind == Kind.MONTHLY

    /** Whether the command must run now, given when it last did (0 when it never did). */
    fun isDue(now: Long, lastRun: Long, zone: TimeZone = TimeZone.getDefault()): Boolean {
        if (lastRun == 0L) return true
        return when (kind) {
            Kind.MANUAL -> false
            Kind.INTERVAL -> now - lastRun >= intervalMinutes * 60_000L
            else -> lastOccurrence(now, zone)?.let { lastRun < it } ?: false
        }
    }

    /** When the command runs next, or null when it only runs on a tap. */
    fun nextRun(now: Long, lastRun: Long, zone: TimeZone = TimeZone.getDefault()): Long? = when (kind) {
        Kind.MANUAL -> null
        Kind.INTERVAL -> lastRun + intervalMinutes * 60_000L
        else -> nextOccurrence(now, zone)
    }

    /** The latest scheduled moment at or before [now]; null for the kinds without a calendar. */
    fun lastOccurrence(now: Long, zone: TimeZone = TimeZone.getDefault()): Long? {
        if (!isCalendar) return null
        for (back in 0..SEARCH_DAYS) {
            val at = occurrenceOn(now, -back, zone) ?: continue
            if (at <= now) return at
        }
        return null
    }

    /** The first scheduled moment after [now]; null for the kinds without a calendar. */
    fun nextOccurrence(now: Long, zone: TimeZone = TimeZone.getDefault()): Long? {
        if (!isCalendar) return null
        for (ahead in 0..SEARCH_DAYS) {
            val at = occurrenceOn(now, ahead, zone) ?: continue
            if (at > now) return at
        }
        return null
    }

    /** The scheduled moment on the day [offset] days from the one of [now], when that day is one of the schedule's. */
    private fun occurrenceOn(now: Long, offset: Int, zone: TimeZone): Long? {
        val day = Calendar.getInstance(zone).apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, offset)
        }
        val matches = when (kind) {
            Kind.DAILY -> true
            Kind.WEEKLY -> day.get(Calendar.DAY_OF_WEEK) in weekdays
            Kind.MONTHLY -> day.get(Calendar.DAY_OF_MONTH) == minOf(dayOfMonth, day.getActualMaximum(Calendar.DAY_OF_MONTH))
            else -> false
        }
        if (!matches) return null
        day.set(Calendar.HOUR_OF_DAY, hour)
        day.set(Calendar.MINUTE, minute)
        day.set(Calendar.SECOND, 0)
        day.set(Calendar.MILLISECOND, 0)
        return day.timeInMillis
    }

    fun encode(): String = listOf(kind.name, intervalMinutes, hour, minute, weekdays.sorted().joinToString(","), dayOfMonth).joinToString("|")

    companion object {
        /** The shortest interval, so a widget cannot keep the network and the process busy all day. */
        const val MIN_INTERVAL_MINUTES = 15
        const val DEFAULT_INTERVAL_MINUTES = 15

        /** Far enough to find the next day of any rule, a month with the day and a leap year included. */
        private const val SEARCH_DAYS = 400

        /** Reads what [encode] wrote; anything else is a widget that only runs on a tap. */
        fun decode(text: String?): CommandWidgetSchedule {
            val part = text?.split("|") ?: return CommandWidgetSchedule()
            return runCatching {
                CommandWidgetSchedule(
                    kind = Kind.valueOf(part[0]),
                    intervalMinutes = part[1].toInt().coerceAtLeast(MIN_INTERVAL_MINUTES),
                    hour = part[2].toInt().coerceIn(0, 23),
                    minute = part[3].toInt().coerceIn(0, 59),
                    weekdays = part[4].split(",").mapNotNull { it.toIntOrNull() }.filter { it in Calendar.SUNDAY..Calendar.SATURDAY }.toSet()
                        .ifEmpty { setOf(Calendar.MONDAY) },
                    dayOfMonth = part[5].toInt().coerceIn(1, 31),
                )
            }.getOrDefault(CommandWidgetSchedule())
        }
    }
}
