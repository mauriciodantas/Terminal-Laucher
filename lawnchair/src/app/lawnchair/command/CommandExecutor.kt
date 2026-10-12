package app.lawnchair.command

import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import app.lawnchair.theme.SystemColorMode

/** Runs a [CommandAction] and loads the data the command bar completes from. */
object CommandExecutor {

    /** The last app list, shown right away while a fresh one loads. */
    @Volatile
    var cachedApps: List<AppEntry>? = null
        private set

    /** Loads the app list on a background thread so the first open of the command bar is not slow. */
    fun prewarm(context: Context) {
        val app = context.applicationContext
        Thread { runCatching { loadApps(app) } }.start()
        SystemSwitches.watchTorch(app)
    }

    fun loadApps(context: Context): List<AppEntry> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        return launcherApps.getActivityList(null, Process.myUserHandle())
            .map { AppEntry(it.label.toString(), it.componentName.flattenToString()) }
            .distinctBy { it.id }
            .sortedBy { it.label.lowercase() }
            .also { cachedApps = it }
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
            val emails = loadEmails(context)
            result.values.map { it.copy(email = emails[it.name].orEmpty()) }
        }.getOrDefault(emptyList())
    }

    /** The first e-mail of each contact name, for "ana?". */
    private fun loadEmails(context: Context): Map<String, String> = runCatching {
        val emails = HashMap<String, String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY, ContactsContract.CommonDataKinds.Email.ADDRESS),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext() && emails.size < MAX_CONTACTS) {
                val name = cursor.getString(0) ?: continue
                val address = cursor.getString(1) ?: continue
                emails.putIfAbsent(name, address)
            }
        }
        emails
    }.getOrDefault(emptyMap())

    fun hasContactsPermission(context: Context): Boolean = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) ==
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
                    .putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                    .apply { if (action.days.isNotEmpty()) putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, ArrayList(action.days)) },
            )

            is CommandAction.SetTimer -> start(
                context,
                Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
            )

            is CommandAction.Calc -> copy(context, action.result)

            is CommandAction.Copy -> copy(context, action.text)

            is CommandAction.Schedule -> CommandScheduler.add(context, action.job)

            is CommandAction.CancelJob -> CommandScheduler.cancel(context, action.job)

            is CommandAction.Toggle -> return SystemSwitches.toggle(context, action.target, action.on)

            is CommandAction.Brightness -> return SystemSwitches.brightness(context, action.percent)

            is CommandAction.Volume -> return SystemSwitches.volume(context, action.percent)

            is CommandAction.Media -> return SystemSwitches.media(context, action.key)

            is CommandAction.OpenSettings -> openSettings(context, action.page)

            is CommandAction.OpenCalendar -> start(
                context,
                Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(action.at.toString()).build()),
            )

            is CommandAction.AddNote -> NoteStore.add(context, action.text)

            is CommandAction.ShareNotes -> start(
                context,
                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, action.text), null),
            )

            // The command bar puts the text on its line and shows the ping output itself.
            is CommandAction.FillLine, is CommandAction.Ping -> return false

            // Only the setup opens a screen; the bar talks to the AI itself.
            is CommandAction.Ai -> {
                if (action.mode != AiMode.SETUP) return false
                start(
                    context,
                    app.lawnchair.ui.preferences.PreferenceActivity.createIntent(
                        context,
                        app.lawnchair.ui.preferences.navigation.CommandActions,
                    ),
                )
            }

            is CommandAction.Chain -> return action.steps.map { execute(context, it) }.all { it }

            is CommandAction.Call -> {
                val uri = Uri.fromParts("tel", action.contact.number, null)
                // Calls straight away when allowed; otherwise the dialer opens with the number.
                start(context, Intent(if (hasCallPermission(context)) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri))
            }

            is CommandAction.Custom -> return runCustom(context, action)

            is CommandAction.SplitScreen -> return launchSplit(context, action)

            is CommandAction.Theme -> when (action.change) {
                ThemeChange.INVERT -> SystemColorMode.pin(context, SystemColorMode.Pin.INVERTED)
                ThemeChange.LIGHT -> SystemColorMode.pin(context, SystemColorMode.Pin.LIGHT)
                ThemeChange.DARK -> SystemColorMode.pin(context, SystemColorMode.Pin.DARK)
                ThemeChange.SYSTEM -> SystemColorMode.follow(context)
                ThemeChange.RESET -> ThemeStore.reset(context)
            }

            is CommandAction.ThemeApply -> return ThemeStore.apply(context, action.spec)

            is CommandAction.ThemeSave -> ThemeStore.saveCurrent(context, action.name)

            is CommandAction.ThemeDelete -> ThemeStore.remove(context, action.name)

            is CommandAction.ThemeShare -> {
                val spec = ThemeStore.named(context, action.name) ?: return false
                start(context, ThemeStore.shareIntent(context, spec) ?: return false)
            }

            is CommandAction.Route -> start(
                context,
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(action.query))),
            )

            is CommandAction.NewTask -> start(context, TaskApp.intent(context, action.title))

            is CommandAction.WebSearch -> start(
                context,
                Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, action.query),
            )
        }
        true
    }.getOrDefault(false)

    /**
     * True when [action] works without the command bar in front: it changes a setting or saves
     * something instead of opening a screen, which Android blocks from the background.
     */
    fun runsInBackground(context: Context, action: CommandAction): Boolean = when (action) {
        is CommandAction.Theme, is CommandAction.ThemeApply, is CommandAction.ThemeSave, is CommandAction.ThemeDelete, is CommandAction.Calc, is CommandAction.Copy, is CommandAction.Schedule,
        is CommandAction.CancelJob, is CommandAction.Volume, is CommandAction.Media, is CommandAction.AddNote,
        -> true

        // Without the special access these open the screen that grants it, which needs the bar in front.
        is CommandAction.Brightness -> android.provider.Settings.System.canWrite(context)

        is CommandAction.Toggle -> when (action.target) {
            SystemToggle.TORCH -> true
            SystemToggle.ROTATION -> android.provider.Settings.System.canWrite(context)
            SystemToggle.DND -> context.getSystemService(android.app.NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
            SystemToggle.WIFI, SystemToggle.BLUETOOTH -> false
        }

        is CommandAction.Chain -> action.steps.all { runsInBackground(context, it) }

        else -> false
    }

    private fun copy(context: Context, text: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("command", text))
    }

    private fun openSettings(context: Context, page: SettingsPage) {
        if (page == SettingsPage.LAUNCHER) {
            start(context, Intent(context, app.lawnchair.ui.preferences.PreferenceActivity::class.java))
            return
        }
        val intent = Intent(page.action)
        start(context, if (context.packageManager.resolveActivity(intent, 0) != null) intent else Intent(android.provider.Settings.ACTION_SETTINGS))
    }

    fun hasCallPermission(context: Context): Boolean = context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) ==
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
    fun probeIntents(context: Context, packageName: String): List<CustomAction> = CustomActions.PROBES.filter {
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

    private fun packageOf(app: AppEntry): String? = ComponentName.unflattenFromString(app.id)?.packageName

    /** What [packageName] offers to bind to a letter: catalog recipes, intents it answers to and launcher shortcuts. */
    fun loadIntegrations(context: Context, packageName: String): List<CustomAction> = CustomActions.CATALOG.filter { packageName in it.packages } +
        loadShortcuts(context, packageName) +
        probeIntents(context, packageName)

    /** Only the apps with something to bind, one entry per package; apps that just open are left out. */
    fun loadIntegrationApps(context: Context): List<AppEntry> = loadApps(context)
        .distinctBy { packageOf(it) }
        .filter { app ->
            val pkg = packageOf(app) ?: return@filter false
            CustomActions.CATALOG.any { pkg in it.packages } ||
                loadShortcuts(context, pkg).isNotEmpty() ||
                probeIntents(context, pkg).isNotEmpty()
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

    /**
     * Puts the two apps of [action] side by side. The system only splits the screen for a launch
     * with FLAG_ACTIVITY_LAUNCH_ADJACENT made by the activity in front, so from the command bar
     * ([context] is that activity) the second app opens beside the bar, and then the first one is
     * opened from inside the split, taking the bar's half, and the bar closes. Without an activity
     * (an assistant call) both are opened one after the other, which may not split.
     */
    private fun launchSplit(context: Context, action: CommandAction.SplitScreen): Boolean {
        val first = launchIntent(context, action.first) ?: return false
        val second = launchIntent(context, action.second) ?: return false
        val adjacent = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
        val activity = context as? android.app.Activity
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        if (activity == null) {
            val app = context.applicationContext
            app.startActivity(first.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            handler.postDelayed({ runCatching { app.startActivity(second.addFlags(adjacent)) } }, SPLIT_SECOND_DELAY_MS)
            return true
        }
        activity.startActivity(second.addFlags(adjacent))
        handler.postDelayed({
            runCatching { activity.startActivity(first.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            activity.finish()
        }, SPLIT_SECOND_DELAY_MS)
        return true
    }

    private fun launchIntent(context: Context, app: AppEntry): Intent? {
        val component = ComponentName.unflattenFromString(app.id) ?: return null
        return Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
    }

    private fun start(context: Context, intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private const val MAX_CONTACTS = 2000
    private const val SPLIT_SECOND_DELAY_MS = 700L
}
