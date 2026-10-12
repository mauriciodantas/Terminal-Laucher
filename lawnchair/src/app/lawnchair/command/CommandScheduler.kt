package app.lawnchair.command

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.android.launcher3.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs the jobs of the command bar later: reminders become notifications, commands scheduled
 * with "em 30m: ..." run at their time and "quando carregar: ..." rules run on each event.
 * A command that only works with the bar in front (opening an app, a call) is not started from
 * the background, which Android blocks: it comes as a notification that runs it on a tap.
 */
object CommandScheduler {

    private const val CHANNEL = "command_jobs"
    private const val ACTION_FIRE = "app.lawnchair.command.FIRE_JOB"
    private const val EXTRA_ID = "job_id"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun add(context: Context, job: ScheduledJob): ScheduledJob {
        val saved = JobStore.add(context, job)
        if (saved.kind != JobKind.RULE) arm(context, saved)
        return saved
    }

    fun cancel(context: Context, job: ScheduledJob) {
        JobStore.remove(context, job.id)
        alarms(context)?.cancel(pending(context, job.id))
    }

    /**
     * Arms every saved job again and runs the ones missed meanwhile. Android drops the alarms on a
     * reboot, an app update, a force stop and when the exact alarm access is taken back, so this
     * also runs each time the app process starts.
     */
    fun armAll(context: Context) {
        val app = context.applicationContext
        scope.launch {
            val now = System.currentTimeMillis()
            JobStore.load(app).filter { it.kind != JobKind.RULE }.forEach { job ->
                if (job.at <= now) fire(app, job.id) else arm(app, job)
            }
        }
    }

    private fun exactAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms(context)?.canScheduleExactAlarms() == true

    /**
     * The screen that grants exact alarms, when a job at a time was just saved without them:
     * Android 14 denies them by default and the job may then run many minutes late.
     */
    fun exactAlarmRequest(context: Context, job: ScheduledJob): Intent? {
        if (job.kind == JobKind.RULE || exactAllowed(context)) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
            .takeIf { it.resolveActivity(context.packageManager) != null }
    }

    /** Why [job] will not show anything when it comes, or null when it will. */
    @StringRes
    fun notificationProblem(context: Context, job: ScheduledJob): Int? {
        val manager = NotificationManagerCompat.from(context)
        val blocked = !manager.areNotificationsEnabled() ||
            manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE
        if (!blocked) return null
        return if (job.kind == JobKind.REMIND) R.string.cmd_notify_blocked_reminder else R.string.cmd_notify_blocked_command
    }

    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun pending(context: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.toInt(),
        Intent(context, CommandJobReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun arm(context: Context, job: ScheduledJob) {
        val alarms = alarms(context) ?: return
        val intent = pending(context, job.id)
        // Exact when the system allows it; otherwise Android may run it a few minutes late.
        if (exactAllowed(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, job.at, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, job.at, intent)
        }
    }

    /** Runs the job [id] once: the alarm and [armAll] may both reach it when the app starts. */
    internal suspend fun fire(context: Context, id: Long) {
        val job = JobStore.take(context, id) ?: return
        when (job.kind) {
            JobKind.REMIND -> notify(context, id, context.getString(R.string.cmd_notify_reminder), job.text, null)
            JobKind.COMMAND, JobKind.RULE -> run(context, job.text, id)
        }
    }

    /** Fires the job of an alarm, keeping the process alive until it is done. */
    internal fun fireFromAlarm(context: Context, id: Long, done: () -> Unit) {
        val app = context.applicationContext
        scope.launch {
            try {
                // A broadcast has about ten seconds before Android may stop the process.
                withTimeoutOrNull(FIRE_TIMEOUT_MS) { fire(app, id) }
            } finally {
                done()
            }
        }
    }

    /** Runs the rules that wait for [event]. */
    fun onEvent(context: Context, event: TriggerEvent) {
        JobStore.load(context).filter { it.kind == JobKind.RULE && it.event == event }.forEach { runLine(context, it.text, it.id) }
    }

    /** Runs [line] now when it can run from the background, otherwise offers it in a notification. */
    fun runLine(context: Context, line: String, id: Long) {
        val app = context.applicationContext
        scope.launch { run(app, line, id) }
    }

    private suspend fun run(app: Context, line: String, id: Long) {
        val analysis = CommandLine.analyze(app, line)
        val action = analysis.action
        when {
            action == null -> notify(app, id, app.getString(R.string.cmd_notify_failed), line, null)

            CommandExecutor.runsInBackground(app, action) -> if (!CommandExecutor.execute(app, action)) {
                notify(app, id, app.getString(R.string.cmd_notify_failed), line, null)
            }

            else -> notify(app, id, app.getString(R.string.cmd_notify_tap_to_run), line, line)
        }
    }

    /** [run] is a command line that a tap on the notification runs in the command bar. */
    private fun notify(context: Context, id: Long, title: String, text: String, run: String?) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.cmd_notify_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val tap = PendingIntent.getActivity(
            context,
            id.toInt(),
            CommandActivity.intent(context, text = run.orEmpty(), run = run != null),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_command_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(if (run == null) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        runCatching { manager.notify(CHANNEL, id.toInt(), notification) }
    }

    /**
     * Listens for the events of the rules while the launcher runs. Charging and headphones are
     * not delivered to receivers declared in the manifest, so this lives with the app process,
     * which as the home screen stays alive.
     */
    fun watchEvents(context: Context) {
        val app = context.applicationContext
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val event = when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> TriggerEvent.CHARGING
                    Intent.ACTION_POWER_DISCONNECTED -> TriggerEvent.UNPLUGGED
                    else -> null
                } ?: return
                onEvent(app, event)
            }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                app.registerReceiver(receiver, filter)
            }
        }
        watchHeadphones(app)
    }

    /**
     * Headphones through the audio devices, which see wired, USB and Bluetooth ones alike and need
     * no permission; the headset plug broadcast only knew the wired jack.
     */
    private fun watchHeadphones(app: Context) {
        val audio = app.getSystemService(AudioManager::class.java) ?: return
        fun headphones(devices: Array<out AudioDeviceInfo>) = devices.filter { it.isSink && it.type in headphoneTypes }.map { it.id }
        val callback = object : AudioDeviceCallback() {
            // Registering also reports the devices already connected; they are known and change nothing.
            private val connected = headphones(audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)).toMutableSet()

            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                // A Bluetooth headset may come as several devices: the rule runs once, when the first arrives.
                val had = connected.isNotEmpty()
                connected += headphones(addedDevices)
                if (!had && connected.isNotEmpty()) onEvent(app, TriggerEvent.HEADPHONES)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                connected -= removedDevices.map { it.id }.toSet()
            }
        }
        runCatching { audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper())) }
    }

    private val headphoneTypes = buildSet {
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        add(AudioDeviceInfo.TYPE_USB_HEADSET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
    }

    private const val FIRE_TIMEOUT_MS = 9_000L

    internal const val FIRE = ACTION_FIRE
    internal const val ID = EXTRA_ID
}

/**
 * Fires a scheduled job, and arms them all again after a reboot, an update or when exact alarms
 * are allowed, so the jobs armed inexactly before run on time.
 */
class CommandJobReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CommandScheduler.FIRE -> {
                val pending = goAsync()
                CommandScheduler.fireFromAlarm(context, intent.getLongExtra(CommandScheduler.ID, -1)) { pending.finish() }
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> CommandScheduler.armAll(context)
        }
    }
}
