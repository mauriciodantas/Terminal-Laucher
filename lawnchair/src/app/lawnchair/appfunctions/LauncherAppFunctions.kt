package app.lawnchair.appfunctions

import android.content.ComponentName
import android.content.Context
import android.os.Process
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import androidx.appfunctions.AppFunctionStringValueConstraint
import app.lawnchair.command.AliasStore
import app.lawnchair.command.Aliases
import app.lawnchair.command.AppEntry
import app.lawnchair.command.CalcEvaluator
import app.lawnchair.command.CommandAction
import app.lawnchair.command.CommandAlias
import app.lawnchair.command.CommandEngine
import app.lawnchair.command.CommandExecutor
import app.lawnchair.command.CustomActionStore
import app.lawnchair.command.VoiceCommand
import app.lawnchair.data.AppDatabase
import app.lawnchair.data.folder.service.FolderService
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.smartspace.glance.GlanceKind
import app.lawnchair.smartspace.provider.SmartspaceProvider
import app.lawnchair.theme.color.ColorOption
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.util.ComponentKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** An app installed on the device that the launcher can open. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AppSummary(
    /** The name shown under the app's icon. Pass it to openApp. */
    val name: String,
)

/** One line of the home screen "At a Glance" panel. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class GlanceItem(
    /** What the line is about: AGENDA, CLIMA, MIDIA, ALARME, LEMBRETE, BATERIA or AVISO. */
    val kind: String,
    /** The main text, for example the event name, the temperature or the Bluetooth device. */
    val title: String,
    /** The detail text, for example the time or the battery percentages. May be empty. */
    val detail: String,
)

/** A folder of the app drawer and the apps inside it. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class FolderSummary(
    /** The folder name. */
    val name: String,
    /** The names of the apps in the folder. */
    val apps: List<String>,
)

/** A word the user made up for a whole command of the command bar. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class AliasSummary(
    /** The word. */
    val alias: String,
    /** The command it stands for, for example "ligar maria". */
    val command: String,
)

/** What happened when a command was run. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class CommandResult(
    /**
     * EXECUTED when the command ran, RESULT when it only produced an answer, NEEDS_CONFIRMATION when
     * it would call or message someone and was not run, NOT_RUNNABLE when it is incomplete or
     * unknown, FAILED when no app could handle it.
     */
    val status: String,
    /** A short human readable description of what happened or what is missing. */
    val message: String,
)

/**
 * The launcher as a set of tools for assistants: open apps, run command bar commands, calculate,
 * read what the home screen shows and tidy the launcher (folders, aliases, colour, panel items,
 * hidden apps). Nothing here sends anything to another person: commands that would call or message
 * someone are never run, only described. Aliases, folders, hidden apps, the colour and the panel
 * items can all be put back with the matching function.
 */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "LauncherAppFunctionService",
    appFunctionXmlFileName = "launcher_app_function_service",
)
abstract class BaseLauncherAppFunctionService : AppFunctionService() {

