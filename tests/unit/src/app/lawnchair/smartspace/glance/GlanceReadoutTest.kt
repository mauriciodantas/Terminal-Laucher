package app.lawnchair.smartspace.glance

import org.junit.Assert.assertEquals
import org.junit.Test

class GlanceReadoutTest {

    @Test
    fun scaleStaysInsideTheOfferedRange() {
        assertEquals(1f, GlanceReadout.scale(100f.toInt()), 0.001f)
        assertEquals(0.6f, GlanceReadout.scale(10), 0.001f)
        assertEquals(1.4f, GlanceReadout.scale(500), 0.001f)
    }

    @Test
    fun hiddenShortcutsRoundTrip() {
        val hidden = setOf(GlanceShortcut.CAMERA, GlanceShortcut.TORCH)
        val stored = GlanceReadout.serializeHidden(hidden)
        assertEquals("TORCH,CAMERA", stored)
        assertEquals(hidden, GlanceReadout.parseHidden(stored))
    }

    @Test
    fun unknownShortcutNamesAreIgnored() {
        assertEquals(setOf(GlanceShortcut.CLOCK), GlanceReadout.parseHidden("OLD, CLOCK,"))
        assertEquals(emptySet<GlanceShortcut>(), GlanceReadout.parseHidden(""))
    }

    @Test
    fun unknownFormatIdsFallBackToTheDefault() {
        assertEquals(GlanceClockFormat.SYSTEM, GlanceClockFormat.fromId("x"))
        assertEquals(GlanceDateFormat.FULL, GlanceDateFormat.fromId(null))
        assertEquals(GlanceDateFormat.SHORT, GlanceDateFormat.fromId("short"))
    }

    @Test
    fun secondsAreAppendedOnlyWhenAsked() {
        assertEquals("HH:mm:ss", GlanceReadout.withSeconds("HH:mm", true))
        assertEquals("HH:mm", GlanceReadout.withSeconds("HH:mm", false))
    }

    @Test
    fun textAndDimStayInsideTheirRange() {
        assertEquals(0.8f, GlanceReadout.textScale(10), 0.001f)
        assertEquals(0.9f, GlanceReadout.dim(200), 0.001f)
        assertEquals(GlanceAlign.CENTER, GlanceAlign.fromId("center"))
        assertEquals(GlanceUrgentColor.RED, GlanceUrgentColor.fromId("?"))
    }
}
