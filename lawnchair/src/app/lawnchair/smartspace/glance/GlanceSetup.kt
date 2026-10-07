package app.lawnchair.smartspace.glance

import android.content.Intent
import android.provider.Settings
import androidx.core.os.bundleOf
import com.android.launcher3.settings.SettingsActivity

/** What the user still has to do before "Mídia tocando" can work. */
enum class MediaSetupStep {
    /** Everything is ready. */
    NONE,

    /** The launcher is not allowed to read notifications yet. */
    GRANT_ACCESS,

    /** Access is granted, but the app's notification dots are off. */
    ENABLE_DOTS,
}

/** Pure rules of the At a Glance setup, free of Android types so they can be unit tested. */
object GlanceSetup {

    /**
     * The next thing to fix, in order: first the access to notifications, then the dots. Only one
     * step is reported at a time, so the user is never sent to a screen that cannot help yet.
     */
    fun mediaStep(serviceEnabled: Boolean, dotsEnabled: Boolean): MediaSetupStep = when {
        !serviceEnabled -> MediaSetupStep.GRANT_ACCESS
        !dotsEnabled -> MediaSetupStep.ENABLE_DOTS
        else -> MediaSetupStep.NONE
    }
}

/** The system screen that fixes each step, so the user lands exactly where the switch is. */
object GlanceSetupIntents {
    fun forStep(step: MediaSetupStep): Intent? = when (step) {
        MediaSetupStep.NONE -> null

        MediaSetupStep.GRANT_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

        MediaSetupStep.ENABLE_DOTS -> Intent("android.settings.NOTIFICATION_SETTINGS")
            .putExtra(SettingsActivity.EXTRA_FRAGMENT_HIGHLIGHT_KEY, "notification_badging")
            .putExtra(
                SettingsActivity.EXTRA_FRAGMENT_ARGS,
                bundleOf(SettingsActivity.EXTRA_FRAGMENT_HIGHLIGHT_KEY to "notification_badging"),
            )
    }
}
