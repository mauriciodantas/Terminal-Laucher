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
import android.provider.Settings
import androidx.core.content.FileProvider
import app.lawnchair.theme.SystemColorMode
import app.lawnchair.widgets.lottie.ImportWidgetActivity
import app.lawnchair.widgets.lottie.LottieWidgetPack
import app.lawnchair.widgets.lottie.LottieWidgetPacks
import app.lawnchair.widgets.lottie.LottieWidgetStore.Playback
import java.io.File
import org.json.JSONObject

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
            is CommandAction.FillLine, is CommandAction.Ping, is CommandAction.SpeedTest, is CommandAction.ShowQr, is CommandAction.Weather -> return false

            is CommandAction.Nearby -> start(
                context,
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(action.query))),
            )

            // The system shows its own confirmation; REQUEST_DELETE_PACKAGES lets the launcher ask.
            is CommandAction.Uninstall -> start(
                context,
                Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageOf(action.app) ?: return false, null)),
            )

            is CommandAction.AppInfo -> start(
                context,
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageOf(action.app) ?: return false, null)),
            )

            is CommandAction.UpdateApp -> return openStore(context, action.app)

            is CommandAction.InstallPack -> return installPack(context, action.id) != null

            is CommandAction.RemovePack -> {
                val left = BundledPacks.remove(CustomActionStore.load(context), ProviderStore.load(context), action.id)
                CustomActionStore.save(context, left.actions)
                ProviderStore.save(context, left.providers)
            }

            is CommandAction.AiSetup -> {
                if (action.remove) {
                    AiStore.save(context, null)
                } else {
                    val base = AiStore.load(context)
                    val key = action.key ?: base?.key ?: return false
                    val provider = action.provider ?: AiProvider.guess(key) ?: base?.provider ?: AiProvider.OPENAI
                    // The model of one service means nothing to another: switching drops it unless a new one comes with it.
                    val model = action.model ?: if (base?.provider == provider) base.model else ""
                    AiStore.save(context, AiConfig(provider, key, model, base?.timeoutSeconds ?: AiConfig.DEFAULT_TIMEOUT))
                }
            }

            // Only the setup opens a screen; the bar talks to the AI itself.
            is CommandAction.Ai -> {
                if (action.mode != AiMode.SETUP) return false
                start(
                    context,
                    app.lawnchair.ui.preferences.PreferenceActivity.createIntent(
                        context,
                        app.lawnchair.ui.preferences.navigation.CommandActions(),
                    ),
                )
            }

            is CommandAction.Chain -> {
                // A step joined with "&&" runs only if the one before it worked; ";" runs whatever happened.
                var last = true
                var all = true
                action.steps.forEachIndexed { i, step ->
                    if (action.andThen.getOrElse(i) { false } && !last) {
                        all = false
                        return@forEachIndexed
                    }
                    last = execute(context, step)
                    if (!last) all = false
                }
                return all
            }

            is CommandAction.SetVariable -> {
                UserVariableStore.save(context, UserVariables.set(UserVariableStore.load(context), action.name, action.value))
            }

            is CommandAction.UnsetVariable -> {
                UserVariableStore.save(context, UserVariables.remove(UserVariableStore.load(context), action.name))
            }

            is CommandAction.ClearHistory -> CommandActivity.clearHistory(context)

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

            is CommandAction.OpenUrl -> start(
                context,
                Intent(Intent.ACTION_VIEW, Uri.parse(action.url)).addCategory(Intent.CATEGORY_BROWSABLE),
            )

            is CommandAction.ImportTheme -> return ThemeStore.review(context, action.spec)

            is CommandAction.ImportWidget -> {
                val dir = File(context.cacheDir, "widgets").apply { mkdirs() }
                val file = File(dir, "import.${LottieWidgetPacks.EXTENSION}")
                val json = action.json.trim()
                val pack = if (json.startsWith("{") && runCatching { JSONObject(json).optString("format") == "terminal-widget" }.getOrDefault(false)) {
                    val o = JSONObject(json)
                    val animBytes = when {
                        o.has("animation") -> {
                            val a = o.opt("animation")
                            if (a is JSONObject) {
                                a.toString().toByteArray(Charsets.UTF_8)
                            } else {
                                a?.toString().orEmpty().toByteArray(Charsets.UTF_8)
                            }
                        }

                        else -> json.toByteArray(Charsets.UTF_8)
                    }
                    LottieWidgetPack(
                        name = o.optString("name").ifBlank { action.name },
                        animation = animBytes,
                        command = o.optString("command").trim(),
                        playback = Playback.entries.firstOrNull { it.name == o.optString("playback") } ?: Playback.LOOP,
                        themeColors = o.optBoolean("themeColors", false),
                        commands = o.optJSONObject("commands")?.let { runCatching { CommandPacks.decode(it.toString()) }.getOrNull() } ?: CommandPack(emptyList(), emptyList()),
                    )
                } else {
                    LottieWidgetPack(
                        name = action.name.ifBlank { "widget.json" },
                        animation = action.json.toByteArray(Charsets.UTF_8),
                        command = "",
                        playback = Playback.LOOP,
                        themeColors = false,
                        commands = CommandPack(emptyList(), emptyList()),
                    )
                }
                file.outputStream().use { LottieWidgetPacks.write(pack, it) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                start(
                    context,
                    Intent(context, ImportWidgetActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .setData(uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            }

            is CommandAction.ImportCommands -> {
                val dir = File(context.cacheDir, "commands").apply { mkdirs() }
                val file = File(dir, "import.${CommandPacks.EXTENSION}").apply {
                    writeText(CommandPacks.encode(action.pack.actions, action.pack.aliases, action.pack.providers))
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                start(
                    context,
                    Intent(context, ImportCommandsActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .setData(uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            }

            is CommandAction.WebSearch -> start(
                context,
                Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, action.query),
            )

            is CommandAction.SaveCommand -> {
                when (val cmd = action.command) {
                    is ParsedCommand.Action -> {
                        val existing = CustomActionStore.load(context).filterNot { it.letter == cmd.action.letter }
                        CustomActionStore.save(context, existing + cmd.action)
                        val existingAliases = AliasStore.load(context).filterNot { it.name.lowercase() == cmd.action.letter }
                        AliasStore.save(context, existingAliases)
                    }

                    is ParsedCommand.Alias -> {
                        val existing = AliasStore.load(context).filterNot { it.name.lowercase() == cmd.alias.name.lowercase() }
                        AliasStore.save(context, existing + cmd.alias)
                        val existingActions = CustomActionStore.load(context).filterNot { it.letter == cmd.alias.name.lowercase() }
                        CustomActionStore.save(context, existingActions)
                    }
                }
            }

            is CommandAction.RemoveCommand -> {
                val name = action.name.lowercase()
                val existingActions = CustomActionStore.load(context).filterNot { it.letter == name }
                CustomActionStore.save(context, existingActions)
                val existingAliases = AliasStore.load(context).filterNot { it.name.lowercase() == name }
                AliasStore.save(context, existingAliases)
            }
        }
        true
    }.getOrDefault(false)

    /**
     * True when [action] works without the command bar in front: it changes a setting or saves
     * something instead of opening a screen, which Android blocks from the background.
     */
    fun runsInBackground(context: Context, action: CommandAction): Boolean = when (action) {
        is CommandAction.Theme, is CommandAction.ThemeApply, is CommandAction.ThemeSave, is CommandAction.ThemeDelete, is CommandAction.Calc, is CommandAction.Copy, is CommandAction.Schedule,
        is CommandAction.CancelJob, is CommandAction.Volume, is CommandAction.Media, is CommandAction.AddNote, is CommandAction.AiSetup,
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
        // Without the system cleanup (some makers drop it), the storage screen is the nearest thing.
        val fallback = if (page == SettingsPage.FREE_SPACE) Intent(SettingsPage.STORAGE.action) else Intent(android.provider.Settings.ACTION_SETTINGS)
        val intent = Intent(page.action)
        start(context, if (context.packageManager.resolveActivity(intent, 0) != null) intent else fallback)
    }

    private const val PLAY_STORE = "com.android.vending"

    /** The Play Store page of [app], in the store app when there is one; the store itself when null. */
    private fun openStore(context: Context, app: AppEntry?): Boolean {
        val pm = context.packageManager
        if (app == null) {
            val store = pm.getLaunchIntentForPackage(PLAY_STORE) ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps"))
            start(context, store)
            return true
        }
        val pkg = packageOf(app) ?: return false
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).setPackage(PLAY_STORE)
        start(context, if (pm.resolveActivity(market, 0) != null) market else Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
        return true
    }

    /**
     * Adds the [BundledPack] [id] to the saved actions and providers. Returns how many of its
     * actions were left out because the user already has their names, or null when there is no such pack.
     */
    fun installPack(context: Context, id: String): Int? {
        val pack = BundledPacks.of(id) ?: return null
        val result = BundledPacks.install(CustomActionStore.load(context), AliasStore.load(context), ProviderStore.load(context), pack)
        CustomActionStore.save(context, result.actions)
        ProviderStore.save(context, result.providers)
        return result.skipped
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
        // Its answer is shown in the command bar, which fetches it.
        if (spec.kind == ActionKind.API) return false
        val targets = if (spec.packages.isEmpty()) listOf<String?>(null) else spec.packages
        for (pkg in targets) {
            val intent = buildIntent(spec, action.contact, action.text, pkg)
            if (spec.target == "broadcast") {
                context.sendBroadcast(intent)
                return true
            }
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
        spec.categories.forEach { intent.addCategory(it) }
        spec.component?.let {
            val comp = ComponentName.unflattenFromString(it)
            if (comp != null) intent.component = comp
        }
        spec.effectiveExtras().forEach { extra ->
            val filled = fillTemplate(extra.value, contact, text, ::normalizedNumber)
            if (filled.isNotEmpty()) {
                when (extra.type) {
                    ExtraType.STRING -> intent.putExtra(extra.key, spec.extraFor(filled) ?: filled)
                    ExtraType.INT -> filled.toIntOrNull()?.let { intent.putExtra(extra.key, it) }
                    ExtraType.BOOL -> intent.putExtra(extra.key, filled.toBooleanStrictOrNull() ?: (filled == "1" || filled.equals("true", ignoreCase = true)))
                    ExtraType.LONG -> filled.toLongOrNull()?.let { intent.putExtra(extra.key, it) }
                    ExtraType.FLOAT -> filled.toFloatOrNull()?.let { intent.putExtra(extra.key, it) }
                    ExtraType.URI -> intent.putExtra(extra.key, Uri.parse(filled))
                }
            }
        }
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
