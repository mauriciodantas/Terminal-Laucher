package app.lawnchair.ui.preferences.destinations

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.lawnchair.command.CommandAction
import app.lawnchair.command.CommandActivity
import app.lawnchair.command.CommandExtras
import app.lawnchair.command.ScriptContext
import app.lawnchair.command.ScriptInput
import app.lawnchair.command.ScriptLimits
import app.lawnchair.command.ScriptOutput
import app.lawnchair.command.ScriptRunner
import app.lawnchair.command.ScriptSpec
import app.lawnchair.command.StepAction
import app.lawnchair.command.StepActions
import app.lawnchair.command.StepResult
import app.lawnchair.command.Variables
import java.util.Locale
import kotlinx.coroutines.launch

/** Two spaces: what Tab inserts in the code. */
private const val INDENT = "  "

/**
 * The code of a Script command: monospaced, with line numbers, Tab as two spaces and a light
 * highlight. An expression ("node -p") shows only the expression and offers to become a program.
 * [errorLine] is marked in the gutter.
 */
@Composable
internal fun ScriptCodeField(
    code: TextFieldValue,
    onCodeChange: (TextFieldValue) -> Unit,
    isExpression: Boolean,
    errorLine: Int?,
    onConvert: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (isExpression) "CÓDIGO · expressão (node -p)" else "CÓDIGO · programa (node -e)",
            style = MaterialTheme.typography.labelSmall,
            color = colors.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (isExpression) {
            TextButton(onClick = onConvert) { Text("Converter em programa") }
        }
    }
    Spacer(Modifier.height(4.dp))
    val lines = code.text.count { it == '\n' } + 1
    val mono = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = colors.onSurface)
    val highlight = remember(colors) { JsHighlight(colors.primary, colors.tertiary, colors.onSurfaceVariant, colors.secondary) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .border(1.dp, if (errorLine != null) colors.error else colors.outline, RoundedCornerShape(4.dp))
            .padding(vertical = 8.dp),
    ) {
        Column(Modifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.End) {
            for (n in 1..lines) {
                Text(
                    n.toString(),
                    style = mono,
                    color = if (n == errorLine) colors.onError else colors.onSurfaceVariant,
                    modifier = Modifier
                        .background(if (n == errorLine) colors.error else Color.Transparent)
                        .padding(horizontal = 2.dp),
                )
            }
        }
        Box(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(end = 8.dp),
        ) {
            BasicTextField(
                value = code,
                onValueChange = onCodeChange,
                textStyle = mono,
                visualTransformation = highlight,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
                modifier = Modifier
                    .widthIn(min = 240.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Tab) {
                            val sel = code.selection
                            val text = code.text.replaceRange(sel.min, sel.max, INDENT)
                            onCodeChange(TextFieldValue(text, TextRange(sel.min + INDENT.length)))
                            true
                        } else {
                            false
                        }
                    },
            )
        }
    }
}

/** [code] with the cursor at the start of [line], for "Ir para a linha". */
internal fun atLine(code: TextFieldValue, line: Int): TextFieldValue {
    val starts = listOf(0) + code.text.indices.filter { code.text[it] == '\n' }.map { it + 1 }
    val at = starts.getOrElse(line - 1) { code.text.length }
    val end = code.text.indexOf('\n', at).let { if (it < 0) code.text.length else it }
    return code.copy(selection = TextRange(at, end))
}

/**
 * The test of a Script command: the text it gets, an optional output of a previous command
 * (JSON or text) and EXECUTAR, which shows the status and time, the text as the bar shows it, the
 * data as a tree, what its action would do (and a button to do it) and its console.log lines.
 */
