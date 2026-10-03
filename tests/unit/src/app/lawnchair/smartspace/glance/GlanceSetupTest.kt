package app.lawnchair.smartspace.glance

import org.junit.Assert.assertEquals
import org.junit.Test

class GlanceSetupTest {

    @Test
    fun mediaStep_isNoneWhenEverythingIsReady() {
        assertEquals(MediaSetupStep.NONE, GlanceSetup.mediaStep(serviceEnabled = true, dotsEnabled = true))
    }

    @Test
    fun mediaStep_asksForAccessWhenTheServiceIsOff() {
        assertEquals(MediaSetupStep.GRANT_ACCESS, GlanceSetup.mediaStep(serviceEnabled = false, dotsEnabled = true))
    }

    @Test
    fun mediaStep_asksForDotsWhenOnlyTheDotsAreOff() {
        assertEquals(MediaSetupStep.ENABLE_DOTS, GlanceSetup.mediaStep(serviceEnabled = true, dotsEnabled = false))
    }

    @Test
    fun mediaStep_regression_accessComesBeforeDotsSoTheUserIsNeverSentToTheWrongScreen() {
        // The old dialog only talked about the dots and opened the General screen, even when the
        // real problem was the missing notification access.
        assertEquals(MediaSetupStep.GRANT_ACCESS, GlanceSetup.mediaStep(serviceEnabled = false, dotsEnabled = false))
    }

    @Test
    fun mediaStep_reportsExactlyOneStepForEveryCombination() {
        val results = listOf(true, false).flatMap { service ->
            listOf(true, false).map { dots -> GlanceSetup.mediaStep(service, dots) }
        }
        assertEquals(1, results.count { it == MediaSetupStep.NONE })
        assertEquals(2, results.count { it == MediaSetupStep.GRANT_ACCESS })
        assertEquals(1, results.count { it == MediaSetupStep.ENABLE_DOTS })
    }
}
