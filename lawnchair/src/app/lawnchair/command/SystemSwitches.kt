package app.lawnchair.command

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent

/**
 * The switches, brightness, volume and media keys of the command bar. Those the system lets an
 * app change are changed directly; for the others, or while a special access is missing, the
 * matching settings screen opens instead, so the next run works.
 */
object SystemSwitches {

    @Volatile
    private var torchOn = false

    @Volatile
    private var torchWatched = false

    /** Follows the flashlight so "lanterna" alone knows which way to flip it. */
    fun watchTorch(context: Context) {
        if (torchWatched) return
        val cameras = context.getSystemService(CameraManager::class.java) ?: return
        runCatching {
            cameras.registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        torchOn = enabled
                    }
                },
                Handler(Looper.getMainLooper()),
            )
            torchWatched = true
        }
    }

    fun toggle(context: Context, target: SystemToggle, on: Boolean?): Boolean = when (target) {
        SystemToggle.WIFI -> open(
            context,
            Intent(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS),
        )

        SystemToggle.BLUETOOTH -> open(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

        SystemToggle.TORCH -> torch(context, on)

        SystemToggle.DND -> doNotDisturb(context, on)

        SystemToggle.ROTATION -> withWriteSettings(context) {
            val current = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on ?: !current) 1 else 0)
        }
    }

    private fun torch(context: Context, on: Boolean?): Boolean {
        val cameras = context.getSystemService(CameraManager::class.java) ?: return false
        watchTorch(context)
        val id = cameras.cameraIdList.firstOrNull {
            cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return false
        val target = on ?: !torchOn
        cameras.setTorchMode(id, target)
        torchOn = target
        return true
    }

    private fun doNotDisturb(context: Context, on: Boolean?): Boolean {
        val notifications = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!notifications.isNotificationPolicyAccessGranted) {
            return open(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        val active = notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        notifications.setInterruptionFilter(
            if (on ?: !active) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL,
        )
        return true
    }

    /** [percent] null turns automatic brightness on. */
    fun brightness(context: Context, percent: Int?): Boolean = withWriteSettings(context) {
        val resolver = context.contentResolver
        if (percent == null) {
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        } else {
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, (percent * 255 / 100).coerceIn(1, 255))
        }
    }

    fun volume(context: Context, percent: Int): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (max * percent + 50) / 100, AudioManager.FLAG_SHOW_UI)
        return true
    }

    /** Presses a media key, for whatever app is playing. */
    fun media(context: Context, key: MediaKey): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        val code = when (key) {
            MediaKey.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaKey.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaKey.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaKey.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        val time = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, code, 0))
        return true
    }

    /** Runs [change] when the app may write system settings; otherwise opens the screen that allows it. */
    private fun withWriteSettings(context: Context, change: () -> Unit): Boolean {
        if (!Settings.System.canWrite(context)) {
            return open(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName)))
        }
        change()
        return true
    }

    private fun open(context: Context, intent: Intent): Boolean {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
