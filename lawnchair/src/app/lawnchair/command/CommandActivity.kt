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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import app.lawnchair.theme.LauncherGround
import app.lawnchair.theme.color.tokens.PhosphorColorToken
import app.lawnchair.ui.theme.LawnchairTheme
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        /** [voice] opens the speech recognizer right away, for the microphone on the home screen. */
        fun start(context: Context, voice: Boolean = false) {
            CommandExecutor.prewarm(context)
            context.startActivity(Intent(context, CommandActivity::class.java).putExtra(EXTRA_VOICE, voice))
        }

        fun voiceIntent(context: Context): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.command_voice_prompt))

        fun voiceAvailable(context: Context): Boolean = context.packageManager.resolveActivity(voiceIntent(context), 0) != null

        internal fun wantsVoice(intent: Intent?): Boolean = intent?.getBooleanExtra(EXTRA_VOICE, false) == true

        fun loadHistory(context: Context): List<String> = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, "")
            .orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }

        fun saveHistory(context: Context, history: List<String>) {
            context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
                .putString(KEY_HISTORY, history.joinToString("\n")).apply()
        }

        fun clearHistory(context: Context) {
            context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().remove(KEY_HISTORY).apply()
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
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            history = CommandActivity.loadHistory(context)
            custom = CustomActionStore.load(context)
            aliases = AliasStore.load(context)
            contacts = CommandExecutor.loadContacts(context)
            apps = CommandExecutor.loadApps(context)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = CommandExecutor.hasContactsPermission(context)
        scope.launch { contacts = withContext(Dispatchers.IO) { CommandExecutor.loadContacts(context) } }
    }

    var pendingAction by remember { mutableStateOf<CommandAction?>(null) }
    var pendingCommand by remember { mutableStateOf("") }

    var field by remember { mutableStateOf(TextFieldValue("")) }
    var selected by remember { mutableIntStateOf(0) }
    var historyIndex by remember { mutableIntStateOf(-1) }
    var done by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    val text = field.text
    val analysis = remember(text, apps, contacts, granted, custom, aliases) {
        CommandEngine.analyze(text, apps, contacts, granted, custom, aliases)
    }
    val sel = selected.coerceIn(0, maxOf(0, analysis.suggestions.size - 1))
    val top = analysis.suggestions.getOrNull(sel)
    val ghost = if (done == null) CommandEngine.ghost(text, top) else ""

    fun setText(value: String) {
        field = TextFieldValue(value, TextRange(value.length))
        selected = 0
        done = null
    }

    val executedLabel = stringResource(R.string.command_executed)
    val failedLabel = stringResource(R.string.command_failed)

    fun commit(action: CommandAction, command: String) {
        val ok = CommandExecutor.execute(context, action)
        if (!ok) {
            done = failedLabel
            return
        }
        history = CommandEngine.pushHistory(history, command)
        CommandActivity.saveHistory(context, history)
        CommandUsage.record(context, CommandUsage.keysFor(action, command, aliases))
        historyIndex = -1
        if (action is CommandAction.Calc) {
            done = context.getString(R.string.command_copied, action.result)
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

    fun runAction(action: CommandAction, command: String) {
        if (action is CommandAction.Call && !CommandExecutor.hasCallPermission(context)) {
            pendingAction = action
            pendingCommand = command
            callPermission.launch(android.Manifest.permission.CALL_PHONE)
            return
        }
        commit(action, command)
    }

    fun run() {
        analysis.action?.let { runAction(it, text) }
    }

    /** A suggestion that is already a complete, runnable command runs on the tap, without Enter. */
    fun pick(suggestion: Suggestion) {
        setText(suggestion.completion)
        CommandEngine.analyze(suggestion.completion, apps, contacts, granted, custom, aliases).action
            ?.let { runAction(it, suggestion.completion.trim()) }
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
            val action = CommandEngine.analyze(value, apps, contacts, granted, custom, aliases).action
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

    val ground = remember { Color(LauncherGround.get(context)) }
    Box(
        Modifier
            .fillMaxSize()
            .background(ground)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(8.dp)
                .border(1.dp, line)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
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

            // The command line.
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
                    .padding(top = 12.dp)
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

            // One-tap starts while nothing is typed: recent commands, aliases and letters.
            if (text.isEmpty() && done == null) {
                val chips = remember(history, aliases, custom) { CommandEngine.quickChips(history, aliases, custom) }
                if (chips.isNotEmpty()) {
                    Row(Modifier.padding(top = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Label(stringResource(R.string.command_chips_title), dim, 10.sp, Modifier.weight(1f))
                        if (history.isNotEmpty()) {
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
                                    if (chip.kind == "RECENTE") "↺" else "★",
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

            // Suggestions.
            Column(
                Modifier
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
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
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
                    analysis.suggestions.forEachIndexed { index, suggestion ->
                        val active = index == sel
                        Row(
                            Modifier
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

            // Preview.
            val previewTitle = if (done != null) stringResource(R.string.command_executed) else analysis.previewTitle
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
                    modifier = Modifier.padding(top = 3.dp),
                )
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

            Spacer(Modifier.weight(1f))

            // Keys that a phone keyboard lacks.
            Row(Modifier.fillMaxWidth().border(1.dp, line)) {
                FKey(stringResource(R.string.command_key_history), phosphor, Modifier.weight(1f)) { older() }
                FKey(stringResource(R.string.command_key_complete), phosphor, Modifier.weight(1f)) { complete() }
                FKey(stringResource(R.string.command_key_next), phosphor, Modifier.weight(1f)) { next() }
            }
        }
    }
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
