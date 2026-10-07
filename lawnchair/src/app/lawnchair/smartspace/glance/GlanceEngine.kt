package app.lawnchair.smartspace.glance

import app.lawnchair.smartspace.model.SmartspaceTarget.FeatureType

/** The kinds of information the At a Glance panel alternates between. */
enum class GlanceKind(val label: String, val panelTitle: String) {
    AGENDA("AGENDA", "PRÓXIMO EVENTO"),
    CLIMA("CLIMA", "CLIMA AGORA"),
    MIDIA("MÍDIA", "TOCANDO AGORA"),
    ALARME("ALARME", "PRÓXIMO ALARME"),
    LEMBRETE("LEMBRETES", "LEMBRETE"),
    BATERIA("BATERIA", "BATERIA BLUETOOTH"),
    AVISO("AVISO", "AVISO DO SISTEMA"),
    ;

    companion object {
        /** Targets that only exist to hold a place and must never be shown. */
        private val placeholderIds = setOf("dummyTarget")

        /** Ids of targets that are system notices even though their feature type says otherwise. */
        private val noticeIdPrefixes = listOf("batteryStatus", "torchStatus", "onboarding", "smartspaceSetup")

        /** Id of the target the Bluetooth battery provider reports. */
        const val BLUETOOTH_ID = "bluetoothBattery"

        fun isPlaceholder(id: String): Boolean = id in placeholderIds

        /**
         * The kind for a smartspace target. The id is checked first because the battery provider
         * reports itself as a calendar feature.
         */
        fun from(featureType: FeatureType, id: String = ""): GlanceKind {
            if (id.startsWith(BLUETOOTH_ID)) return BATERIA
            if (noticeIdPrefixes.any { id.startsWith(it) }) return AVISO
            return when (featureType) {
                FeatureType.FEATURE_CALENDAR,
                FeatureType.FEATURE_TIME_TO_LEAVE,
                FeatureType.FEATURE_COMMUTE_TIME,
                FeatureType.FEATURE_FLIGHT,
                -> AGENDA

                FeatureType.FEATURE_REMINDER -> LEMBRETE

                FeatureType.FEATURE_WEATHER,
                FeatureType.FEATURE_WEATHER_ALERT,
                FeatureType.FEATURE_SEVERE_WEATHER_ALERT,
                -> CLIMA

                FeatureType.FEATURE_MEDIA,
                FeatureType.FEATURE_MEDIA_RESUME,
                FeatureType.FEATURE_MEDIA_HEADS_UP,
                -> MIDIA

                FeatureType.FEATURE_ALARM,
                FeatureType.FEATURE_UPCOMING_ALARM,
                FeatureType.FEATURE_HOLIDAY_ALARM,
                -> ALARME

                else -> AVISO
            }
        }
    }
}

/** One thing worth showing on the panel. [startsAtMillis] is set only when the start is known. */
data class GlanceTarget(
    val id: String,
    val kind: GlanceKind,
    val title: String,
    val subtitle: String = "",
    val startsAtMillis: Long? = null,
    val score: Float = 0f,
)

data class GlanceSettings(
    /** Most tabs the panel may show. */
    val maxTargets: Int = 5,
    val enabledKinds: Set<GlanceKind> = GlanceKind.values().toSet(),
    /** Moves an event that is about to start to the front and marks it as urgent. */
    val autoPriority: Boolean = true,
    /** How many minutes before its start an event counts as about to start. */
    val urgentWindowMinutes: Int = GlanceEngine.DEFAULT_LEAD_MINUTES,
)

/** What the panel shows: the tabs in order, and which one (if any) is urgent. */
data class GlancePanel(val tabs: List<GlanceTarget>, val urgentId: String?) {
    val isEmpty: Boolean get() = tabs.isEmpty()

    fun isUrgent(target: GlanceTarget): Boolean = urgentId != null && target.id == urgentId
}

/** Pure rules of the At a Glance panel, free of Android types so they can be unit tested. */
object GlanceEngine {

    private const val MINUTE_MS = 60_000L

    /** Kinds that can become urgent when their start is close. */
    private val urgentKinds = setOf(GlanceKind.AGENDA, GlanceKind.LEMBRETE)

    /** Lead times the user may pick, in minutes. */
    val LEAD_TIME_OPTIONS = listOf(15, 30, 60)
    const val DEFAULT_LEAD_MINUTES = 30

