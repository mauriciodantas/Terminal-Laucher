package app.lawnchair.smartspace.glance

import androidx.annotation.StringRes
import com.android.launcher3.R

/** How the clock of the At a Glance readout writes the time. */
enum class GlanceClockFormat(val id: String, val pattern12: String, val pattern24: String, @StringRes val labelRes: Int) {
    SYSTEM("system", "h:mm", "HH:mm", R.string.glance_clock_format_system),
    H24("24h", "HH:mm", "HH:mm", R.string.glance_clock_format_24),
    H12("12h", "h:mm", "h:mm", R.string.glance_clock_format_12),
    ;

    companion object {
        fun fromId(id: String?): GlanceClockFormat = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/** How the date under the clock is written. */
enum class GlanceDateFormat(val id: String, val pattern: String, @StringRes val labelRes: Int) {
    FULL("full", "EEEE · dd.MMM.yyyy", R.string.glance_date_format_full),
    SHORT("short", "EEE · dd.MMM", R.string.glance_date_format_short),
    NUMERIC("numeric", "dd/MM/yyyy", R.string.glance_date_format_numeric),
    WEEKDAY("weekday", "EEEE", R.string.glance_date_format_weekday),
    ;

    companion object {
        fun fromId(id: String?): GlanceDateFormat = entries.firstOrNull { it.id == id } ?: FULL
    }
}

/** Where the readout lines (header, clock, date, status) sit. */
enum class GlanceAlign(val id: String, val gravity: Int, @StringRes val labelRes: Int) {
    START("start", android.view.Gravity.START, R.string.glance_align_start),
    CENTER("center", android.view.Gravity.CENTER_HORIZONTAL, R.string.glance_align_center),
    END("end", android.view.Gravity.END, R.string.glance_align_end),
    ;

    companion object {
        fun fromId(id: String?): GlanceAlign = entries.firstOrNull { it.id == id } ?: START
    }
}

/** The title bar of the panel: a solid fill, or only its text over the background. */
enum class GlanceTitleBar(val id: String, @StringRes val labelRes: Int) {
    FILLED("filled", R.string.glance_title_filled),
    OUTLINE("outline", R.string.glance_title_outline),
    ;

    companion object {
        fun fromId(id: String?): GlanceTitleBar = entries.firstOrNull { it.id == id } ?: FILLED
    }
}

/** The color of the priority state (an event about to start). */
enum class GlanceUrgentColor(val id: String, val argb: Int, @StringRes val labelRes: Int) {
    RED("red", 0xFFFF5A45.toInt(), R.string.glance_urgent_red),
    AMBER("amber", 0xFFFFB020.toInt(), R.string.glance_urgent_amber),
    MAGENTA("magenta", 0xFFFF4FA3.toInt(), R.string.glance_urgent_magenta),
    ;

    companion object {
        fun fromId(id: String?): GlanceUrgentColor = entries.firstOrNull { it.id == id } ?: RED
    }
}

/** How much room the target row takes. */
enum class GlanceDensity(val id: String, val rowMinDp: Int, @StringRes val labelRes: Int) {
    COMFORTABLE("comfortable", 48, R.string.glance_density_comfortable),
    COMPACT("compact", 34, R.string.glance_density_compact),
    ;

    companion object {
        fun fromId(id: String?): GlanceDensity = entries.firstOrNull { it.id == id } ?: COMFORTABLE
    }
}

/** A ready-made look: the style preferences it sets in one tap. */
enum class GlancePreset(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val border: Boolean,
    val titleBar: GlanceTitleBar,
    val borderWidth: Int,
    val radius: Int,
    val align: GlanceAlign,
    val density: GlanceDensity,
    val glow: Boolean,
    val header: Boolean,
    val status: Boolean,
    val clock: GlanceClockFormat,
    val date: GlanceDateFormat,
) {
    TERMINAL(
        R.string.glance_preset_terminal, R.string.glance_preset_terminal_desc,
        true, GlanceTitleBar.FILLED, 1, 0, GlanceAlign.START, GlanceDensity.COMFORTABLE, true, true, true,
        GlanceClockFormat.SYSTEM, GlanceDateFormat.FULL,
    ),
    OUTLINE(
        R.string.glance_preset_outline, R.string.glance_preset_outline_desc,
        true, GlanceTitleBar.OUTLINE, 2, 8, GlanceAlign.START, GlanceDensity.COMFORTABLE, true, true, true,
        GlanceClockFormat.SYSTEM, GlanceDateFormat.SHORT,
    ),
    OPEN(
        R.string.glance_preset_open, R.string.glance_preset_open_desc,
        false, GlanceTitleBar.OUTLINE, 1, 0, GlanceAlign.START, GlanceDensity.COMPACT, false, false, true,
        GlanceClockFormat.SYSTEM, GlanceDateFormat.SHORT,
    ),
    CENTERED(
        R.string.glance_preset_centered, R.string.glance_preset_centered_desc,
        true, GlanceTitleBar.FILLED, 1, 12, GlanceAlign.CENTER, GlanceDensity.COMFORTABLE, true, true, true,
        GlanceClockFormat.SYSTEM, GlanceDateFormat.FULL,
    ),
    MINIMAL(
        R.string.glance_preset_minimal, R.string.glance_preset_minimal_desc,
        false, GlanceTitleBar.OUTLINE, 1, 0, GlanceAlign.END, GlanceDensity.COMPACT, false, false, false,
        GlanceClockFormat.H24, GlanceDateFormat.WEEKDAY,
    ),
}

/** Pure rules of the readout settings, free of Android types so they can be unit tested. */
object GlanceReadout {
    const val SCALE_MIN = 60
    const val SCALE_MAX = 140
    const val TEXT_MIN = 80
    const val TEXT_MAX = 140
    const val DIM_MIN = 40
    const val DIM_MAX = 90
    const val RADIUS_MAX = 16
    const val BORDER_MAX = 3

    fun textScale(percent: Int): Float = percent.coerceIn(TEXT_MIN, TEXT_MAX) / 100f

    fun dim(percent: Int): Float = percent.coerceIn(DIM_MIN, DIM_MAX) / 100f

    /** The clock pattern with the seconds appended when asked for. */
    fun withSeconds(pattern: String, seconds: Boolean): String = if (seconds) "$pattern:ss" else pattern

    /** The clock size as a factor, kept inside the range the settings offer. */
    fun scale(percent: Int): Float = percent.coerceIn(SCALE_MIN, SCALE_MAX) / 100f

    fun parseHidden(stored: String): Set<GlanceShortcut> = stored.split(',')
        .mapNotNull { name -> GlanceShortcut.entries.firstOrNull { it.name == name.trim() } }
        .toSet()

    fun serializeHidden(hidden: Set<GlanceShortcut>): String = GlanceShortcut.entries.filter { it in hidden }.joinToString(",") { it.name }
}