@Composable
internal fun ScriptTestPanel(
    spec: ScriptSpec,
    name: String,
    runner: ScriptRunner,
    canRun: Boolean,
    onGoToLine: (Int) -> Unit,
    onRunAction: (CommandAction) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    var text by remember { mutableStateOf("") }
    var stdinOpen by remember { mutableStateOf(false) }
    var stdin by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<StepResult?>(null) }
    var took by remember { mutableLongStateOf(0L) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Texto (input.text)") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        Button(
            enabled = canRun && !running && runner.isSupported,
            onClick = {
                running = true
                val previous = stdin.takeIf { stdinOpen && it.isNotBlank() }?.let { raw ->
                    if (ScriptOutput.isJson(raw)) StepResult.ok(data = raw.trim()) else StepResult.ok(text = raw)
                }
                scope.launch {
                    val start = System.currentTimeMillis()
                    val ctx = ScriptContext(Variables.plain(CommandExtras()), Locale.getDefault().toLanguageTag(), name)
                    result = runner.run(spec, ScriptInput(text, previous), ctx)
                    took = System.currentTimeMillis() - start
                    running = false
                }
            },
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(if (running) "Executando..." else "EXECUTAR")
        }
    }
    if (!runner.isSupported) {
        Text(context.getString(app.lawnchair.command.R.string.cmd_warn_script_unsupported), color = colors.error, style = MaterialTheme.typography.bodySmall)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { stdinOpen = !stdinOpen }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (stdinOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Entrada do comando anterior (stdin)", style = MaterialTheme.typography.bodyMedium)
    }
    if (stdinOpen) {
        OutlinedTextField(
            value = stdin,
            onValueChange = { stdin = it },
            placeholder = { Text("JSON ou texto, como se viesse de \"cmd | ${name.ifEmpty { "nome" }}\"") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
        )
    }

    val r = result ?: return
    Spacer(Modifier.height(12.dp))
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (r.ok) "OK" else "ERRO",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (r.ok) colors.primary else colors.error,
                )
                Text("${took}ms", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            if (!r.ok) {
                Spacer(Modifier.height(4.dp))
                Text(r.failure(), color = colors.error, style = MaterialTheme.typography.bodyMedium)
                r.limit?.let {
                    Text("Limite: " + ScriptLimits.message(it), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                r.line?.let { line ->
                    TextButton(onClick = { onGoToLine(line) }) { Text("Ir para a linha $line") }
                }
            }
            r.text?.let {
                Spacer(Modifier.height(6.dp))
                Text("NA BARRA:", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(it, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, color = colors.primary)
            }
            r.data?.let {
                Spacer(Modifier.height(8.dp))
                Text("DATA:", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                JsonTreeView(rawJson = it, onInsertField = null)
            }
            r.action?.let { action ->
                Spacer(Modifier.height(8.dp))
                Text("AÇÃO:", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Text(StepActions.describe(action), style = MaterialTheme.typography.bodyMedium)
                val problem = StepActions.problem(action)
                if (problem != null) {
                    Text(problem, color = colors.error, style = MaterialTheme.typography.bodySmall)
                } else {
                    OutlinedButton(onClick = {
                        when (action) {
                            is StepAction.Open -> onRunAction(CommandAction.OpenUrl(action.uri))

                            is StepAction.Copy -> onRunAction(CommandAction.Copy(action.text))

                            is StepAction.Share -> onRunAction(CommandAction.ShareNotes(action.text))

                            // A line goes to the bar, which shows it and asks for Enter like any other.
                            is StepAction.Run -> runCatching { CommandActivity.start(context, text = action.line) }
                                .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show() }
                        }
                    }) { Text("Executar ação") }
                }
            }
            if (r.logs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("CONSOLE.LOG:", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                r.logs.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

/** Keywords, strings, comments and numbers of JavaScript in color; the text itself does not change. */
private class JsHighlight(
    private val keyword: Color,
    private val string: Color,
    private val comment: Color,
    private val number: Color,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText = TransformedText(color(text.text), OffsetMapping.Identity)

    private fun color(code: String): AnnotatedString = buildAnnotatedString {
        append(code)
        TOKEN.findAll(code).forEach { m ->
            val style = when {
                m.groups[1] != null -> SpanStyle(color = comment)
                m.groups[2] != null -> SpanStyle(color = string)
                m.groups[3] != null -> SpanStyle(color = number)
                m.value in KEYWORDS -> SpanStyle(color = keyword, fontWeight = FontWeight.Bold)
                else -> null
            }
            if (style != null) addStyle(style, m.range.first, m.range.last + 1)
        }
    }

    private companion object {
        val TOKEN = Regex("""(//[^\n]*|/\*[\s\S]*?\*/)|("(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'|`(?:\\.|[^`\\])*`)|(\b\d+(?:\.\d+)?(?:e\d+)?\b)|([A-Za-z_$][\w$]*)""")
        val KEYWORDS = setOf(
            "function", "return", "const", "let", "var", "if", "else", "for", "while", "do", "break", "continue",
            "switch", "case", "default", "new", "typeof", "instanceof", "in", "of", "async", "await", "try",
            "catch", "finally", "throw", "class", "extends", "null", "undefined", "true", "false", "this",
        )
    }
}