    /**
     * Opens an installed app on the device by its name, as in the launcher's app drawer.
     *
     * @param appName The name of the app, for example "Chrome". Case and accents do not matter, and the
     *   start of the name is enough. Call searchApps first when the name is uncertain.
     * @return A [CommandResult] saying whether the app was opened.
     * @throws AppFunctionInvalidArgumentException If appName is blank.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun openApp(appName: String): CommandResult {
        if (appName.isBlank()) throw AppFunctionInvalidArgumentException("appName cannot be blank")
        return runCommandText("abrir ${appName.trim()}")
    }

    /**
     * Runs a command of the launcher's command bar. Understands the built-in commands (abrir, calc,
     * alarme, rota, t) and the user's own aliases and custom actions. Commands that call a contact or
     * send a message are not run: the result is NEEDS_CONFIRMATION and the user must confirm them.
     *
     * @param command The command text, for example "abrir camera", "alarme 0630", "calc 12*8" or "rota Avenida Paulista".
     * @return A [CommandResult] with the outcome.
     * @throws AppFunctionInvalidArgumentException If command is blank.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun runCommand(command: String): CommandResult {
        if (command.isBlank()) throw AppFunctionInvalidArgumentException("command cannot be blank")
        return runCommandText(command)
    }

    /**
     * Evaluates an arithmetic expression with + - * / % and parentheses.
     *
     * @param expression For example "12*8" or "(3+4)*2".
     * @return The result as text, formatted like the command bar does.
     * @throws AppFunctionInvalidArgumentException If the expression is empty or cannot be evaluated.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun calculate(expression: String): String {
        val value = if (expression.isBlank()) null else CalcEvaluator.evaluate(expression.trim())
        return value?.let { CalcEvaluator.format(it) }
            ?: throw AppFunctionInvalidArgumentException("Cannot evaluate \"$expression\"")
    }

    /**
     * Lists the installed apps whose name starts with, or contains, a text.
     *
     * @param query Part of the app name. Blank lists the first apps in alphabetical order.
     * @return Up to 25 matching apps, best matches first.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchApps(query: String): List<AppSummary> {
        val apps = loadApps()
        val q = query.trim().lowercase()
        val matches = if (q.isEmpty()) {
            apps
        } else {
            apps.filter { it.label.lowercase().startsWith(q) } +
                apps.filter { !it.label.lowercase().startsWith(q) && it.label.lowercase().contains(q) }
        }
        return matches.take(MAX_APPS).map { AppSummary(it.label) }
    }

    /**
     * Reads what the home screen "At a Glance" panel is showing now: next events, weather, alarms,
     * reminders, media and the battery of connected Bluetooth devices.
     *
     * @return The panel lines, most important first. Empty when nothing is shown.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getGlanceSummary(): List<GlanceItem> {
        val provider = SmartspaceProvider.INSTANCE.get(applicationContext)
        val targets = withTimeoutOrNull(GLANCE_TIMEOUT_MS) { provider.targets.first() }.orEmpty()
        val prefs = PreferenceManager2.INSTANCE.get(applicationContext)
        return targets.mapNotNull { target ->
            val action = target.headerAction ?: return@mapNotNull null
            val kind = GlanceKind.from(target.featureType, target.id)
            // The panel leaves out what the user turned off, so the summary does too.
            if (glancePreference(prefs, kind)?.firstCached() == false) return@mapNotNull null
            GlanceItem(
                kind = kind.name,
                title = action.title.toString(),
                detail = action.subtitle?.toString().orEmpty(),
            )
        }
    }

    /**
     * Lists the folders the user created in the app drawer.
     *
     * @return Each folder with the names of its apps.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listFolders(): List<FolderSummary> = withContext(Dispatchers.IO) {
        val labels = loadApps().associate { it.id.substringBefore('#') to it.label }
        AppDatabase.INSTANCE.get(applicationContext).folderDao().getAllFoldersWithItems().first()
            .map { entry ->
                FolderSummary(
                    name = entry.folder.title,
                    apps = entry.items.mapNotNull { item ->
                        val key = item.componentKey?.substringBefore('#') ?: return@mapNotNull null
                        labels[key] ?: key.substringBefore('/')
                    },
                )
            }
    }

    /**
     * Lists the aliases the user defined for the command bar. Each alias can be used with runCommand.
     *
     * @return The aliases and the commands they stand for.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listAliases(): List<AliasSummary> = withContext(Dispatchers.IO) {
        AliasStore.load(applicationContext).map { AliasSummary(it.name, it.expansion) }
    }

    /**
     * Creates a folder in the app drawer with the given apps. Apps already in another folder are refused.
     *
     * @param name The folder name, for example "Trabalho". Must not already exist.
     * @param appNames The names of at least two apps to put in the folder. Use searchApps to confirm names.
     * @return The created folder with its apps.
     * @throws AppFunctionInvalidArgumentException If the name is blank or taken, fewer than two apps are given, or an app is already in another folder.
     * @throws AppFunctionElementNotFoundException If an app name matches no installed app.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun createFolder(name: String, appNames: List<String>): FolderSummary {
        val title = name.trim()
        if (title.isEmpty()) throw AppFunctionInvalidArgumentException("name cannot be blank")
        val apps = resolveApps(appNames)
        if (apps.size < 2) {
            throw AppFunctionInvalidArgumentException("A folder needs at least two different apps")
        }
        val folders = folders()
        if (folders.any { it.folder.title.equals(title, ignoreCase = true) }) {
            throw AppFunctionInvalidArgumentException("A folder named \"$title\" already exists")
        }
        checkNotInOtherFolder(apps, folders, except = null)
        val service = FolderService.INSTANCE.get(applicationContext)
        val id = service.saveFolderInfo(title).toInt()
        service.updateFolderWithItems(id, title, apps.map { keyOf(it) })
        reloadDrawer()
        return FolderSummary(title, apps.map { it.label })
    }

    /**
     * Adds apps to a folder that already exists in the app drawer.
     *
     * @param folderName The name of the folder, as returned by listFolders. Case does not matter.
     * @param appNames The names of the apps to add. Apps already in the folder are kept once.
     * @return The folder with all its apps after the change.
     * @throws AppFunctionElementNotFoundException If the folder or an app does not exist.
     * @throws AppFunctionInvalidArgumentException If an app is already in another folder.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun addAppsToFolder(folderName: String, appNames: List<String>): FolderSummary {
        val folders = folders()
        val folder = folders.firstOrNull { it.folder.title.equals(folderName.trim(), ignoreCase = true) }
            ?: throw AppFunctionElementNotFoundException("No folder named \"$folderName\". Call listFolders.")
        val apps = resolveApps(appNames)
        checkNotInOtherFolder(apps, folders, except = folder.folder.id)
        val current = folder.items.sortedBy { it.rank }.mapNotNull { it.componentKey }
        val keys = (current + apps.map { keyOf(it) }).distinct()
        FolderService.INSTANCE.get(applicationContext)
            .updateFolderWithItems(folder.folder.id, folder.folder.title, keys)
        reloadDrawer()
        val labels = loadApps().associate { keyOf(it) to it.label }
        return FolderSummary(folder.folder.title, keys.map { labels[it] ?: it.substringBefore('/') })
    }

    /**
     * Deletes a folder of the app drawer. The apps stay installed and go back to the drawer list.
     *
     * @param folderName The name of the folder, as returned by listFolders. Case does not matter.
     * @throws AppFunctionElementNotFoundException If there is no such folder.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun deleteFolder(folderName: String) {
        val folder = folders().firstOrNull { it.folder.title.equals(folderName.trim(), ignoreCase = true) }
            ?: throw AppFunctionElementNotFoundException("No folder named \"$folderName\". Call listFolders.")
        FolderService.INSTANCE.get(applicationContext).deleteFolderInfo(folder.folder.id)
        reloadDrawer()
    }

    /**
     * Gives a short word of the user's own to a whole command of the command bar, usable typed or
     * spoken. For example alias "mae" for "ligar maria". An alias cannot reuse a built-in command.
     *
     * @param alias One word, letters and numbers only. Accents and case do not matter.
     * @param command The command it stands for, in command bar syntax, for example "ligar maria" or "abrir chrome".
     * @return The saved alias.
     * @throws AppFunctionInvalidArgumentException If the alias is not one valid free word or the command is blank.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun createAlias(alias: String, command: String): AliasSummary = withContext(Dispatchers.IO) {
        val existing = AliasStore.load(applicationContext)
        val error = Aliases.validate(alias, command, existing, CustomActionStore.load(applicationContext))
        if (error != null) throw AppFunctionInvalidArgumentException(error)
        val saved = CommandAlias(alias.trim().lowercase(), command.trim())
        AliasStore.save(applicationContext, existing + saved)
        AliasSummary(saved.name, saved.expansion)
    }

    /**
     * Removes an alias of the command bar.
     *
     * @param alias The alias word, as returned by listAliases.
     * @throws AppFunctionElementNotFoundException If there is no such alias.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun removeAlias(alias: String) = withContext(Dispatchers.IO) {
        val existing = AliasStore.load(applicationContext)
        val found = Aliases.find(alias, existing)
            ?: throw AppFunctionElementNotFoundException("No alias \"$alias\". Call listAliases.")
        AliasStore.save(applicationContext, existing - found)
    }

    /**
     * Changes the phosphor colour that tints the whole launcher.
     *
     * @param color One of VERDE (green), AMBAR (amber), CIANO (cyan) or BRANCO (white).
     * @throws AppFunctionInvalidArgumentException If the colour is not one of the four.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun setPhosphorColor(
        @AppFunctionStringValueConstraint(enumValues = ["VERDE", "AMBAR", "CIANO", "BRANCO"])
        color: String,
    ) {
        val argb = PHOSPHOR_COLORS[color.trim().uppercase()]
            ?: throw AppFunctionInvalidArgumentException("Unknown color \"$color\"")
        withContext(Dispatchers.Main) {
            PreferenceManager2.INSTANCE.get(applicationContext).accentColor.set(ColorOption.CustomColor(argb))
        }
    }

    /**
     * Shows or hides one kind of information in the home screen "At a Glance" panel.
     *
     * @param item AGENDA (calendar), CLIMA (weather), MIDIA (media playing), ALARME, LEMBRETE (reminders) or BATERIA (Bluetooth battery).
     * @param enabled True to show it, false to hide it.
     * @throws AppFunctionInvalidArgumentException If the item is not one of the six.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun setGlanceItemEnabled(
        @AppFunctionStringValueConstraint(enumValues = ["AGENDA", "CLIMA", "MIDIA", "ALARME", "LEMBRETE", "BATERIA"])
        item: String,
        enabled: Boolean,
    ) {
        val prefs = PreferenceManager2.INSTANCE.get(applicationContext)
        val kind = GlanceKind.values().firstOrNull { it.name == item.trim().uppercase() }
        val pref = kind?.let { glancePreference(prefs, it) }
            ?: throw AppFunctionInvalidArgumentException("Unknown item \"$item\"")
        withContext(Dispatchers.Main) { pref.set(enabled) }
    }

    /**
     * Hides an app from the app drawer. The app stays installed and showApp brings it back.
     *
     * @param appName The name of the app, as returned by searchApps.
     * @throws AppFunctionElementNotFoundException If no installed app matches.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun hideApp(appName: String) = setHidden(appName, hidden = true)

    /**
     * Shows again an app that hideApp hid from the app drawer.
     *
     * @param appName The name of the app.
     * @throws AppFunctionElementNotFoundException If no installed app matches.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun showApp(appName: String) = setHidden(appName, hidden = false)

    /** The switch that shows or hides a kind of panel line; null for kinds the user cannot turn off. */
    private fun glancePreference(prefs: PreferenceManager2, kind: GlanceKind) = when (kind) {
        GlanceKind.AGENDA -> prefs.glanceAgenda
        GlanceKind.CLIMA -> prefs.glanceWeather
        GlanceKind.MIDIA -> prefs.smartspaceNowPlaying
        GlanceKind.ALARME -> prefs.glanceAlarm
        GlanceKind.LEMBRETE -> prefs.glanceReminders
        GlanceKind.BATERIA -> prefs.glanceBluetooth
        else -> null
    }

