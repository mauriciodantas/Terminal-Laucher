package app.lawnchair.ui.preferences.destinations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lawnchair.command.ApiCall
import app.lawnchair.command.ApiSpec
import app.lawnchair.command.HtmlNode
import app.lawnchair.command.HtmlSelect
import app.lawnchair.command.HtmlTree
import app.lawnchair.command.HttpResult
import app.lawnchair.command.PageDecoding
import app.lawnchair.command.SelectorSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * What the editor's test shows of a page it asked: how it came (type, charset, size, whether it was cut),
 * then the whole text for `--raw`, or the elements a selector finds and the tree of the document to
 * pick the selector from. [answer] is kept by the editor while it is open, so changing [spec] (the
 * selector, the format, the maximum) shows the new result at once, with no new request.
 */
@Composable
internal fun PageTestPanel(
    answer: HttpResult.Answer,
    spec: ApiSpec,
    elapsedMs: Long?,
    onSelector: (String) -> Unit,
) {
    val read = remember(answer, spec) { ApiCall.readPage(spec, answer) }
    val bytes = remember(answer) { answer.body.toByteArray().size }
    val charset = remember(answer) { PageDecoding.fromHeader(answer.contentType)?.name() ?: "detectado" }
    Column(Modifier.fillMaxWidth()) {
        Text(
            buildList {
                add(if (bytes < 1024) "$bytes B baixados" else "${bytes / 1024} KB baixados")
                add(answer.contentType?.substringBefore(';')?.trim() ?: "sem Content-Type")
                add("charset $charset")
            }.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (read.cut.any) {
            Text(
                "Cortado: " + read.cut.describe(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (!read.result.ok) {
            Text(read.result.failure(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        if (spec.raw) {
            RawText(read.result.text.orEmpty())
        } else {
            SelectedElements(read.count, spec, read.result.data)
            Spacer(Modifier.height(8.dp))
            Text("NA BARRA:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                read.result.shown(),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            DocumentTree(answer, spec.select, onSelector)
        }
    }
}

@Composable
private fun SelectedElements(count: Int, spec: ApiSpec, data: String?) {
    Text(
        if (count == 1) "1 elemento casou com ${spec.select}" else "$count elementos casaram com ${spec.select}",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
    )
    val items = remember(data) { runCatching { JSONArray(data.orEmpty()) }.getOrNull() }
    items ?: return
    for (i in 0 until minOf(items.length(), SHOWN_ELEMENTS)) {
        val item = items.optJSONObject(i) ?: continue
        val attrs = item.optJSONObject("attrs")
        val attributes = attrs?.keys()?.asSequence()?.joinToString(" ") { "$it=\"${attrs.optString(it).take(40)}\"" }.orEmpty()
        Text(
            "<${item.optString("tag")}> ${item.optString("text").take(80)}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
        )
        if (attributes.isNotEmpty()) {
            Text(attributes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
    if (items.length() > SHOWN_ELEMENTS) {
        Text("… e mais ${items.length() - SHOWN_ELEMENTS}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The text of `--raw`, with a plain search that lists the lines holding what was typed. */
@Composable
private fun RawText(text: String) {
    var query by remember { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Buscar no texto") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val lines = remember(text) { text.lines() }
    val matches = remember(text, query) {
        if (query.isBlank()) emptyList() else lines.withIndex().filter { it.value.contains(query, ignoreCase = true) }
    }
    if (query.isNotBlank()) {
        Text(
            if (matches.isEmpty()) "nada encontrado" else "${matches.size} linhas",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
    ) {
        Text(
            if (query.isBlank()) text else matches.take(MAX_MATCHES).joinToString("\n") { "${it.index + 1}: ${it.value.trim().take(200)}" },
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** The document as an expandable tree: a tap on a node fills the selector, the `+` of an attribute adds `@attribute`. */
@Composable
private fun DocumentTree(answer: HttpResult.Answer, selector: String, onSelector: (String) -> Unit) {
    val tree by produceState<HtmlNode?>(null, answer) {
        value = withContext(Dispatchers.Default) {
            val markup = HtmlSelect.markupOf(answer.contentType, answer.body) ?: return@withContext null
            runCatching { HtmlTree.of(HtmlSelect.document(answer.body, markup, answer.finalUrl.orEmpty()), markup) }.getOrNull()
        }
    }
    val root = tree ?: return
    Text("DOCUMENTO (toque num nó para usar o seletor dele; + num atributo para pegar o valor):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
    ) {
        HtmlNodeRow(root, 0, SelectorSpec.of(selector).css, onSelector)
    }
}

@Composable
private fun HtmlNodeRow(node: HtmlNode, depth: Int, current: String, onSelector: (String) -> Unit) {
    var expanded by remember(node.path) { mutableStateOf(depth < 1) }
    val picked = node.selector == current
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 12).dp)
                .then(if (picked) Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp)) else Modifier),
        ) {
            if (node.children.isNotEmpty() || node.attrs.isNotEmpty()) {
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (expanded) "Recolher" else "Expandir",
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                Spacer(Modifier.size(24.dp))
            }
            Text(
                node.title + if (node.text.isNotEmpty()) "  ${node.text}" else "",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelector(node.selector) }
                    .padding(vertical = 3.dp),
            )
        }
        if (expanded) {
            node.attrs.forEach { (name, value) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (depth * 12 + 36).dp),
                ) {
                    Text(
                        "$name=\"${value.take(40)}\"",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onSelector(HtmlTree.withAttribute(node.selector, name)) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Pegar o atributo $name", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
            node.children.forEach { HtmlNodeRow(it, depth + 1, current, onSelector) }
        }
    }
}

private const val SHOWN_ELEMENTS = 8
private const val MAX_MATCHES = 200

/** The ways the editor can show what a page command puts on the bar. */
enum class ApiShow(val label: String) {
    JSON("Campos JSON"),
    HTML("Seletor HTML"),
    RAW("Bruto"),
    ;

    companion object {
        fun of(spec: ApiSpec?): ApiShow = when {
            spec?.raw == true -> RAW
            spec?.select?.isNotEmpty() == true -> HTML
            else -> JSON
        }
    }
}
