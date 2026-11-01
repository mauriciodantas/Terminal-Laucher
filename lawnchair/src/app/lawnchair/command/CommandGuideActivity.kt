package app.lawnchair.command

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchair.command.R as CommandR
import app.lawnchair.theme.LauncherGround
import app.lawnchair.theme.color.tokens.PhosphorColorToken
import app.lawnchair.ui.theme.LawnchairTheme
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The guide of commands: every command the bar understands, by task, with what it does, how to
 * write it and the user's own letters, packs and aliases. It is built by [CommandGuide] each time
 * it opens, so what the user creates shows up. Picking an entry fills the command line; nothing
 * runs from here.
 */
class CommandGuideActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val query = intent?.getStringExtra(EXTRA_QUERY).orEmpty()
        setContent {
            LawnchairTheme {
                GuideScreen(query, onClose = { finish() })
            }
        }
    }

    companion object {
        private const val EXTRA_QUERY = "guide_query"

        fun start(context: Context, query: String = "") {
            context.startActivity(
                Intent(context, CommandGuideActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_QUERY, query),
            )
        }
    }
}

private data class GuideData(val entries: List<GuideEntry>, val loaded: Boolean)

private val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

/** The launcher's bar has fixed sizes; these are the ones the guide draws with, named like the Terminal Bar's tokens. */
private object GuideTokens {
    val metrics = GuideMetrics
    val type = GuideType
    val chrome = GuideChrome
}

private object GuideMetrics {
    val screenPadding = 8.dp
    val panelPaddingX = 14.dp
    val panelPaddingY = 10.dp
    val inputHeight = 48.dp
    val touchTarget = 44.dp
    val gap = 8.dp
    val gapSmall = 6.dp
    val innerPaddingX = 10.dp
}

private object GuideType {
    val small = 11.sp
    val body = 12.5.sp
    val title = 14.sp
    val input = 15.sp
    val bodyLine = 18.sp
    val labelTracking = 1.6.sp
    val titleTracking = 0.2.sp
    val buttonTracking = 0.5.sp
}

private object GuideChrome {
    const val PROMPT = ">"
    const val CLOSE = "[×]"
}

/** One thing the list shows: a section header or an entry under it. */
private sealed interface GuideRow {
    data class Header(val section: GuideSection, val open: Boolean) : GuideRow
    data class Item(val entry: GuideEntry) : GuideRow
    data object MineEmpty : GuideRow
}