    private suspend fun setHidden(appName: String, hidden: Boolean) {
        val app = resolveApp(appName)
        val prefs = PreferenceManager2.INSTANCE.get(applicationContext)
        val key = keyOf(app)
        withContext(Dispatchers.Main) {
            val current = prefs.hiddenApps.get().first()
            prefs.hiddenApps.set(if (hidden) current + key else current - key)
        }
    }

    private suspend fun folders() = withContext(Dispatchers.IO) {
        AppDatabase.INSTANCE.get(applicationContext).folderDao().getAllFoldersWithItems().first()
    }

    /** The key the launcher stores an app under: component and user. */
    private fun keyOf(app: AppEntry): String = ComponentKey(ComponentName.unflattenFromString(app.id)!!, Process.myUserHandle()).toString()

    /** The installed app a spoken or typed name stands for: exact name first, then the start, then a part. */
    private suspend fun resolveApp(name: String): AppEntry {
        val q = Aliases.key(name)
        if (q.isEmpty()) throw AppFunctionInvalidArgumentException("An app name cannot be blank")
        val apps = loadApps()
        return apps.firstOrNull { Aliases.key(it.label) == q }
            ?: apps.firstOrNull { Aliases.key(it.label).startsWith(q) }
            ?: apps.firstOrNull { Aliases.key(it.label).contains(q) }
            ?: throw AppFunctionElementNotFoundException("No app named \"$name\". Call searchApps.")
    }