    /** The closest allowed lead time, so a stale or odd stored value never breaks the panel. */
    fun normalizeLeadMinutes(minutes: Int): Int = if (minutes in LEAD_TIME_OPTIONS) minutes else DEFAULT_LEAD_MINUTES

    /** Whole minutes from [nowMillis] to the start, or null when the start is unknown. */
    fun minutesUntil(startsAtMillis: Long?, nowMillis: Long): Long? = startsAtMillis?.let { Math.floorDiv(it - nowMillis, MINUTE_MS) }

    /** True for an event that starts within the window, and has not started more than a minute ago. */
    fun isUrgent(target: GlanceTarget, settings: GlanceSettings, nowMillis: Long): Boolean {
        if (!settings.autoPriority || target.kind !in urgentKinds) return false
        val minutes = minutesUntil(target.startsAtMillis, nowMillis) ?: return false
        return minutes in -1..settings.urgentWindowMinutes.toLong()
    }

    /** "EM 18 MIN", "EM 3H 12M", "EM 2 D" or "AGORA". */
    fun countdownLabel(minutes: Long): String = when {
        minutes <= 0 -> "AGORA"

        minutes < 60 -> "EM $minutes MIN"

        minutes < 24 * 60 -> {
            val hours = minutes / 60
            val rest = minutes % 60
            if (rest == 0L) "EM ${hours}H" else "EM ${hours}H ${rest}M"
        }

        else -> "EM ${minutes / (24 * 60)} D"
    }

    /** Label of the tab at [index] (zero based): "01 AGENDA". */
    fun tabLabel(index: Int, kind: GlanceKind): String = "%02d %s".format(index + 1, kind.label)

    /** Picks, orders and limits the tabs. */
    fun build(targets: List<GlanceTarget>, settings: GlanceSettings, nowMillis: Long): GlancePanel {
        val usable = targets.filter {
            it.kind in settings.enabledKinds &&
                !GlanceKind.isPlaceholder(it.id) &&
                (it.title.isNotBlank() || it.subtitle.isNotBlank())
        }

        // One tab per kind. Within a kind the urgent event wins, then the highest score, then the
        // soonest start.
        val perKind = usable.groupBy { it.kind }.mapValues { (_, list) ->
            list.sortedWith(
                compareByDescending<GlanceTarget> { isUrgent(it, settings, nowMillis) }
                    .thenByDescending { it.score }
                    .thenBy { it.startsAtMillis ?: Long.MAX_VALUE },
            ).first()
        }.values.toList()

        val urgent = perKind.firstOrNull { isUrgent(it, settings, nowMillis) }
        val ordered = perKind.sortedWith(
            compareByDescending<GlanceTarget> { it.id == urgent?.id }
                .thenByDescending { it.score }
                .thenBy { it.kind.ordinal },
        )
        val limit = settings.maxTargets.coerceAtLeast(1)
        return GlancePanel(tabs = ordered.take(limit), urgentId = urgent?.id)
    }

    private val timeRegex = Regex("""\b([01]?\d|2[0-3])[:h]([0-5]\d)\b""")
    private val percentRegex = Regex("""(\d{1,3})\s?%""")
    private val temperatureRegex = Regex("""(-?\d{1,2})\s?°\s?[CcFf]?""")

    /**
     * The big pixel-font value on the left of the panel (a time or a temperature), pulled out of the
     * target text, and what is left of the text once it is removed. The value is null when the text
     * has nothing worth enlarging.
     */
    data class Lead(val value: String?, val text: String)

    fun lead(target: GlanceTarget): Lead {
        val full = listOf(target.title, target.subtitle).filter { it.isNotBlank() }.joinToString(" · ")
        val match = when (target.kind) {
            GlanceKind.CLIMA -> temperatureRegex.find(full)?.let { it to (it.groupValues[1] + "°") }

            GlanceKind.BATERIA -> percentRegex.find(full)?.let { it to (it.groupValues[1] + "%") }

            GlanceKind.AGENDA, GlanceKind.ALARME, GlanceKind.LEMBRETE ->
                timeRegex.find(full)?.let { it to (it.groupValues[1].padStart(2, '0') + ":" + it.groupValues[2]) }

            else -> null
        } ?: return Lead(null, full)
        val (found, value) = match
        val rest = full.removeRange(found.range)
            .replace(Regex("""\s*·\s*·\s*"""), " · ")
            .trim(' ', '·', '-', ',')
        return Lead(value, rest)
    }
}
