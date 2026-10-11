package app.lawnchair.command

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.android.launcher3.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    /** Arms every saved job again, after a reboot or an app update; runs the ones missed meanwhile. */
    fun armAll(context: Context) {
        val now = System.currentTimeMillis()
        JobStore.load(context).filter { it.kind != JobKind.RULE }.forEach { job ->
            if (job.at <= now) fire(context, job.id) else arm(context, job)
        }
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, job.at, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, job.at, intent)
        }
    }

    internal fun fire(context: Context, id: Long) {
        val job = JobStore.load(context).firstOrNull { it.id == id } ?: return
        JobStore.remove(context, id)
        when (job.kind) {
            JobKind.REMIND -> notify(context, id, context.getString(R.string.cmd_notify_reminder), job.text, null)
            JobKind.COMMAND, JobKind.RULE -> runLine(context, job.text, id)
        }
    }

    /** Runs the rules that wait for [event]. */
    fun onEvent(context: Context, event: TriggerEvent) {
        JobStore.load(context).filter { it.kind == JobKind.RULE && it.event == event }.forEach { runLine(context, it.text, it.id) }
    }

    /** Runs [line] now when it can run from the background, otherwise offers it in a notification. */
    fun runLine(context: Context, line: String, id: Long) {
        val app = context.applicationContext
        scope.launch {
            val analysis = CommandLine.analyze(app, line)
            val action = analysis.action
            when {
                action == null -> notify(app, id, context.getString(R.string.cmd_notify_failed), line, null)

                CommandExecutor.runsInBackground(app, action) -> if (!CommandExecutor.execute(app, action)) {
                    notify(app, id, context.getString(R.string.cmd_notify_failed), line, null)
                }

                else -> notify(app, id, context.getString(R.string.cmd_notify_tap_to_run), line, line)
            }
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
            addAction(AudioManager.ACTION_HEADSET_PLUG)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // The headset state is sticky: the copy delivered on registering is not an event.
                if (isInitialStickyBroadcast) return
                val event = when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> TriggerEvent.CHARGING
                    Intent.ACTION_POWER_DISCONNECTED -> TriggerEvent.UNPLUGGED
                    AudioManager.ACTION_HEADSET_PLUG -> TriggerEvent.HEADPHONES.takeIf { intent.getIntExtra("state", 0) == 1 }
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
    }

    internal const val FIRE = ACTION_FIRE
    internal const val ID = EXTRA_ID
}

/** Fires a scheduled job, and arms them all again after a reboot or an update. */
class CommandJobReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CommandScheduler.FIRE -> CommandScheduler.fire(context, intent.getLongExtra(CommandScheduler.ID, -1))
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> CommandScheduler.armAll(context)
        }
    }
}
