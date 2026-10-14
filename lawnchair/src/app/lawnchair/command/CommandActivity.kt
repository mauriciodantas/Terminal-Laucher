package app.lawnchair.command

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchair.command.R as CommandR
import app.lawnchair.theme.LauncherGround
import app.lawnchair.theme.color.tokens.PhosphorColorToken
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.util.foldAccents
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How long "ia tema" waits at least, in seconds: drawing a wallpaper takes longer than the usual answer. */
private const val THEME_TIMEOUT = 120

/**
 * The terminal command bar: a command line with autocomplete, a list of suggestions, a preview of
 * what would happen and a history. All the rules live in [CommandEngine].
 */
class CommandActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            LawnchairTheme {
                CommandScreen(onClose = { finish() })
            }
        }
    }

    companion object {
        private const val STORE = "nostromo"
        private const val KEY_HISTORY = "command_history"

        private const val EXTRA_VOICE = "start_voice"
        private const val EXTRA_TEXT = "start_text"
        private const val EXTRA_RUN = "start_run"

        /** The scheme of links that open the bar with a command typed: terminal://cmd?text=... */
        const val LINK_SCHEME = "terminal"

        /**
         * [voice] opens the speech recognizer right away, for the microphone on the home screen.
         * [text] starts the command line with what was typed on a hardware keyboard at home.
         */
        fun start(context: Context, voice: Boolean = false, text: String = "") {
            CommandExecutor.prewarm(context)
            context.startActivity(intent(context, voice, text))
        }

        /** [run] runs [text] right away, for a scheduled command tapped in its notification. */
        fun intent(context: Context, voice: Boolean = false, text: String = "", run: Boolean = false): Intent = // A task of its own, outside the home task: the system cannot split the screen with
            // the home screen, and "dividir" opens an app beside the bar.
            Intent(context, CommandActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_VOICE, voice)
                .putExtra(EXTRA_TEXT, text)
                .putExtra(EXTRA_RUN, run)

        /** True only for the app's own intents: a link or another app cannot make the bar run a command unseen. */
        private fun isOwn(intent: Intent?): Boolean = intent?.component?.className == CommandActivity::class.java.name

        internal fun wantsRun(intent: Intent?): Boolean = isOwn(intent) && intent?.getBooleanExtra(EXTRA_RUN, false) == true

        /** Text selected in another app and sent to the bar from its menu. */
        internal fun selection(intent: Intent?): String? = if (intent?.action == Intent.ACTION_PROCESS_TEXT) {
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        } else {
            null
        }

        fun voiceIntent(context: Context): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.command_voice_prompt))

        fun voiceAvailable(context: Context): Boolean = context.packageManager.resolveActivity(voiceIntent(context), 0) != null

        internal fun wantsVoice(intent: Intent?): Boolean = intent?.getBooleanExtra(EXTRA_VOICE, false) == true

        /**
         * What the command line starts with: the key typed at home, the command of a link, or for
         * selected text a " {sel}" with the cursor before it, so "rota" typed makes "rota {sel}".
         */
        internal fun initialText(intent: Intent?): String = when {
            intent == null -> ""

            intent.action == Intent.ACTION_PROCESS_TEXT -> " {sel}"

            intent.action == Intent.ACTION_VIEW && intent.data?.scheme == LINK_SCHEME ->
                intent.data?.let { it.getQueryParameter("text") ?: it.getQueryParameter("cmd") }.orEmpty()

            else -> intent.getStringExtra(EXTRA_TEXT).orEmpty()
        }

        fun loadHistory(context: Context): List<String> = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, "")
            .orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }

        fun saveHistory(context: Context, history: List<String>) {
            context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
                .putString(KEY_HISTORY, history.joinToString("\n")).apply()
        }

        /** Forgets the typed history and the per-hour counts, which also feed the recent chips. */
        fun clearHistory(context: Context) {
            context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().remove(KEY_HISTORY).apply()
            StatsStore.clear(context)
        }
    }
}

private val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

/** The font of the command bar: the one the user picked under Appearance, carried by the theme. */
@Composable
private fun mono(): FontFamily = MaterialTheme.typography.bodyMedium.fontFamily ?: PlexMono

private val Danger = Color(0xFFFF5A45)
private val Amber = Color(0xFFF2B84B)

/** Colors the command word fully and the argument softer, so where one ends and the other starts is clear. */
private class CommandHighlight(private val command: Color, private val argument: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val start = raw.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) raw.length else it }
        val end = raw.indexOfFirst(start) { it.isWhitespace() }.let { if (it < 0) raw.length else it }
        val styled = buildAnnotatedString {
            append(raw)
            addStyle(SpanStyle(color = command), start, end)
            addStyle(SpanStyle(color = argument), end, raw.length)
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    private fun String.indexOfFirst(from: Int, predicate: (Char) -> Boolean): Int {
        for (i in from until length) if (predicate(this[i])) return i
        return -1
    }
}

/** Text over a solid phosphor fill: the ground, unless the accent is dark enough to need a light one. */
@Composable
private fun onPhosphor(): Color {
    val context = LocalContext.current
    return remember { Color(LauncherGround.onAccent(context, PhosphorColorToken(1f).resolveColor(context))) }
}

