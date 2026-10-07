package app.lawnchair.smartspace.provider

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Icon
import app.lawnchair.BlankActivity
import app.lawnchair.getAppName
import app.lawnchair.smartspace.glance.GlanceSetup
import app.lawnchair.smartspace.glance.GlanceSetupIntents
import app.lawnchair.smartspace.glance.MediaSetupStep
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceScores
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.ui.preferences.components.isNotificationServiceEnabled
import app.lawnchair.ui.preferences.components.notificationDotsEnabled
import com.android.launcher3.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first

class NowPlayingProvider(context: Context) :
    SmartspaceDataSource(
        context,
        R.string.smartspace_now_playing,
        { smartspaceNowPlaying },
    ) {

    private val defaultIcon = Icon.createWithResource(context, R.drawable.ic_music_note)

    override val internalTargets = callbackFlow {
        val mediaListener = MediaListener(context) {
            trySend(listOfNotNull(getSmartspaceTarget(it)))
        }
        mediaListener.onResume()
        awaitClose { mediaListener.onPause() }
    }

    private fun getSmartspaceTarget(media: MediaListener): SmartspaceTarget? {
        val tracking = media.tracking ?: return null
        val title = tracking.info.title ?: return null

        val sbn = tracking.sbn
        val icon = sbn.notification.smallIcon ?: defaultIcon

        val mediaInfo = tracking.info
        val subtitle = mediaInfo.artist?.takeIf { it.isNotEmpty() }
            ?: sbn?.getAppName(context)
            ?: context.getAppName(tracking.packageName)
        val intent = sbn?.notification?.contentIntent
        return SmartspaceTarget(
            id = "nowPlaying-${mediaInfo.hashCode()}",
            headerAction = SmartspaceAction(
                id = "nowPlayingAction-${mediaInfo.hashCode()}",
                icon = icon,
                title = title,
                subtitle = subtitle,
                pendingIntent = intent,
                onClick = if (intent == null) Runnable { media.toggle(true) } else null,
            ),
            score = SmartspaceScores.SCORE_MEDIA,
            featureType = SmartspaceTarget.FeatureType.FEATURE_MEDIA,
        )
    }

    override suspend fun requiresSetup(): Boolean = isNotificationServiceEnabled(context = context).not() ||
        notificationDotsEnabled(context = context).first().not()

    override suspend fun startSetup(activity: Activity) {
        val step = GlanceSetup.mediaStep(
            serviceEnabled = isNotificationServiceEnabled(context = context),
            dotsEnabled = notificationDotsEnabled(context = context).first(),
        )
        val intent = GlanceSetupIntents.forStep(step) ?: return
        val message = activity.getString(
            if (step == MediaSetupStep.GRANT_ACCESS) {
                R.string.glance_media_access_message
            } else {
                R.string.glance_media_dots_message
            },
        )
        BlankActivity.startBlankActivityDialog(
            activity,
            intent,
            activity.getString(R.string.glance_media_setup_title),
            message,
            activity.getString(
                if (step == MediaSetupStep.GRANT_ACCESS) R.string.glance_media_access_action else R.string.glance_media_dots_action,
            ),
        )
    }
}
