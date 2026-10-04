package app.lawnchair.smartspace.glance

/** The sound profile shown in the status line. */
enum class SoundProfile(val label: String) {
    NORMAL("SOM NORMAL"),
    VIBRATE("VIBRAÇÃO"),
    SILENT("SILENCIOSO"),
    DO_NOT_DISTURB("NÃO PERTURBE"),
}

/** Pure rules of the dynamic status line, free of Android types so they can be unit tested. */
object StatusLine {

    /** Shown when the dynamic line is off, or when nothing can be read. */
    const val FALLBACK = "CICLO DE HIPERSONO · SEÇÃO A"

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
            storagePercent?.let { "ARMAZ. ${it.coerceIn(0, 100)}%" },
            ramPercent?.let { "RAM ${it.coerceIn(0, 100)}%" },
        )
        return if (parts.isEmpty()) FALLBACK else parts.joinToString(" · ")
    }

    // Values of AudioManager.RINGER_MODE_*, kept here so the rules do not depend on Android.
    const val RINGER_SILENT = 0
    const val RINGER_VIBRATE = 1
}