@Composable
private fun CommandScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val phosphor = remember { Color(PhosphorColorToken(1f).resolveColor(context)) }
    val dim = remember { Color(PhosphorColorToken(0.62f).resolveColor(context)) }
    val line = remember { Color(PhosphorColorToken(0.4f).resolveColor(context)) }

    // Heavy lookups (app labels, contacts) run off the main thread; the screen opens empty and fills in.
    var apps by remember { mutableStateOf(CommandExecutor.cachedApps ?: emptyList()) }
    var contacts by remember { mutableStateOf<List<ContactEntry>>(emptyList()) }
    var granted by remember { mutableStateOf(CommandExecutor.hasContactsPermission(context)) }
    var history by remember { mutableStateOf<List<String>>(emptyList()) }
    var custom by remember { mutableStateOf(CustomActions.DEFAULTS) }
    var aliases by remember { mutableStateOf<List<CommandAlias>>(emptyList()) }
    val startIntent = (context as? android.app.Activity)?.intent
    val selection = remember { CommandActivity.selection(startIntent) }
    // History, calendar, notes, jobs, device: what the newer commands complete from and show.
    var extras by remember { mutableStateOf(CommandExtras(selection = selection)) }
    var usual by remember { mutableStateOf<List<String>>(emptyList()) }

    suspend fun reloadExtras() {
        val clip = extras.clipboard
        extras = withContext(Dispatchers.IO) { CommandSources.load(context, history, selection) }.copy(clipboard = clip)
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            history = CommandActivity.loadHistory(context)
            custom = CustomActionStore.load(context)
            aliases = AliasStore.load(context)
            usual = CommandStats.rank(StatsStore.load(context), java.time.LocalTime.now().hour)
            contacts = CommandExecutor.loadContacts(context)
            apps = CommandExecutor.loadApps(context)
        }
        reloadExtras()
        RatesStore.refresh(context)?.let { rates -> extras = extras.copy(rates = rates) }
    }
    // Back from the settings, what changed there counts: a key saved, a command added or removed.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    val (actions, names) = withContext(Dispatchers.IO) { CustomActionStore.load(context) to AliasStore.load(context) }
                    custom = actions
                    aliases = names
                    extras = withContext(Dispatchers.IO) { with(CommandSources) { extras.withProviders(context) } }
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = CommandExecutor.hasContactsPermission(context)
        scope.launch { contacts = withContext(Dispatchers.IO) { CommandExecutor.loadContacts(context) } }
    }
    // Calendar and location for "agenda" and {location}: granted, the extras load again.
    val needPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { reloadExtras() }
    }

    // The conversation with the AI while the bar is open, and the note of a command it filled in.
    var aiTurns by remember { mutableStateOf<List<AiTurn>>(emptyList()) }
    var aiBusy by remember { mutableStateOf(false) }

    // The answer of the conversation while it is still coming in.
    var aiPartial by remember { mutableStateOf<String?>(null) }

    // The folders "ia pastas" proposed, waiting for APPLY or CANCEL.
    var folderPlan by remember { mutableStateOf<FolderPlan?>(null) }
    var aiNote by remember { mutableStateOf<Pair<String, String>?>(null) }

    // The speed test running in the bar, so a second Enter does not start another.
    var speedJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // The code "qr" drew, shown under the preview until something else is typed.
    var qr by remember { mutableStateOf<QrMatrix?>(null) }

    // The line a first Enter armed when the command asks twice; any change disarms it.
    var armed by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<CommandAction?>(null) }
    var pendingCommand by remember { mutableStateOf("") }

    var field by remember {
        val intent = (context as? android.app.Activity)?.intent
        val typed = CommandActivity.initialText(intent)
        // Selected text waits as {sel} after the cursor: the command is typed before it.
        val cursor = if (CommandActivity.selection(intent) != null) 0 else typed.length
        mutableStateOf(TextFieldValue(typed, TextRange(cursor)))
    }
    var selected by remember { mutableIntStateOf(0) }
    var historyIndex by remember { mutableIntStateOf(-1) }
    var done by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    val text = field.text
    val analysis = remember(text, apps, contacts, granted, custom, aliases, extras, history) {
        CommandEngine.analyze(text, apps, contacts, granted, custom, aliases, extras.copy(history = history))
    }
    // The clipboard is read only when the line asks for it ("colar", {clip}): each read shows the
    // system's "pasted" notice, and Android lets only the app in front read it, once it has focus.
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val resolved = Aliases.resolve(text, aliases)
    val wantsClip = Variables.uses(resolved, Variables.Var.CLIP) ||
        BuiltInCommand.of(resolved.trim().substringBefore(' ').foldAccents()) == BuiltInCommand.PASTE
    LaunchedEffect(wantsClip, windowInfo.isWindowFocused) {
        if (wantsClip && windowInfo.isWindowFocused && extras.clipboard == null) {
            extras = extras.copy(clipboard = CommandSources.clipboard(context).orEmpty())
        }
    }
    val sel = selected.coerceIn(0, maxOf(0, analysis.suggestions.size - 1))
    val top = analysis.suggestions.getOrNull(sel)
    val ghost = if (done == null) CommandEngine.ghost(text, top) else ""

    fun setText(value: String, cursor: Int = value.length) {
        speedJob?.cancel()
        field = TextFieldValue(value, TextRange(cursor))
        selected = 0
        done = null
        qr = null
    }

    val executedLabel = stringResource(R.string.command_executed)
    val failedLabel = stringResource(R.string.command_failed)

    fun record(action: CommandAction, command: String) {
        // "!!" is not kept: the command it repeated stays the last one.
        if (command.trim() == BuiltInCommand.REPEAT) return
        history = CommandEngine.pushHistory(history, command)
        CommandActivity.saveHistory(context, history)
        CommandUsage.record(context, CommandUsage.keysFor(action, command, aliases))
        StatsStore.record(context, command.trim())
    }

    /**
     * Sends [action] to the AI. Only the request, the commands of the bar, the user's letters and
     * alias names, the app names and the drawer folder names go out: no contacts, notes, calendar,
     * clipboard or location.
     */
    fun askAi(action: CommandAction.Ai, command: String) {
        val config = AiStore.load(context) ?: return
        if (aiBusy) return
        record(action, command)
        // "ia pastas" alone still needs a request in the user's language, which the folder names follow.
        val prompt = action.prompt.ifEmpty {
            when (action.mode) {
                AiMode.FOLDERS -> context.getString(R.string.cmd_ai_folders_request)
                AiMode.THEME -> context.getString(R.string.cmd_ai_theme_request)
                else -> ""
            }
        }
        // A conversation goes on across questions; a command request starts fresh each time.
        val turns = (if (action.mode == AiMode.CHAT) aiTurns else emptyList()) + AiTurn(fromUser = true, text = prompt)
        if (action.mode == AiMode.CHAT) aiTurns = turns
        folderPlan = null
        aiBusy = true
        done = context.getString(R.string.command_running)
        scope.launch {
            val folders = if (action.mode == AiMode.FOLDERS) runCatching { FolderOrganizer.existing(context) }.getOrDefault(emptyList()) else emptyList()
            val now = java.time.ZonedDateTime.now()
            val aiContext = AiContext(
                custom = custom,
                aliases = aliases,
                apps = apps.map { it.label },
                now = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm, EEEE", java.util.Locale.getDefault())) + " (" + now.zone + ")",
                folders = FolderOrganizer.describe(folders, apps),
                forceFolders = action.force,
                theme = if (action.mode == AiMode.THEME) extras.theme else null,
            )
            val system = AiPrompts.system(action.mode, aiContext)
            // Only the conversation is shown as it comes: a command or a pack is read once complete.
            val result = runCatching {
                when (action.mode) {
                    AiMode.CHAT -> AiClient.stream(config, system, turns) { aiPartial = it }

                    // A theme may carry a drawing: a long reply that a thinking model starts late. It comes in pieces,
                    // the time limit counting between them, and never under THEME_TIMEOUT whatever the setting.
                    AiMode.THEME, AiMode.CREATE -> AiClient.stream(config, system, turns, minTimeoutSeconds = THEME_TIMEOUT) {}

                    else -> AiClient.send(config, system, turns)
                }
            }
            aiBusy = false
            aiPartial = null
            val reply = result.getOrElse { error ->
                if (action.mode == AiMode.CHAT) aiTurns = turns.dropLast(1)
                done = AiClient.describe(context, error)
                return@launch
            }
            when (action.mode) {
                AiMode.RUN -> {
                    val proposal = AiPrompts.command(reply.text)
                    val line = proposal.command
                    if (line == null) {
                        done = proposal.note.ifEmpty { context.getString(R.string.cmd_ai_no_command) }
                    } else {
                        // The command waits on the line with its preview; Enter runs it.
                        setText(line)
                        aiNote = line to proposal.note
                    }
                }

                AiMode.CREATE -> {
                    val pack = AiPrompts.pack(reply.text)
                    // Asked for a theme in other words ("ia create theme for halloween"), the AI sends one: it is reviewed as a theme.
                    val theme = AiPrompts.createdTheme(reply.text)
                    if (theme != null) {
                        if (ThemeStore.review(context, ThemeImages.embed(context, theme.first))) onClose() else done = failedLabel
                    } else if (pack == null) {
                        done = context.getString(R.string.cmd_ai_create_failed)
                    } else {
                        // The same import screen as a pack received from somebody: nothing is added unseen.
                        val opened = runCatching {
                            val dir = java.io.File(context.cacheDir, "commands").apply { mkdirs() }
                            val file = java.io.File(dir, "ia." + CommandPacks.EXTENSION).apply { writeText(pack.first) }
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            context.startActivity(
                                Intent(context, ImportCommandsActivity::class.java)
                                    .setAction(Intent.ACTION_VIEW)
                                    .setData(uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            )
                        }.isSuccess
                        if (opened) onClose() else done = failedLabel
                    }
                }

                AiMode.THEME -> {
                    val theme = AiPrompts.theme(reply.text)
                    when {
                        theme == null -> done = context.getString(R.string.cmd_ai_theme_failed)

                        // The same screen as a theme received from somebody: nothing changes unseen.
                        ThemeStore.review(context, ThemeImages.embed(context, theme.first)) -> onClose()

                        else -> done = failedLabel
                    }
                }

                AiMode.FOLDERS -> {
                    val plan = AiFolders.plan(reply.text, apps, folders, action.force)
                    when {
                        plan == null -> done = context.getString(R.string.cmd_ai_folders_failed)

                        plan.isEmpty -> done = plan.note.ifEmpty { context.getString(R.string.cmd_ai_folders_none) }

                        // The plan waits on screen: nothing changes until APPLY.
                        else -> {
                            folderPlan = plan
                            done = null
                            setText("")
                        }
                    }
                }

                else -> {
                    aiTurns = turns + reply.turn
                    // Ready for the next question.
                    setText(BuiltInCommand.AI.word + " ")
                }
            }
        }
    }

    fun commit(action: CommandAction, command: String) {
        when (action) {
            is CommandAction.Ai -> if (action.mode != AiMode.SETUP) {
                askAi(action, command)
                return
            }

            // Puts the text on the line instead of running anything.
            is CommandAction.FillLine -> {
                setText(action.text)
                return
            }

            // Measured in the bar, the figures updating as they come; closing the bar stops it.
            is CommandAction.SpeedTest -> {
                if (speedJob?.isActive == true) return
                record(action, command)
                speedJob = scope.launch {
                    val result = runCatching { SpeedTestRunner.run { r -> done = Speed.text(r) } }
                    result.exceptionOrNull()?.takeIf { it !is kotlinx.coroutines.CancellationException }?.let {
                        done = context.getString(R.string.cmd_fast_failed, it.message ?: it.javaClass.simpleName)
                    }
                }
                return
            }

            // Drawn under the preview; the bar stays open for the other phone to read it.
            is CommandAction.ShowQr -> {
                record(action, command)
                val code = QrCode.encode(action.content)
                qr = code
                done = context.getString(if (code != null) CommandR.string.cmd_qr_shown else CommandR.string.cmd_qr_failed)
                return
            }

            // Fetched here and shown in the preview, like the ping output.
            is CommandAction.Weather -> {
                record(action, command)
                done = context.getString(R.string.command_running)
                scope.launch { done = withContext(Dispatchers.IO) { Weather.run(action) } }
                return
            }

            is CommandAction.Custom -> if (action.action.kind == ActionKind.API) {
                record(action, command)
                done = context.getString(R.string.command_running)
                val providers = extras.providers
                scope.launch { done = withContext(Dispatchers.IO) { ApiCall.run(action.action, action.text, providers, SecretStore.lookup(context)) } }
                return
            }

            // The new commands work right away: the list the bar completes from is read again.
            is CommandAction.InstallPack -> {
                val pack = BundledPacks.of(action.id)
                val skipped = CommandExecutor.installPack(context, action.id)
                if (pack == null || skipped == null) {
                    done = failedLabel
                    return
                }
                record(action, command)
                custom = CustomActionStore.load(context)
                // A pack brings its providers too: the commands that use them work right away.
                extras = with(CommandSources) { extras.withProviders(context) }
                val letters = custom.filter { it.pack == pack.id }.joinToString(" · ") { it.letter }
                done = if (skipped == 0) {
                    context.getString(CommandR.string.cmd_pack_done, pack.name.uppercase(), letters)
                } else {
                    context.getString(CommandR.string.cmd_pack_done_skipped, pack.name.uppercase(), letters, skipped)
                }
                return
            }

            is CommandAction.RemovePack -> {
                if (!CommandExecutor.execute(context, action)) {
                    done = failedLabel
                    return
                }
                record(action, command)
                custom = CustomActionStore.load(context)
                // A pack brings its providers too: the commands that use them work right away.
                extras = with(CommandSources) { extras.withProviders(context) }
                done = context.getString(CommandR.string.cmd_pack_removed, BundledPacks.of(action.id)?.name?.uppercase() ?: action.id)
                return
            }

            // The output shows in the preview; the bar stays open to read it.
            is CommandAction.Ping -> {
                record(action, command)
                done = context.getString(R.string.command_running)
                scope.launch { done = withContext(Dispatchers.IO) { CommandSources.ping(action.host) } }
                return
            }

            else -> {}
        }
        val ok = CommandExecutor.execute(context, action)
        if (!ok) {
            done = failedLabel
            return
        }
        record(action, command)
        historyIndex = -1
        if (action is CommandAction.Schedule || action is CommandAction.CancelJob || action is CommandAction.AddNote) {
            scope.launch { reloadExtras() }
        }
        if (action is CommandAction.Calc) {
            done = context.getString(R.string.command_copied, action.result)
        } else if (action is CommandAction.Copy) {
            done = context.getString(R.string.command_copied, action.text.lineSequence().first())
        } else if (action is CommandAction.Schedule) {
            val problem = CommandScheduler.notificationProblem(context, action.job)
            val exactAlarms = CommandScheduler.exactAlarmRequest(context, action.job)
            when {
                // The bar stays to say it: the job is saved but would come and go unseen.
                problem != null -> done = context.getString(problem)

                exactAlarms != null -> {
                    done = context.getString(R.string.cmd_exact_alarm_hint)
                    runCatching { context.startActivity(exactAlarms.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    onClose()
                }

                else -> {
                    done = executedLabel
                    onClose()
                }
            }
        } else if (action is CommandAction.SplitScreen) {
            // The bar stays a moment to open the first app from inside the split; it closes itself.
            done = executedLabel
        } else {
            done = executedLabel
            onClose()
        }
    }

    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Allowed: the call starts. Denied: the dialer opens with the number instead.
        pendingAction?.let { commit(it, pendingCommand) }
        pendingAction = null
    }

    // A reminder or a scheduled command needs notifications to show; asked once, then it runs either way.
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingAction?.let { commit(it, pendingCommand) }
        pendingAction = null
    }

    fun runAction(action: CommandAction, command: String) {
        if (action is CommandAction.Call && !CommandExecutor.hasCallPermission(context)) {
            pendingAction = action
            pendingCommand = command
            callPermission.launch(android.Manifest.permission.CALL_PHONE)
            return
        }
        if (action is CommandAction.Schedule && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            !CommandSources.granted(context, android.Manifest.permission.POST_NOTIFICATIONS)
        ) {
            pendingAction = action
            pendingCommand = command
            notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        commit(action, command)
    }

    /** A suggestion that is already a complete, runnable command runs on the tap, without Enter. */
    fun pick(suggestion: Suggestion) {
        setText(suggestion.completion)
        CommandEngine.analyze(suggestion.completion, apps, contacts, granted, custom, aliases, extras.copy(history = history)).action
            ?.let { runAction(it, suggestion.completion.trim()) }
    }

    /** Enter runs the command; among options, which have none of their own, it picks the highlighted one. */
    fun run() {
        val action = analysis.action
        // A DELETE is armed by the first Enter and sent by the second, on the same line.
        if (action != null && analysis.confirmFirst && armed != text) {
            armed = text
            val spec = (action as? CommandAction.Custom)?.action
            done = context.getString(
                CommandR.string.cmd_api_confirm_armed,
                spec?.api?.method.orEmpty(),
                spec?.let { ApiCall.host(it, extras.providers) }.orEmpty(),
            )
            return
        }
        armed = null
        if (action != null) {
            runAction(action, text)
        } else if (analysis.picksOnEnter) {
            analysis.suggestions.getOrNull(sel)?.let { pick(it) }
        }
    }

    val voiceIntent = remember { CommandActivity.voiceIntent(context) }
    val voiceAvailable = remember { CommandActivity.voiceAvailable(context) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == android.app.Activity.RESULT_OK && !spoken.isNullOrBlank()) {
            val value = VoiceCommand.normalize(spoken, aliases)
            field = TextFieldValue(value, TextRange(value.length))
            selected = 0
            done = null
            // A spoken command that is already complete and runnable runs on its own.
            val action = CommandEngine.analyze(value, apps, contacts, granted, custom, aliases, extras.copy(history = history)).action
            if (action != null) {
                runAction(action, value.trim())
                return@rememberLauncherForActivityResult
            }
        }
        runCatching { focus.requestFocus() }
    }

    fun complete() {
        top?.let { setText(it.completion) }
    }

    fun clearHistory() {
        history = emptyList()
        usual = emptyList()
        historyIndex = -1
        CommandActivity.clearHistory(context)
    }

    fun older() {
        val index = CommandEngine.olderIndex(historyIndex, history.size)
        if (index >= 0) {
            historyIndex = index
            setText(history[index])
            historyIndex = index
        }
    }

    fun next() {
        selected = CommandEngine.nextSuggestion(sel, analysis.suggestions.size)
    }

    val startWithVoice = remember {
        voiceAvailable && CommandActivity.wantsVoice((context as? android.app.Activity)?.intent)
    }
    LaunchedEffect(Unit) {
        if (startWithVoice) runCatching { voice.launch(voiceIntent) } else focus.requestFocus()
    }
    // A scheduled command tapped in its notification runs once everything it needs has loaded.
    val runAtStart = remember { CommandActivity.wantsRun(startIntent) && text.isNotBlank() }
    var ranAtStart by remember { mutableStateOf(false) }
    LaunchedEffect(analysis.action, apps) {
        if (runAtStart && !ranAtStart && apps.isNotEmpty()) {
            analysis.action?.let {
                ranAtStart = true
                runAction(it, text.trim())
            }
        }
    }

    val ground = remember { Color(LauncherGround.get(context)) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(ground)
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        // A tablet in landscape with the keyboard up has little height left: the header gives way
        // so the suggestions keep the room.
        val compact = maxHeight < 480.dp
        // A wide screen puts the preview beside the suggestions instead of under them.
        val wide = maxWidth >= 900.dp
        // On a tablet the terminal keeps a readable width instead of stretching across the screen.
        Column(
            Modifier
                .widthIn(max = if (wide) 1200.dp else 840.dp)
                .fillMaxSize()
                .padding(8.dp)
                .border(1.dp, line)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (!compact) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Label("MU/TH/UR 6000", dim, 10.sp)
                    Label(stringResource(R.string.command_header_right), dim, 10.sp)
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, line)
                        .padding(vertical = 6.dp),
                ) {
                    Text(
                        stringResource(R.string.command_title),
                        color = phosphor,
                        fontFamily = mono(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        letterSpacing = 0.2.sp,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Label(stringResource(R.string.command_subtitle), dim, 10.5.sp)
            }

            // The command line.
            val commandLine: @Composable () -> Unit = {
                val style = TextStyle(
                    fontFamily = mono(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    letterSpacing = 0.9.sp,
                )
                val inputLabel = stringResource(R.string.command_input_label)
                val highlight = remember(phosphor, dim) { CommandHighlight(phosphor, phosphor.copy(alpha = 0.72f)) }
                Row(
                    Modifier
                        .padding(top = if (compact) 0.dp else 12.dp)
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(1.dp, phosphor)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(">", color = phosphor, style = style)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = Color.Transparent)) { append(text) }
                                withStyle(SpanStyle(color = phosphor.copy(alpha = 0.4f))) { append(ghost) }
                            },
                            style = style,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                        BasicTextField(
                            value = field,
                            onValueChange = {
                                if (it.text != field.text) {
                                    selected = 0
                                    done = null
                                    qr = null
                                    // Typing something else stops a speed test, which would keep writing over the preview.
                                    speedJob?.cancel()
                                }
                                field = it
                            },
                            textStyle = style.copy(color = phosphor),
                            visualTransformation = highlight,
                            cursorBrush = SolidColor(phosphor),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Go,
                            ),
                            keyboardActions = KeyboardActions(onGo = { run() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focus)
                                .semantics { contentDescription = inputLabel }
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (event.key) {
                                        Key.Tab -> {
                                            complete()
                                            true
                                        }

                                        Key.DirectionUp -> {
                                            older()
                                            true
                                        }

                                        Key.DirectionDown -> {
                                            next()
                                            true
                                        }

                                        Key.Escape -> {
                                            onClose()
                                            true
                                        }

                                        // A hardware Enter runs the command, like the keyboard's Go key.
                                        Key.Enter, Key.NumPadEnter -> {
                                            run()
                                            true
                                        }

                                        else -> false
                                    }
                                },
                        )
                    }
                    if (voiceAvailable) {
                        Spacer(Modifier.width(6.dp))
                        val voiceLabel = stringResource(R.string.command_voice)
                        Box(
                            Modifier
                                .size(44.dp)
                                .clickable { runCatching { voice.launch(voiceIntent) } }
                                .semantics { contentDescription = voiceLabel },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("[MIC]", color = phosphor, fontFamily = mono(), fontSize = 10.sp, letterSpacing = 0.5.sp)
                        }
                    }
                }
            }

            // One-tap starts while nothing is typed: recent commands, aliases and letters.
            val quickStarts: @Composable () -> Unit = {
                if (text.isEmpty() && done == null) {
                    val chips = remember(history, aliases, custom, usual) { CommandEngine.quickChips(history, aliases, custom, usual) }
                    if (chips.isNotEmpty()) {
                        Row(Modifier.padding(top = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Label(stringResource(R.string.command_chips_title), dim, 10.sp, Modifier.weight(1f))
                            if (history.isNotEmpty() || usual.isNotEmpty()) {
                                Box(
                                    Modifier
                                        .heightIn(min = 32.dp)
                                        .clickable { clearHistory() }
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Label(stringResource(R.string.command_history_clear), dim, 10.sp)
                                }
                            }
                        }
                        Row(
                            Modifier
                                .padding(top = 6.dp)
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            val recentKind = stringResource(CommandR.string.cmd_kind_recent)
                            chips.forEach { chip ->
                                Row(
                                    Modifier
                                        .heightIn(min = 40.dp)
                                        .border(1.dp, line)
                                        .clickable { setText(chip.fill) }
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        if (chip.kind == recentKind) "↺" else "★",
                                        color = dim,
                                        fontFamily = mono(),
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(end = 6.dp),
                                    )
                                    Text(chip.label, color = phosphor, fontFamily = mono(), fontWeight = FontWeight.Medium, fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Suggestions.
            // The list takes the room left on the screen and scrolls past it.
            val suggestions: @Composable (Modifier) -> Unit = { modifier ->
                Column(
                    modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .heightIn(min = 100.dp)
                        .border(1.dp, line),
                ) {
                    Label(
                        analysis.listTitle,
                        dim,
                        10.sp,
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                    val listScroll = rememberScrollState()
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .scrollHint(listScroll, phosphor.copy(alpha = 0.6f), ground)
                            .verticalScroll(listScroll),
                    ) {
                        if (analysis.needsContacts) {
                            Row(
                                Modifier
                                    .padding(6.dp)
                                    .fillMaxWidth()
                                    .border(1.dp, Danger)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.command_contacts_needed),
                                    color = Danger,
                                    fontFamily = mono(),
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    Modifier
                                        .width(92.dp)
                                        .height(44.dp)
                                        .background(Danger)
                                        .clickable { permission.launch(android.Manifest.permission.READ_CONTACTS) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        stringResource(R.string.command_grant),
                                        color = Color(0xFF1A0502),
                                        fontFamily = mono(),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                        analysis.need?.let { need ->
                            Row(
                                Modifier
                                    .padding(6.dp)
                                    .fillMaxWidth()
                                    .border(1.dp, Amber)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(
                                        when (need) {
                                            Need.CALENDAR -> R.string.command_calendar_needed
                                            Need.LOCATION -> R.string.command_location_needed
                                            Need.SECRET -> R.string.command_secret_needed
                                        },
                                    ),
                                    color = Amber,
                                    fontFamily = mono(),
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    Modifier
                                        .width(92.dp)
                                        .height(44.dp)
                                        .background(Amber)
                                        .clickable {
                                            if (need == Need.SECRET) {
                                                // The key is typed in the advanced settings, never in the bar.
                                                context.startActivity(
                                                    app.lawnchair.ui.preferences.PreferenceActivity.createIntent(
                                                        context,
                                                        app.lawnchair.ui.preferences.navigation.CommandProviders(analysis.needProvider.orEmpty()),
                                                    ),
                                                )
                                            } else {
                                                needPermission.launch(
                                                    if (need == Need.CALENDAR) android.Manifest.permission.READ_CALENDAR else android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                                )
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        stringResource(if (need == Need.SECRET) R.string.command_configure else R.string.command_grant),
                                        color = Color(0xFF1A1202),
                                        fontFamily = mono(),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                        analysis.suggestions.forEachIndexed { index, suggestion ->
                            val active = index == sel
                            // [▼] and the arrow keys can move past what the list shows; it follows them.
                            val requester = remember { BringIntoViewRequester() }
                            if (active) LaunchedEffect(sel, analysis.suggestions) { requester.bringIntoView() }
                            Row(
                                Modifier
                                    .bringIntoViewRequester(requester)
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .background(if (active) phosphor else Color.Transparent)
                                    .clickable { pick(suggestion) }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val color = if (active) onPhosphor() else phosphor
                                Text(if (active) ">" else "", color = color, fontFamily = mono(), fontSize = 12.5.sp, modifier = Modifier.width(14.dp))
                                Text(
                                    suggestion.label,
                                    color = color,
                                    fontFamily = mono(),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(suggestion.kind, color = color.copy(alpha = 0.75f), fontFamily = mono(), fontSize = 9.5.sp)
                            }
                        }
                    }
                    if (analysis.total > analysis.suggestions.size) {
                        Label(
                            stringResource(R.string.command_more, analysis.suggestions.size, analysis.total),
                            dim,
                            9.5.sp,
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            // The conversation with the AI takes the place of the suggestions while "ia" is typed.
            val aiLine = BuiltInCommand.of(resolved.trim().substringBefore(' ').foldAccents()) == BuiltInCommand.AI
            val showConversation = aiTurns.isNotEmpty() && (aiLine || text.isBlank())
            val conversation: @Composable (Modifier) -> Unit = { modifier ->
                Column(
                    modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .heightIn(min = 100.dp)
                        .border(1.dp, line),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Label(
                            stringResource(CommandR.string.cmd_ai_title) + " · " + extras.ai?.substringBefore(" · ")?.uppercase().orEmpty(),
                            dim,
                            10.sp,
                            Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 5.dp),
                        )
                        Box(
                            Modifier
                                .heightIn(min = 32.dp)
                                .clickable(enabled = !aiBusy) { aiTurns = emptyList() }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Label(stringResource(R.string.cmd_ai_clear), dim, 10.sp)
                        }
                    }
                    val chatScroll = rememberScrollState()
                    // The newest message comes into view as it arrives.
                    LaunchedEffect(aiTurns.size) { chatScroll.animateScrollTo(chatScroll.maxValue) }
                    // An answer still coming in stays in view without an animation for each piece.
                    LaunchedEffect(aiPartial?.length) { chatScroll.scrollTo(chatScroll.maxValue) }
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .scrollHint(chatScroll, phosphor.copy(alpha = 0.6f), ground)
                            .verticalScroll(chatScroll)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        aiTurns.forEach { turn ->
                            Text(
                                if (turn.fromUser) "> " + turn.text else turn.text,
                                color = if (turn.fromUser) dim else phosphor,
                                fontFamily = mono(),
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                // A tap on an answer copies it.
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !turn.fromUser) {
                                    CommandExecutor.execute(context, CommandAction.Copy(turn.text))
                                    done = context.getString(R.string.command_copied, turn.text.lineSequence().first())
                                },
                            )
                        }
                        aiPartial?.let { partial ->
                            Text(
                                partial.trimStart() + "▌",
                                color = phosphor,
                                fontFamily = mono(),
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // The folders "ia pastas" proposed: nothing changes in the drawer until APPLY.
            val folderBox: @Composable (Modifier, FolderPlan) -> Unit = { modifier, plan ->
                Column(
                    modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .heightIn(min = 100.dp)
                        .border(1.dp, line),
                ) {
                    Label(
                        stringResource(R.string.cmd_ai_folders_title, plan.folders.size, plan.appCount),
                        dim,
                        10.sp,
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                    val planScroll = rememberScrollState()
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .scrollHint(planScroll, phosphor.copy(alpha = 0.6f), ground)
                            .verticalScroll(planScroll)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (plan.note.isNotEmpty()) {
                            Text(plan.note, color = dim, fontFamily = mono(), fontSize = 11.sp, lineHeight = 16.sp)
                        }
                        plan.folders.forEach { folder ->
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = phosphor)) {
                                        // "+" adds to a folder that exists, "=" rewrites it (--force).
                                        val mark = when {
                                            folder.existingId == null -> ""
                                            folder.replace -> "= "
                                            else -> "+ "
                                        }
                                        append(mark + folder.name.uppercase())
                                    }
                                    withStyle(SpanStyle(color = dim)) { append("  " + folder.apps.joinToString(", ") { it.label }) }
                                },
                                fontFamily = mono(),
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        // --force: the folders that lose apps, with what stays, and the ones deleted.
                        val labels = apps.associate { it.id.substringBefore('/') to it.label }
                        plan.trimmed.forEach { folder ->
                            Text(
                                "~ " + folder.title.uppercase() + "  " + folder.packages.mapNotNull { labels[it] }.joinToString(", "),
                                color = dim,
                                fontFamily = mono(),
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        plan.removed.forEach { folder ->
                            Text(
                                "× " + folder.title.uppercase() + "  " + stringResource(R.string.cmd_ai_folders_deleted),
                                color = Danger,
                                fontFamily = mono(),
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth().border(1.dp, line)) {
                        FKey(stringResource(R.string.cmd_ai_folders_cancel), dim, Modifier.weight(1f)) {
                            folderPlan = null
                        }
                        FKey(stringResource(R.string.cmd_ai_folders_apply), phosphor, Modifier.weight(1f)) {
                            folderPlan = null
                            done = context.getString(R.string.command_running)
                            scope.launch {
                                val changed = runCatching { FolderOrganizer.apply(context, plan) }
                                done = changed.fold(
                                    { context.getString(R.string.cmd_ai_folders_done, it) },
                                    { failedLabel },
                                )
                            }
                        }
                    }
                }
            }
            val listArea: @Composable (Modifier) -> Unit = { modifier ->
                val plan = folderPlan
                when {
                    plan != null -> folderBox(modifier, plan)
                    showConversation -> conversation(modifier)
                    else -> suggestions(modifier)
                }
            }

            // Preview.
            val preview: @Composable () -> Unit = {
                // Armed and waiting for the second Enter, nothing ran yet: the title stays the command's.
                val previewTitle = if (done != null && armed != text) stringResource(R.string.command_executed) else analysis.previewTitle
                val previewText = done ?: analysis.preview
                val previewColor = when {
                    done != null -> phosphor
                    analysis.tone == Tone.OK -> phosphor
                    analysis.tone == Tone.IDLE -> dim
                    else -> Danger
                }
                Column(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .border(1.dp, line)
                        .clickable(enabled = analysis.action != null) { run() }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Label(previewTitle, dim, 10.sp)
                    Text(
                        previewText,
                        color = previewColor,
                        fontFamily = mono(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        letterSpacing = 0.9.sp,
                        // A long answer (a list of 40 moves) scrolls instead of pushing the keys away.
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                    val code = qr
                    if (done != null && code != null) {
                        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                            QrImage(code)
                        }
                    }
                    val note = aiNote?.takeIf { it.first == text.trim() && it.second.isNotEmpty() }?.second
                    if (done == null && note != null) {
                        Text(
                            stringResource(CommandR.string.cmd_ai_title) + " › " + note,
                            color = dim,
                            fontFamily = mono(),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                    if (done == null && analysis.intent.isNotEmpty()) {
                        Text(
                            "⇢ " + analysis.intent,
                            color = Amber,
                            fontFamily = mono(),
                            fontSize = 10.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
                // Help: what the command being typed does, with examples.
                val help = analysis.help
                if (done == null && help != null) {
                    Column(
                        Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .border(1.dp, line)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Label(stringResource(CommandR.string.cmd_help) + " · " + help.title, dim, 10.sp)
                        Text(
                            help.text,
                            color = dim,
                            fontFamily = mono(),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }

            // Keys that a phone keyboard lacks.
            val keys: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth().border(1.dp, line)) {
                    FKey(stringResource(R.string.command_key_history), phosphor, Modifier.weight(1f)) { older() }
                    FKey(stringResource(R.string.command_key_complete), phosphor, Modifier.weight(1f)) { complete() }
                    FKey(stringResource(R.string.command_key_next), phosphor, Modifier.weight(1f)) { next() }
                }
            }

            if (wide) {
                // Wide screens: what is typed on the left, what it will do on the right.
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.weight(1.4f)) {
                        commandLine()
                        quickStarts()
                        listArea(Modifier.weight(1f, fill = false))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        preview()
                        Spacer(Modifier.weight(1f))
                        keys()
                    }
                }
            } else {
                commandLine()
                quickStarts()
                // The preview stays right under the list, and the keys at the bottom.
                Column(Modifier.weight(1f)) {
                    listArea(Modifier.weight(1f, fill = false))
                    preview()
                }
                keys()
            }
        }
    }
}

/**
 * Shows that a list scrolls: a thin bar at the right edge for where the view is, and the bottom
 * fading into [ground] while there is more below.
 */
private fun Modifier.scrollHint(state: ScrollState, bar: Color, ground: Color): Modifier = drawWithContent {
    drawContent()
    val max = state.maxValue
    if (max <= 0 || max == Int.MAX_VALUE) return@drawWithContent
    val viewport = size.height
    val fade = 28.dp.toPx().coerceAtMost(viewport / 3)
    if (state.value < max) {
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, ground), startY = viewport - fade, endY = viewport),
            topLeft = Offset(0f, viewport - fade),
            size = Size(size.width, fade),
        )
    }
    val length = (viewport * viewport / (viewport + max)).coerceAtLeast(24.dp.toPx())
    val top = (viewport - length) * state.value / max
    val width = 3.dp.toPx()
    drawRect(bar, topLeft = Offset(size.width - width - 1.dp.toPx(), top), size = Size(width, length))
}

@Composable
private fun Label(text: String, color: Color, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    Text(
        text,
        color = color,
        fontFamily = mono(),
        fontSize = size,
        letterSpacing = 1.6.sp,
        modifier = modifier,
    )
}

@Composable
private fun FKey(text: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontFamily = mono(), fontSize = 10.sp, letterSpacing = 0.8.sp)
    }
}

/**
 * A QR code from "qr", black on white whatever the theme: phone cameras read that best, and the
 * white margin around it is part of the code.
 */
@Composable
private fun QrImage(code: QrMatrix) {
    Canvas(
        Modifier
            .widthIn(max = 280.dp)
            .fillMaxWidth(0.85f)
            .aspectRatio(1f)
            .background(Color.White),
    ) {
        val cell = size.width / code.size
        for (y in 0 until code.size) {
            for (x in 0 until code.size) {
                // A hair wider than the cell, so no seams show between dark squares.
                if (code[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}
