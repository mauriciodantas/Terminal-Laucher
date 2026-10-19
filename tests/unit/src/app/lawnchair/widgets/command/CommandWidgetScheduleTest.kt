package app.lawnchair.widgets.command

import app.lawnchair.widgets.command.CommandWidgetSchedule.Kind
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandWidgetScheduleTest {

    private val zone = TimeZone.getTimeZone("UTC")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long = Calendar.getInstance(zone).apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }.timeInMillis

    @Test
    fun manualRunsOnlyWhenNeverRead() {
        val schedule = CommandWidgetSchedule(Kind.MANUAL)
        assertTrue(schedule.isDue(at(2026, 10, 19), 0, zone))
        assertFalse(schedule.isDue(at(2026, 10, 19), at(2026, 1, 1), zone))
        assertNull(schedule.nextRun(at(2026, 10, 19), at(2026, 1, 1), zone))
    }

    @Test
    fun intervalCountsFromTheLastRun() {
        val schedule = CommandWidgetSchedule(Kind.INTERVAL, intervalMinutes = 15)
        val last = at(2026, 10, 19, 8, 0)
        assertFalse(schedule.isDue(last + 14 * 60_000L, last, zone))
        assertTrue(schedule.isDue(last + 15 * 60_000L, last, zone))
        assertEquals(last + 15 * 60_000L, schedule.nextRun(last, last, zone))
    }

    @Test
    fun dailyRunsOnceAfterItsTime() {
        val schedule = CommandWidgetSchedule(Kind.DAILY, hour = 8)
        val lastRun = at(2026, 10, 19, 8, 0)
        assertFalse(schedule.isDue(at(2026, 10, 19, 20, 0), lastRun, zone))
        assertFalse(schedule.isDue(at(2026, 10, 20, 7, 59), lastRun, zone))
        assertTrue(schedule.isDue(at(2026, 10, 20, 8, 0), lastRun, zone))
        assertEquals(at(2026, 10, 20, 8, 0), schedule.nextRun(at(2026, 10, 19, 20, 0), lastRun, zone))
    }

    @Test
    fun missedDailyRunIsMadeUpOnce() {
        val schedule = CommandWidgetSchedule(Kind.DAILY, hour = 8)
        // Not read for three days: due once, and then not again until the next 8:00.
        val lastRun = at(2026, 10, 15, 8, 0)
        val now = at(2026, 10, 19, 12, 0)
        assertTrue(schedule.isDue(now, lastRun, zone))
        assertFalse(schedule.isDue(now, at(2026, 10, 19, 12, 0), zone))
    }

    @Test
    fun weeklyRunsOnTheChosenWeekdays() {
        // 2026-10-19 is a Monday.
        val schedule = CommandWidgetSchedule(Kind.WEEKLY, hour = 9, weekdays = setOf(Calendar.MONDAY, Calendar.FRIDAY))
        assertEquals(at(2026, 10, 19, 9, 0), schedule.lastOccurrence(at(2026, 10, 19, 10, 0), zone))
        assertEquals(at(2026, 10, 19, 9, 0), schedule.lastOccurrence(at(2026, 10, 21, 10, 0), zone))
        assertEquals(at(2026, 10, 23, 9, 0), schedule.nextOccurrence(at(2026, 10, 19, 10, 0), zone))
        assertEquals(at(2026, 10, 26, 9, 0), schedule.nextOccurrence(at(2026, 10, 23, 10, 0), zone))
    }

    @Test
    fun monthlyRunsOnTheDayOfTheMonth() {
        val schedule = CommandWidgetSchedule(Kind.MONTHLY, hour = 6, dayOfMonth = 15)
        assertEquals(at(2026, 10, 15, 6, 0), schedule.lastOccurrence(at(2026, 10, 19), zone))
        assertEquals(at(2026, 11, 15, 6, 0), schedule.nextOccurrence(at(2026, 10, 19), zone))
    }

    @Test
    fun monthlyDay31FallsOnTheLastDayOfShortMonths() {
        val schedule = CommandWidgetSchedule(Kind.MONTHLY, hour = 6, dayOfMonth = 31)
        assertEquals(at(2026, 11, 30, 6, 0), schedule.nextOccurrence(at(2026, 11, 1), zone))
        assertEquals(at(2028, 2, 29, 6, 0), schedule.nextOccurrence(at(2028, 2, 1), zone))
    }

    @Test
    fun intervalBelowTheMinimumIsRaised() {
        assertEquals(15, CommandWidgetSchedule.decode(CommandWidgetSchedule(Kind.INTERVAL, intervalMinutes = 1).encode()).intervalMinutes)
    }

    @Test
    fun encodeAndDecodeKeepTheSchedule() {
        val schedule = CommandWidgetSchedule(Kind.WEEKLY, 30, 7, 45, setOf(Calendar.TUESDAY, Calendar.SUNDAY), 12)
        assertEquals(schedule, CommandWidgetSchedule.decode(schedule.encode()))
        assertEquals(CommandWidgetSchedule(), CommandWidgetSchedule.decode("lixo"))
        assertEquals(CommandWidgetSchedule(), CommandWidgetSchedule.decode(null))
    }
}
