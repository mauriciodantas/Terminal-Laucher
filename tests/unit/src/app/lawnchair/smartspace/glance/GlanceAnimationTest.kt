package app.lawnchair.smartspace.glance

import org.junit.Assert.assertEquals
import org.junit.Test

class GlanceAnimationTest {

    @Test
    fun everyStyleRoundTripsThroughItsId() {
        GlanceAnimationStyle.entries.forEach {
            assertEquals(it, GlanceAnimationStyle.fromId(it.id))
        }
    }

    @Test
    fun anUnknownOrMissingIdMeansOff() {
        assertEquals(GlanceAnimationStyle.OFF, GlanceAnimationStyle.fromId("hologram"))
        assertEquals(GlanceAnimationStyle.OFF, GlanceAnimationStyle.fromId(""))
        assertEquals(GlanceAnimationStyle.OFF, GlanceAnimationStyle.fromId(null))
    }

    @Test
    fun theStoredValueKeepsTheNameAfterTheRevision() {
        val stored = GlanceAnimationFile.encode(1700000000000L, "meu radar.json")
        assertEquals("meu radar.json", GlanceAnimationFile.displayName(stored))
    }

    @Test
    fun aNameWithABarIsKeptWhole() {
        assertEquals("a|b.json", GlanceAnimationFile.displayName(GlanceAnimationFile.encode(1L, "a|b.json")))
    }

    @Test
    fun aValueWithoutNameFallsBackToTheFileName() {
        assertEquals("glance_animation.json", GlanceAnimationFile.displayName(""))
        assertEquals("glance_animation.json", GlanceAnimationFile.displayName("123"))
    }
}
