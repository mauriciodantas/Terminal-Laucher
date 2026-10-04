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
            is CommandAction.Call -> {
                val uri = Uri.fromParts("tel", action.contact.number, null)
                // Calls straight away when allowed; otherwise the dialer opens with the number.
                start(context, Intent(if (hasCallPermission(context)) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri))
            }
            is CommandAction.Custom -> return runCustom(context, action)
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

    fun hasCallPermission(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** The number with country code and digits only, as most deep links want it. */
    private fun normalizedNumber(raw: String): String {
        val region = java.util.Locale.getDefault().country
        val e164 = runCatching { android.telephony.PhoneNumberUtils.formatNumberToE164(raw, region) }.getOrNull()
        return (e164 ?: raw).filter { it.isDigit() }
    }

    /** Fills the placeholders of [template]; an empty `?text=` style parameter is dropped. */
    fun fillTemplate(template: String, contact: ContactEntry?, text: String, normalize: (String) -> String): String {
        val values = mapOf(
            "{number}" to contact?.let { normalize(it.number) }.orEmpty(),
            "{phone}" to Uri.encode(contact?.number.orEmpty()),
            "{name}" to Uri.encode(contact?.name.orEmpty()),
            "{text}" to Uri.encode(text),
        )
        var result = template
        values.forEach { (key, value) -> result = result.replace(key, value) }
        // "…?text=" with nothing after it would open the chat with an empty draft parameter.
        return result.replace(Regex("[?&][^?&=]+=$"), "")
    }

    private fun runCustom(context: Context, action: CommandAction.Custom): Boolean {
        val spec = action.action
        if (spec.kind == ActionKind.SHORTCUT) return startShortcut(context, spec)
        val targets = if (spec.packages.isEmpty()) listOf<String?>(null) else spec.packages
        for (pkg in targets) {
            val intent = buildIntent(spec, action.contact, action.text, pkg)
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                start(context, intent)
                return true
            }
        }
        val contact = action.contact
        if (spec.smsFallback && contact != null) {
            start(
                context,
                Intent(Intent.ACTION_SENDTO, Uri.fromParts("sms", contact.number, null))
                    .putExtra("sms_body", action.text),
            )
            return true
        }
        return false
    }

    /** The intent of [spec] filled with what was typed, aimed at [pkg] when given. */
    fun buildIntent(spec: CustomAction, contact: ContactEntry?, text: String, pkg: String?): Intent {
        val intent = Intent(spec.intentAction)
        if (spec.template.isNotEmpty()) {
            val uri = Uri.parse(fillTemplate(spec.template, contact, text, ::normalizedNumber))
            if (spec.mimeType != null) intent.setDataAndType(uri, spec.mimeType) else intent.data = uri
        } else if (spec.mimeType != null) {
            intent.type = spec.mimeType
        }
        spec.textExtra?.let { intent.putExtra(it, text) }
        pkg?.let { intent.setPackage(it) }
        return intent
    }

    /** Recipes from [CustomActions.PROBES] that [packageName] answers to, ready to bind to a letter. */
    fun probeIntents(context: Context, packageName: String): List<CustomAction> =
        CustomActions.PROBES.filter {
            val sample = ContactEntry("", "1")
            val intent = buildIntent(it, sample, "x", packageName)
            context.packageManager.resolveActivity(intent, 0) != null
        }.map { it.copy(packages = listOf(packageName)) }

    private fun startShortcut(context: Context, spec: CustomAction): Boolean {
        val pkg = spec.packages.firstOrNull() ?: return false
        context.getSystemService(LauncherApps::class.java)
            .startShortcut(pkg, spec.template, null, null, Process.myUserHandle())
        return true
    }

    /** Launcher shortcuts an app publishes (manifest, dynamic and pinned), usable as actions. */
    fun loadShortcuts(context: Context, packageName: String): List<CustomAction> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        return runCatching {
            launcherApps.getShortcuts(query, Process.myUserHandle()).orEmpty()
                .filter { it.isEnabled }
                .map {
                    CustomAction(
                        letter = "",
                        label = it.shortLabel?.toString() ?: it.id,
                        kind = ActionKind.SHORTCUT,
                        template = it.id,
                        packages = listOf(packageName),
                        arg = ArgKind.NONE,
                    )
                }
        }.getOrDefault(emptyList())
    }

    private fun start(context: Context, intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private const val MAX_CONTACTS = 2000
}
