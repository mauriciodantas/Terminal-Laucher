package app.lawnchair.smartspace.glance

import app.lawnchair.smartspace.model.SmartspaceTarget.FeatureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlanceEngineTest {

    private val now = 1_000_000_000L
    private fun minutes(n: Long) = n * 60_000L

    private fun target(
        id: String,
        kind: GlanceKind,
        title: String = "titulo $id",
        subtitle: String = "",
        startsInMinutes: Long? = null,
        score: Float = 0f,
    ) = GlanceTarget(id, kind, title, subtitle, startsInMinutes?.let { now + minutes(it) }, score)

    // ---- which kind a smartspace target is ----

    @Test
    fun from_mapsCalendarLikeFeaturesToAgenda() {
        listOf(
            FeatureType.FEATURE_CALENDAR,
            FeatureType.FEATURE_TIME_TO_LEAVE,
            FeatureType.FEATURE_COMMUTE_TIME,
            FeatureType.FEATURE_FLIGHT,
            FeatureType.FEATURE_REMINDER,
        ).forEach { assertEquals(it.name, GlanceKind.AGENDA, GlanceKind.from(it)) }
    }

    @Test
    fun from_mapsWeatherMediaAndAlarmFeatures() {
        assertEquals(GlanceKind.CLIMA, GlanceKind.from(FeatureType.FEATURE_WEATHER))
        assertEquals(GlanceKind.CLIMA, GlanceKind.from(FeatureType.FEATURE_SEVERE_WEATHER_ALERT))
        assertEquals(GlanceKind.MIDIA, GlanceKind.from(FeatureType.FEATURE_MEDIA))
        assertEquals(GlanceKind.MIDIA, GlanceKind.from(FeatureType.FEATURE_MEDIA_RESUME))
        assertEquals(GlanceKind.ALARME, GlanceKind.from(FeatureType.FEATURE_ALARM))
        assertEquals(GlanceKind.ALARME, GlanceKind.from(FeatureType.FEATURE_UPCOMING_ALARM))
    }

    @Test
    fun from_sendsAnythingElseToAviso() {
        assertEquals(GlanceKind.AVISO, GlanceKind.from(FeatureType.FEATURE_TIPS))
        assertEquals(GlanceKind.AVISO, GlanceKind.from(FeatureType.FEATURE_UNDEFINED))
    }

    @Test
    fun from_regression_batteryStatusIsANoticeEvenThoughItIsReportedAsCalendar() {
        assertEquals(GlanceKind.AVISO, GlanceKind.from(FeatureType.FEATURE_CALENDAR, "batteryStatus"))
        assertEquals(GlanceKind.AVISO, GlanceKind.from(FeatureType.FEATURE_CALENDAR, "torchStatus"))
        assertEquals(GlanceKind.AVISO, GlanceKind.from(FeatureType.FEATURE_TIPS, "onboarding-swipe"))
        assertEquals(GlanceKind.AGENDA, GlanceKind.from(FeatureType.FEATURE_CALENDAR, "smartspaceWidgetCard"))
    }

    // ---- minutes and urgency ----

    @Test
    fun minutesUntil_roundsDownAndIsNullWhenUnknown() {
        assertEquals(18L, GlanceEngine.minutesUntil(now + minutes(18) + 59_000, now))
        assertEquals(0L, GlanceEngine.minutesUntil(now + 30_000, now))
        assertNull(GlanceEngine.minutesUntil(null, now))
    }

    @Test
    fun minutesUntil_isNegativeForEventsThatAlreadyStarted() {
        assertEquals(-5L, GlanceEngine.minutesUntil(now - minutes(5), now))
        assertEquals(-1L, GlanceEngine.minutesUntil(now - 10_000, now))
    }

    @Test
    fun isUrgent_trueInsideTheWindowAndAtItsEdges() {
        val s = GlanceSettings(urgentWindowMinutes = 30)
        assertTrue(GlanceEngine.isUrgent(target("e", GlanceKind.AGENDA, startsInMinutes = 18), s, now))
        assertTrue(GlanceEngine.isUrgent(target("e", GlanceKind.AGENDA, startsInMinutes = 30), s, now))
        assertTrue(GlanceEngine.isUrgent(target("e", GlanceKind.AGENDA, startsInMinutes = 0), s, now))
    }

    @Test
    fun isUrgent_falseOutsideTheWindow() {
        val s = GlanceSettings(urgentWindowMinutes = 30)
        assertFalse(GlanceEngine.isUrgent(target("e", GlanceKind.AGENDA, startsInMinutes = 31), s, now))
        assertFalse(GlanceEngine.isUrgent(target("e", GlanceKind.AGENDA, startsInMinutes = 600), s, now))
        assertFalse(GlanceEngine.isUrgent(target("e", GlanceKind.AGENDA, startsInMinutes = -10), s, now))
    }

    @Test
    fun isUrgent_falseWithoutAStartTimeOrForOtherKinds() {
        val s = GlanceSettings()
        assertFalse(GlanceEngine.isUrgent(target("e", GlanceKind.AGENDA), s, now))
        assertFalse(GlanceEngine.isUrgent(target("w", GlanceKind.CLIMA, startsInMinutes = 5), s, now))
        assertFalse(GlanceEngine.isUrgent(target("a", GlanceKind.ALARME, startsInMinutes = 5), s, now))
    }

    @Test
    fun isUrgent_falseWhenAutoPriorityIsOff() {
        val s = GlanceSettings(autoPriority = false)
        assertFalse(GlanceEngine.isUrgent(target("e", GlanceKind.AGENDA, startsInMinutes = 5), s, now))
    }

    // ---- labels ----

    @Test
    fun countdownLabel_minutes() {
        assertEquals("EM 18 MIN", GlanceEngine.countdownLabel(18))
        assertEquals("EM 1 MIN", GlanceEngine.countdownLabel(1))
        assertEquals("EM 59 MIN", GlanceEngine.countdownLabel(59))
    }

    @Test
    fun countdownLabel_hours() {
        assertEquals("EM 1H", GlanceEngine.countdownLabel(60))
        assertEquals("EM 3H 12M", GlanceEngine.countdownLabel(192))
        assertEquals("EM 23H 59M", GlanceEngine.countdownLabel(24 * 60 - 1))
    }

    @Test
    fun countdownLabel_daysAndNow() {
        assertEquals("EM 1 D", GlanceEngine.countdownLabel(24 * 60))
        assertEquals("EM 3 D", GlanceEngine.countdownLabel(3 * 24 * 60 + 500))
        assertEquals("AGORA", GlanceEngine.countdownLabel(0))
        assertEquals("AGORA", GlanceEngine.countdownLabel(-4))
    }

    @Test
    fun tabLabel_isOneBasedAndPadded() {
        assertEquals("01 AGENDA", GlanceEngine.tabLabel(0, GlanceKind.AGENDA))
        assertEquals("03 MÍDIA", GlanceEngine.tabLabel(2, GlanceKind.MIDIA))
        assertEquals("12 CLIMA", GlanceEngine.tabLabel(11, GlanceKind.CLIMA))
    }

    // ---- building the panel ----

    @Test
    fun build_withNoTargets_isEmpty() {
        val panel = GlanceEngine.build(emptyList(), GlanceSettings(), now)
        assertTrue(panel.isEmpty)
        assertNull(panel.urgentId)
    }

    @Test
    fun build_dropsDisabledKinds() {
        val s = GlanceSettings(enabledKinds = setOf(GlanceKind.CLIMA))
        val panel = GlanceEngine.build(
            listOf(target("e", GlanceKind.AGENDA), target("w", GlanceKind.CLIMA), target("m", GlanceKind.MIDIA)),
            s,
            now,
        )
        assertEquals(listOf("w"), panel.tabs.map { it.id })
    }

    @Test
    fun build_dropsPlaceholdersAndEmptyTargets() {
        val panel = GlanceEngine.build(
            listOf(
                target("dummyTarget", GlanceKind.CLIMA),
                target("empty", GlanceKind.MIDIA, title = "  ", subtitle = ""),
                target("ok", GlanceKind.ALARME),
            ),
            GlanceSettings(),
            now,
        )
        assertEquals(listOf("ok"), panel.tabs.map { it.id })
    }

    @Test
    fun build_keepsOneTabPerKind() {
        val panel = GlanceEngine.build(
            listOf(
                target("m1", GlanceKind.MIDIA, score = 1f),
                target("m2", GlanceKind.MIDIA, score = 2f),
                target("w", GlanceKind.CLIMA),
            ),
            GlanceSettings(),
            now,
        )
        assertEquals(setOf("m2", "w"), panel.tabs.map { it.id }.toSet())
    }

    @Test
    fun build_ordersByScoreThenByKind() {
        val panel = GlanceEngine.build(
            listOf(
                target("weather", GlanceKind.CLIMA, score = 0f),
                target("media", GlanceKind.MIDIA, score = 2f),
                target("agenda", GlanceKind.AGENDA, score = 3f),
                target("alarm", GlanceKind.ALARME, score = 0f),
            ),
            GlanceSettings(),
            now,
        )
        // Same score (0): kind order decides, CLIMA comes before ALARME.
        assertEquals(listOf("agenda", "media", "weather", "alarm"), panel.tabs.map { it.id })
    }

    @Test
    fun build_movesAnUrgentEventToTheFrontAndFlagsIt() {
        val panel = GlanceEngine.build(
            listOf(
                target("media", GlanceKind.MIDIA, score = 50f),
                target("agenda", GlanceKind.AGENDA, startsInMinutes = 18, score = 3f),
            ),
            GlanceSettings(),
            now,
        )
        assertEquals("agenda", panel.tabs.first().id)
        assertEquals("agenda", panel.urgentId)
        assertTrue(panel.isUrgent(panel.tabs[0]))
        assertFalse(panel.isUrgent(panel.tabs[1]))
    }

    @Test
    fun build_withoutAutoPriority_keepsTheNormalOrderAndFlagsNothing() {
        val panel = GlanceEngine.build(
            listOf(
                target("media", GlanceKind.MIDIA, score = 50f),
                target("agenda", GlanceKind.AGENDA, startsInMinutes = 18, score = 3f),
            ),
            GlanceSettings(autoPriority = false),
            now,
        )
        assertEquals(listOf("media", "agenda"), panel.tabs.map { it.id })
        assertNull(panel.urgentId)
    }

    @Test
    fun build_aFarEventIsNotUrgent() {
        val panel = GlanceEngine.build(
            listOf(target("agenda", GlanceKind.AGENDA, startsInMinutes = 240, score = 3f)),
            GlanceSettings(),
            now,
        )
        assertNull(panel.urgentId)
    }

    @Test
    fun build_picksTheUrgentEventAmongSeveralAgendaTargets() {
        val panel = GlanceEngine.build(
            listOf(
                target("later", GlanceKind.AGENDA, startsInMinutes = 300, score = 9f),
                target("soon", GlanceKind.AGENDA, startsInMinutes = 12, score = 1f),
            ),
            GlanceSettings(),
            now,
        )
        assertEquals(listOf("soon"), panel.tabs.map { it.id })
        assertEquals("soon", panel.urgentId)
    }

    @Test
    fun build_amongNonUrgentEventsTheHigherScoreThenTheSoonerStartWins() {
        val byScore = GlanceEngine.build(
            listOf(
                target("a", GlanceKind.AGENDA, startsInMinutes = 300, score = 1f),
                target("b", GlanceKind.AGENDA, startsInMinutes = 400, score = 2f),
            ),
            GlanceSettings(),
            now,
        )
        assertEquals("b", byScore.tabs.single().id)

        val bySoonest = GlanceEngine.build(
            listOf(
                target("a", GlanceKind.AGENDA, startsInMinutes = 400, score = 1f),
                target("b", GlanceKind.AGENDA, startsInMinutes = 300, score = 1f),
            ),
            GlanceSettings(),
            now,
        )
        assertEquals("b", bySoonest.tabs.single().id)
    }

    @Test
    fun build_respectsTheMaximumNumberOfTabs() {
        val all = GlanceKind.values().mapIndexed { i, kind -> target("t$i", kind, score = (10 - i).toFloat()) }
        val panel = GlanceEngine.build(all, GlanceSettings(maxTargets = 3), now)
        assertEquals(3, panel.tabs.size)
        assertEquals(listOf("t0", "t1", "t2"), panel.tabs.map { it.id })
    }

    @Test
    fun build_neverDropsTheUrgentEventEvenWithAMaximumOfOne() {
        val panel = GlanceEngine.build(
            listOf(
                target("media", GlanceKind.MIDIA, score = 50f),
                target("agenda", GlanceKind.AGENDA, startsInMinutes = 5, score = 1f),
            ),
            GlanceSettings(maxTargets = 1),
            now,
        )
        assertEquals(listOf("agenda"), panel.tabs.map { it.id })
    }

    @Test
    fun build_aMaximumBelowOneStillShowsOneTab() {
        val panel = GlanceEngine.build(listOf(target("w", GlanceKind.CLIMA)), GlanceSettings(maxTargets = 0), now)
        assertEquals(1, panel.tabs.size)
    }

    @Test
    fun build_isStableForTheSameInput() {
        val input = listOf(
            target("a", GlanceKind.CLIMA),
            target("b", GlanceKind.ALARME),
            target("c", GlanceKind.MIDIA, score = 2f),
        )
        val first = GlanceEngine.build(input, GlanceSettings(), now)
        val second = GlanceEngine.build(input.reversed(), GlanceSettings(), now)
        assertEquals(first.tabs.map { it.id }, second.tabs.map { it.id })
    }
}
