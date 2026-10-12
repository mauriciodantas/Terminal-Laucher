package app.lawnchair.command

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * The app "t [task]" sends to. Android has no standard action for tasks, but almost every tasks
 * app accepts shared text, so the bar shares straight to the one the user picked: the first time
 * through the system list, which reports the choice to [TaskAppChosenReceiver], or in settings.
 */
object TaskApp {
    private const val STORE = "nostromo"
    private const val KEY = "command_task_app"

    private fun prefs(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    private fun shareIntent(text: String? = null) = Intent(Intent.ACTION_SEND).setType("text/plain").apply {
        if (text != null) putExtra(Intent.EXTRA_TEXT, text)
    }

    /** The chosen screen, or null when none was chosen or its app is gone. */
    fun load(context: Context): ComponentName? {
        val component = prefs(context).getString(KEY, null)?.let { ComponentName.unflattenFromString(it) } ?: return null
        val installed = runCatching { context.packageManager.getActivityInfo(component, 0) }.isSuccess
        return component.takeIf { installed }
    }

    fun save(context: Context, component: ComponentName?) {
        prefs(context).edit().apply {
            if (component == null) remove(KEY) else putString(KEY, component.flattenToString())
        }.apply()
    }

    /** The chosen app's name, for the preview and the settings; null when none is chosen. */
    fun label(context: Context): String? = load(context)?.let { component ->
        runCatching { context.packageManager.getActivityInfo(component, 0).loadLabel(context.packageManager).toString() }.getOrNull()
    }

    /** Every screen that accepts shared text, by name, for the settings list. */
    fun candidates(context: Context): List<AppEntry> = context.packageManager
        .queryIntentActivities(shareIntent(), PackageManager.MATCH_DEFAULT_ONLY)
        .filter { it.activityInfo.packageName != context.packageName }
        .map { info ->
            val component = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
            AppEntry(info.loadLabel(context.packageManager).toString(), component.flattenToString())
        }
        .distinctBy { it.id }
        .sortedBy { it.label.lowercase() }

    /** Straight to the chosen app, or the system list that remembers the pick when there is none. */
    fun intent(context: Context, title: String): Intent {
        val chosen = load(context)
        if (chosen != null) return shareIntent(title).setComponent(chosen)
        val callback = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, TaskAppChosenReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
        )
        return Intent.createChooser(shareIntent(title), null, callback.intentSender)
    }
}

/** Remembers the app picked in the system list the first time a task is sent. */
class TaskAppChosenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val component = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT)
        }
        if (component != null) TaskApp.save(context, component)
    }
}