private fun GuideEntry.key(): String = "$group:$word:$usage"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GuideScreen(initialQuery: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val t = GuideTokens
    val phosphor = remember { Color(PhosphorColorToken(1f).resolveColor(context)) }
    val dim = remember { Color(PhosphorColorToken(0.62f).resolveColor(context)) }
    val line = remember { Color(PhosphorColorToken(0.4f).resolveColor(context)) }
    val ground = remember { Color(LauncherGround.get(context)) }
    val mono = MaterialTheme.typography.bodyMedium.fontFamily ?: PlexMono

    var data by remember { mutableStateOf(GuideData(emptyList(), false)) }
    LaunchedEffect(Unit) {
        data = withContext(Dispatchers.IO) {
            val extras = CommandSources.load(context, CommandActivity.loadHistory(context))
            GuideData(CommandGuide.entries(CustomActionStore.load(context), AliasStore.load(context), extras), true)
        }
    }

    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var closedByUser by rememberSaveable { mutableStateOf(setOf<String>()) }
    var openedByUser by rememberSaveable { mutableStateOf(setOf<String>()) }
    var openEntry by rememberSaveable { mutableStateOf<String?>(null) }

    val searching = query.isNotBlank()
    val sections = remember(data, query) { CommandGuide.sections(data.entries, query) }
    val hasMine = data.entries.any { it.group == GuideGroup.MINE }
    val total = sections.sumOf { it.entries.size }

    fun isOpen(group: GuideGroup): Boolean {
        val id = group.name
        // A search opens every section with a result; otherwise only the user's own start open.
        return if (searching) id !in closedByUser else (group == GuideGroup.MINE && id !in closedByUser) || id in openedByUser
    }

    val rows = buildList {
        if (!searching && data.loaded && !hasMine) add(GuideRow.MineEmpty)
        sections.forEach { section ->
            val open = isOpen(section.group)
            add(GuideRow.Header(section, open))
            if (open) section.entries.forEach { add(GuideRow.Item(it)) }
        }
    }

    // What goes to the command line: the word of an entry, or a whole example.
    fun use(text: String) {
        CommandActivity.start(context, text = text)
    }

    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    LaunchedEffect(query) { listState.scrollToItem(0) }
    Box(
        Modifier
            .fillMaxSize()
            .background(ground)
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .padding(t.metrics.screenPadding)
                .border(1.dp, line)
                .padding(horizontal = t.metrics.panelPaddingX, vertical = t.metrics.panelPaddingY),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(CommandR.string.cmd_manual_title),
                    color = phosphor,
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = t.type.title,
                    letterSpacing = t.type.titleTracking,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Box(
                    Modifier
                        .defaultMinSize(minWidth = t.metrics.touchTarget, minHeight = t.metrics.touchTarget)
                        .clickable(onClickLabel = stringResource(CommandR.string.cmd_manual_close), role = Role.Button, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(t.chrome.CLOSE, color = phosphor, fontFamily = mono, fontSize = t.type.small)
                }
            }
            Spacer(Modifier.height(t.metrics.gapSmall))

            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = t.metrics.inputHeight)
                    .border(1.dp, line)
                    .padding(horizontal = t.metrics.innerPaddingX),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t.chrome.PROMPT, color = phosphor, fontFamily = mono, fontSize = t.type.input)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Box(Modifier.weight(1f)) {
                    // The hint is what a screen reader reads before the field; the field keeps its own text.
                    if (query.isEmpty()) {
                        Text(stringResource(CommandR.string.cmd_manual_search_hint), color = dim, fontFamily = mono, fontSize = t.type.input)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = phosphor, fontFamily = mono, fontSize = t.type.input),
                        cursorBrush = SolidColor(phosphor),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                }
                if (query.isNotEmpty()) {
                    GuideLink(
                        stringResource(CommandR.string.cmd_manual_clear),
                        stringResource(CommandR.string.cmd_manual_clear_description),
                        phosphor,
                    ) {
                        query = ""
                        focus.requestFocus()
                    }
                }
            }
            Spacer(Modifier.height(t.metrics.gapSmall))

            // The only live region: it is what a screen reader announces while typing.
            Text(
                if (data.loaded) CommandGuide.countLabel(if (searching) total else data.entries.size) else "",
                color = dim,
                fontFamily = mono,
                fontSize = t.type.small,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(t.metrics.gapSmall))

            LazyColumn(
                state = listState,
                // Room at the right edge for the scroll bar, so it does not cover the counts.
                contentPadding = PaddingValues(end = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .listHint(listState, dim, ground),
            ) {
                if (searching && data.loaded && total == 0) {
                    item(key = "empty") {
                        EmptyResult(query.trim(), CommandGuide.suggestions(data.entries), phosphor, dim, mono) { query = it }
                    }
                }
                rows.forEach { row ->
                    when (row) {
                        GuideRow.MineEmpty -> item(key = "mine-empty") {
                            MineEmpty(phosphor, dim, line, mono) { use(BuiltInCommand.PACK.word + " ") }
                        }

                        is GuideRow.Header -> stickyHeader(key = "h:" + row.section.group.name) {
                            Column(Modifier.fillMaxWidth().background(ground)) {
                                SectionHeader(row, phosphor, dim, line, mono) {
                                    val id = row.section.group.name
                                    if (row.open) {
                                        closedByUser = closedByUser + id
                                        openedByUser = openedByUser - id
                                    } else {
                                        closedByUser = closedByUser - id
                                        openedByUser = openedByUser + id
                                    }
                                }
                            }
                        }

                        is GuideRow.Item -> item(key = "i:" + row.entry.key()) {
                            EntryRow(
                                row.entry,
                                expanded = openEntry == row.entry.key(),
                                phosphor = phosphor,
                                dim = dim,
                                line = line,
                                mono = mono,
                                onToggle = { openEntry = if (openEntry == row.entry.key()) null else row.entry.key() },
                                onUse = { use(row.entry.fill) },
                                onExample = { use(it) },
                            )
                        }
                    }
                }
                // At the end of the list, not fixed under it: with a big font or the keyboard up it would take the room of the commands.
                item(key = "footer") {
                    Text(
                        stringResource(CommandR.string.cmd_manual_footer),
                        color = dim,
                        fontFamily = mono,
                        fontSize = t.type.small,
                        modifier = Modifier.padding(vertical = t.metrics.gap),
                    )
                }
            }
        }
    }
}