    private suspend fun resolveApps(names: List<String>): List<AppEntry> = names.map { resolveApp(it) }.distinctBy { it.id }

    /** The drawer allows an app in one folder only, as the folder editor does by package. */
    private fun checkNotInOtherFolder(
        apps: List<AppEntry>,
        folders: List<app.lawnchair.data.folder.service.FolderWithItems>,
        except: Int?,
    ) {
        val taken = folders.filter { it.folder.id != except }
            .flatMap { f -> f.items.mapNotNull { it.componentKey?.substringBefore('/') }.map { it to f.folder.title } }
            .toMap()
        apps.forEach { app ->
            val owner = taken[app.id.substringBefore('/')]
            if (owner != null) {
                throw AppFunctionInvalidArgumentException("\"${app.label}\" is already in the folder \"$owner\"")
            }
        }
    }

    private suspend fun reloadDrawer() = withContext(Dispatchers.Main) {
        InvariantDeviceProfile.INSTANCE.get(applicationContext).onPreferencesChanged(applicationContext)
    }

    private suspend fun loadApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        CommandExecutor.loadApps(applicationContext)
    }

    private suspend fun runCommandText(text: String): CommandResult = withContext(Dispatchers.IO) {
        val context: Context = applicationContext
        val aliases = AliasStore.load(context)
        val custom = CustomActionStore.load(context)
        val granted = CommandExecutor.hasContactsPermission(context)
        val analysis = CommandEngine.analyze(
            VoiceCommand.normalize(text, aliases),
            CommandExecutor.loadApps(context),
            CommandExecutor.loadContacts(context),
            granted,
            custom,
            aliases,
        )
        when (val action = analysis.action) {
            null -> CommandResult("NOT_RUNNABLE", analysis.preview)

            // An unknown command falls back to a web search on the screen; an assistant should not trigger that.
            is CommandAction.WebSearch -> CommandResult("NOT_RUNNABLE", "Unknown command: ${action.query}")

            // Reaching another person needs the user's confirmation, so it is only described.
            is CommandAction.Call, is CommandAction.Custom ->
                CommandResult("NEEDS_CONFIRMATION", "${analysis.previewTitle}: ${analysis.preview}")

            is CommandAction.Calc -> CommandResult("RESULT", "${action.expression} = ${action.result}")

            else -> if (CommandExecutor.execute(context, action)) {
                CommandResult("EXECUTED", "${analysis.previewTitle}: ${analysis.preview}")
            } else {
                CommandResult("FAILED", "No app could handle this command")
            }
        }
    }

    private companion object {
        const val MAX_APPS = 25

        // The same four phosphor colours as the General settings screen.
        val PHOSPHOR_COLORS = mapOf(
            "VERDE" to 0xFF7DFFB2,
            "AMBAR" to 0xFFFFA63D,
            "CIANO" to 0xFF6FD8FF,
            "BRANCO" to 0xFFE8F0E0,
        )
        const val GLANCE_TIMEOUT_MS = 3_000L
    }
}
