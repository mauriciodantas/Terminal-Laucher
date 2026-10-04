package app.lawnchair.command

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.app.SearchManager

/** Runs a [CommandAction] and loads the data the command bar completes from. */
object CommandExecutor {

    fun loadApps(context: Context): List<AppEntry> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        return launcherApps.getActivityList(null, Process.myUserHandle())
            .map { AppEntry(it.label.toString(), it.componentName.flattenToString()) }
            .distinctBy { it.id }
            .sortedBy { it.label.lowercase() }
    }

    /** Contacts with a phone number, or nothing when the permission is not granted. */
    fun loadContacts(context: Context): List<ContactEntry> {
        if (!hasContactsPermission(context)) return emptyList()
        return runCatching {
            val result = linkedMapOf<String, ContactEntry>()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC",
            )?.use { cursor ->
                while (cursor.moveToNext() && result.size < MAX_CONTACTS) {
                    val name = cursor.getString(0) ?: continue
                    val number = cursor.getString(1) ?: continue
                    result.putIfAbsent(name, ContactEntry(name, number))
                }
            }
            result.values.toList()
        }.getOrDefault(emptyList())
    }

    fun hasContactsPermission(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** True when the action was handed to the system, false when nothing could handle it. */
    fun execute(context: Context, action: CommandAction): Boolean = runCatching {
        when (action) {
            is CommandAction.OpenApp -> {
                val component = ComponentName.unflattenFromString(action.app.id) ?: return false
                context.getSystemService(LauncherApps::class.java)
                    .startMainActivity(component, Process.myUserHandle(), null, null)
            }
            is CommandAction.SetAlarm -> start(
                context,
                Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, action.minute),
            )
            is CommandAction.Calc -> {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("calc", action.result))
            }
            is CommandAction.Call -> start(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", action.contact.number, null)))
            is CommandAction.Message -> start(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("sms", action.contact.number, null)))
            is CommandAction.Route -> start(
                context,
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(action.query))),
            )
            is CommandAction.NewTask -> start(
                context,
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, action.title),
                    null,
                ),
            )
            is CommandAction.WebSearch -> start(
                context,
                Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, action.query),
            )
        }
        true
    }.getOrDefault(false)

    private fun start(context: Context, intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private const val MAX_CONTACTS = 2000
}
