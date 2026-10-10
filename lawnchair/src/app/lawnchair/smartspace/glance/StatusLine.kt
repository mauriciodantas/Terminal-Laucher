package app.lawnchair.smartspace.glance

import androidx.annotation.StringRes
import app.lawnchair.util.Texts
import com.android.launcher3.R

/** The sound profile shown in the status line. */
enum class SoundProfile(@StringRes private val labelRes: Int) {
    NORMAL(R.string.glance_sound_normal),
    VIBRATE(R.string.glance_sound_vibrate),
    SILENT(R.string.glance_sound_silent),
    DO_NOT_DISTURB(R.string.glance_sound_dnd),
    ;

    val label: String get() = Texts.get(labelRes)
}

/** Pure rules of the dynamic status line, free of Android types so they can be unit tested. */
object StatusLine {

    /** Shown when the dynamic line is off, or when nothing can be read. */
    val fallback: String get() = Texts.get(R.string.nostromo_status_line)

    /**
     * The sound profile. "Do not disturb" wins over the ringer mode, because it is what silences
     * the phone even when the ringer says normal.
     */
    fun soundProfile(ringerMode: Int, doNotDisturb: Boolean): SoundProfile = when {
        doNotDisturb -> SoundProfile.DO_NOT_DISTURB
        ringerMode == RINGER_SILENT -> SoundProfile.SILENT
        ringerMode == RINGER_VIBRATE -> SoundProfile.VIBRATE
        else -> SoundProfile.NORMAL
    }

    /** Percent used, 0..100, or null when the total is unknown. */
    fun percentUsed(used: Long, total: Long): Int? {
        if (total <= 0) return null
        return (used.coerceIn(0, total) * 100 / total).toInt()
    }

    /** "SILENCIOSO · ARMAZ. 62% · RAM 48%". Parts that could not be read are left out. */
    fun format(profile: SoundProfile?, storagePercent: Int?, ramPercent: Int?): String {
        val parts = listOfNotNull(
            profile?.label,
            storagePercent?.let { Texts.get(R.string.glance_storage, it.coerceIn(0, 100)) },
            ramPercent?.let { Texts.get(R.string.glance_ram, it.coerceIn(0, 100)) },
        )
        return if (parts.isEmpty()) fallback else parts.joinToString(" · ")
    }

    // Values of AudioManager.RINGER_MODE_*, kept here so the rules do not depend on Android.
    const val RINGER_SILENT = 0
    const val RINGER_VIBRATE = 1
}