/** Shows that the list scrolls: a thin bar at the right edge and the bottom fading into [ground] while there is more below. */
private fun Modifier.listHint(state: LazyListState, bar: Color, ground: Color): Modifier = drawWithContent {
    drawContent()
    val visible = state.layoutInfo.visibleItemsInfo
    val total = state.layoutInfo.totalItemsCount
    if (visible.isEmpty() || (!state.canScrollForward && !state.canScrollBackward)) return@drawWithContent
    val viewport = size.height
    if (state.canScrollForward) {
        val fade = 28.dp.toPx().coerceAtMost(viewport / 3)
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, ground), startY = viewport - fade, endY = viewport),
            topLeft = Offset(0f, viewport - fade),
            size = Size(size.width, fade),
        )
    }
    val length = (viewport * visible.size / total).coerceIn(24.dp.toPx(), viewport)
    val fraction = (state.firstVisibleItemIndex.toFloat() / (total - visible.size).coerceAtLeast(1)).coerceIn(0f, 1f)
    val width = 3.dp.toPx()
    drawRect(bar, topLeft = Offset(size.width - width - 1.dp.toPx(), (viewport - length) * fraction), size = Size(width, length))
}

@Composable
private fun SectionHeader(row: GuideRow.Header, phosphor: Color, dim: Color, line: Color, mono: FontFamily, onToggle: () -> Unit) {
    val t = GuideTokens
    val title = stringResource(row.section.group.title)
    val count = CommandGuide.sectionLabel(title, row.section.entries.size)
    val state = stringResource(if (row.open) CommandR.string.cmd_manual_expanded else CommandR.string.cmd_manual_collapsed)
    val action = stringResource(if (row.open) CommandR.string.cmd_manual_action_collapse else CommandR.string.cmd_manual_action_expand)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = t.metrics.touchTarget)
            .clickable(onClickLabel = action, role = Role.Button, onClick = onToggle)
            .semantics {
                heading()
                contentDescription = count
                stateDescription = state
            }
            .padding(top = t.metrics.gapSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (if (row.open) "▾ " else "▸ ") + title,
            color = phosphor,
            fontFamily = mono,
            fontWeight = FontWeight.SemiBold,
            fontSize = t.type.body,
            letterSpacing = t.type.labelTracking,
            modifier = Modifier.weight(1f),
        )
        Text("${row.section.entries.size}", color = dim, fontFamily = mono, fontSize = t.type.small)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(line))
}

