package app.lawnchair.smartspace.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusLineTest {

    @Test
    fun soundProfile_followsTheRingerMode() {
        assertEquals(SoundProfile.NORMAL, StatusLine.soundProfile(2, false))
        assertEquals(SoundProfile.VIBRATE, StatusLine.soundProfile(StatusLine.RINGER_VIBRATE, false))
        assertEquals(SoundProfile.SILENT, StatusLine.soundProfile(StatusLine.RINGER_SILENT, false))
    }

    @Test
    fun soundProfile_doNotDisturbWinsOverEveryRingerMode() {
        for (mode in 0..2) assertEquals(SoundProfile.DO_NOT_DISTURB, StatusLine.soundProfile(mode, true))
    }

    @Test
    fun soundProfile_anUnknownModeCountsAsNormal() {
        assertEquals(SoundProfile.NORMAL, StatusLine.soundProfile(99, false))
    }

    @Test
    fun percentUsed_roundsDownAndStaysInRange() {
        assertEquals(62, StatusLine.percentUsed(62_999, 100_000))
        assertEquals(0, StatusLine.percentUsed(0, 100))
        assertEquals(100, StatusLine.percentUsed(500, 100))
        assertEquals(0, StatusLine.percentUsed(-5, 100))
    }

    @Test
    fun percentUsed_isNullWithoutATotal() {
        assertNull(StatusLine.percentUsed(10, 0))
        assertNull(StatusLine.percentUsed(10, -1))
    }

    @Test
    fun format_joinsEveryPart() {
        assertEquals(
            "SILENCIOSO · ARMAZ. 62% · RAM 48%",
            StatusLine.format(SoundProfile.SILENT, 62, 48),
        )
    }

    @Test
    fun format_leavesOutWhatCouldNotBeRead() {
        assertEquals("ARMAZ. 62%", StatusLine.format(null, 62, null))
        assertEquals("NÃO PERTURBE · RAM 10%", StatusLine.format(SoundProfile.DO_NOT_DISTURB, null, 10))
    }

    @Test
    fun format_fallsBackToTheStaticLineWhenNothingIsKnown() {
        assertEquals(StatusLine.FALLBACK, StatusLine.format(null, null, null))
    }

    @Test
    fun format_clampsOutOfRangePercentages() {
        assertEquals("ARMAZ. 100% · RAM 0%", StatusLine.format(null, 140, -3))
    }
}