@Composable
private fun EntryRow(
    entry: GuideEntry,
    expanded: Boolean,
    phosphor: Color,
    dim: Color,
    line: Color,
    mono: FontFamily,
    onToggle: () -> Unit,
    onUse: () -> Unit,
    onExample: (String) -> Unit,
) {
    val t = GuideTokens
    val state = stringResource(if (expanded) CommandR.string.cmd_manual_expanded else CommandR.string.cmd_manual_collapsed)
    val origin = CommandGuide.origin(entry)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = state }
            .heightIn(min = t.metrics.touchTarget)
            .padding(vertical = t.metrics.gapSmall),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.usage,
                color = phosphor,
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = t.type.body,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (origin.isNotEmpty()) {
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(origin, color = dim, fontFamily = mono, fontSize = t.type.small, maxLines = 1)
            }
        }
        Text(
            entry.summary,
            color = dim,
            fontFamily = mono,
            fontSize = t.type.body,
            lineHeight = t.type.bodyLine,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (expanded) {
            if (entry.details.isNotBlank()) {
                Text(entry.details, color = dim, fontFamily = mono, fontSize = t.type.body, lineHeight = t.type.bodyLine)
            }
            if (entry.examples.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(stringResource(CommandR.string.cmd_manual_examples), color = dim, fontFamily = mono, fontSize = t.type.small, letterSpacing = t.type.labelTracking)
                // An example is a whole command: tapping it fills the line with it.
                entry.examples.forEach { example ->
                    val description = stringResource(CommandR.string.cmd_manual_use_example, example)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = t.metrics.touchTarget)
                            .clickable(onClickLabel = description, role = Role.Button) { onExample(example) },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text("› $example", color = phosphor, fontFamily = mono, fontSize = t.type.body, lineHeight = t.type.bodyLine)
                    }
                }
            }
            GuideLink(
                stringResource(CommandR.string.cmd_manual_use) + " " + entry.word,
                stringResource(CommandR.string.cmd_manual_use_description, entry.word),
                phosphor,
                onUse,
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(line.copy(alpha = 0.35f)))
}

/** What a search that finds nothing says, with a few commands to try. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyResult(query: String, suggestions: List<String>, phosphor: Color, dim: Color, mono: FontFamily, onTry: (String) -> Unit) {
    val t = GuideTokens
    Column(Modifier.fillMaxWidth().padding(vertical = t.metrics.gap)) {
        Text(
            stringResource(CommandR.string.cmd_manual_empty, query),
            color = dim,
            fontFamily = mono,
            fontSize = t.type.body,
            lineHeight = t.type.bodyLine,
        )
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(t.metrics.gap), verticalArrangement = Arrangement.Center) {
                Box(Modifier.defaultMinSize(minHeight = t.metrics.touchTarget), contentAlignment = Alignment.CenterStart) {
                    Text(stringResource(CommandR.string.cmd_manual_try), color = dim, fontFamily = mono, fontSize = t.type.small, letterSpacing = t.type.labelTracking)
                }
                suggestions.forEach { word -> GuideLink("[$word]", word, phosphor) { onTry(word) } }
            }
        }
    }
}

@Composable
private fun MineEmpty(phosphor: Color, dim: Color, line: Color, mono: FontFamily, onUse: () -> Unit) {
    val t = GuideTokens
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = t.metrics.gapSmall)
            .border(1.dp, line)
            .padding(t.metrics.innerPaddingX),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(CommandR.string.cmd_manual_mine_empty_title), color = phosphor, fontFamily = mono, fontWeight = FontWeight.SemiBold, fontSize = t.type.body)
        Text(stringResource(CommandR.string.cmd_manual_mine_empty_body), color = dim, fontFamily = mono, fontSize = t.type.body, lineHeight = t.type.bodyLine)
        GuideLink(
            stringResource(CommandR.string.cmd_manual_use) + " " + BuiltInCommand.PACK.word,
            stringResource(CommandR.string.cmd_manual_use_description, BuiltInCommand.PACK.word),
            phosphor,
            onUse,
        )
    }
}

/** A bracketed action with a touch target and a label for screen readers, like the links under an answer in the bar. */
@Composable
private fun GuideLink(text: String, description: String, color: Color, onClick: () -> Unit) {
    val t = GuideTokens
    val font = MaterialTheme.typography.bodyMedium.fontFamily ?: PlexMono
    Box(
        Modifier
            .defaultMinSize(minWidth = t.metrics.touchTarget, minHeight = t.metrics.touchTarget)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text, color = color, fontFamily = font, fontWeight = FontWeight.Bold, fontSize = t.type.small, letterSpacing = t.type.buttonTracking)
    }
}
