package app.lawnchair.command

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.lawnchair.command.R as CommandR

/** An app that can open a link, by package. */
internal data class QrLinkTarget(val label: String, val packageName: String)

/** Which apps open a link read from a QR code, and the opening itself. */
internal object QrLinks {

    private fun view(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addCategory(Intent.CATEGORY_BROWSABLE)

    private fun handlers(context: Context, intent: Intent) = context.packageManager
        .queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }
        .distinctBy { it.first }

    /** The browsers: whoever opens a plain address of no site in particular. */
    private fun browsers(context: Context) = handlers(context, view("https://example.com"))

    /** The package that opens [link] as a browser would, or null when none is installed. */
    fun browser(context: Context): String? = browsers(context).firstOrNull()?.first.let { first ->
        val chosen = context.packageManager.resolveActivity(view("https://example.com"), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        chosen?.takeIf { pkg -> browsers(context).any { it.first == pkg } } ?: first
    }

    /** Apps made for this address that are not browsers (a video app for a video link). */
    fun specific(context: Context, link: QrContent.Link): List<QrLinkTarget> {
        val browserPackages = browsers(context).map { it.first }.toSet()
        return handlers(context, view(link.uri))
            .filter { it.first !in browserPackages && it.first != context.packageName }
            .map { QrLinkTarget(it.second, it.first) }
    }

    fun open(context: Context, link: QrContent.Link, packageName: String?): Boolean = runCatching {
        context.startActivity(view(link.uri).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

/**
 * What a QR code holds, shown as read before anything is done with it. The code's own words are
 * the only content; no title or description from it is ever used. [onRun] gets a command the
 * user accepted, [onInsert] gets text for the bar (which puts it there without running it).
 */
@Composable
internal fun QrResultDialog(content: QrContent, onRun: (QrContent.Command) -> Unit, onInsert: (String) -> Unit, onDismiss: () -> Unit) {
    when (content) {
        is QrContent.Link -> LinkDialog(content, onDismiss)

        is QrContent.Command -> ShownText(
            title = stringResource(CommandR.string.cmd_qr_cmd_title),
            text = content.line,
            confirm = { TextButton(onClick = { onRun(content) }) { Text(stringResource(CommandR.string.cmd_qr_run)) } },
            dismiss = { TextButton(onClick = onDismiss) { Text(stringResource(CommandR.string.cmd_qr_cancel)) } },
            onDismiss = onDismiss,
        )

        is QrContent.Text -> TextDialog(content, onInsert, onDismiss)

        // An import goes to its own review screen without this dialog.
        is QrContent.Import -> Unit
    }
}

@Composable
private fun ShownText(title: String, text: String, confirm: @Composable () -> Unit, dismiss: @Composable () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(stringResource(CommandR.string.cmd_qr_read), style = MaterialTheme.typography.bodySmall)
                SelectionContainer {
                    Text(
                        text,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = confirm,
        dismissButton = dismiss,
    )
}

@Composable
private fun TextDialog(content: QrContent.Text, onInsert: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ShownText(
        title = stringResource(CommandR.string.cmd_qr_text_title),
        text = content.raw,
        confirm = {
            Column {
                TextButton(onClick = {
                    onInsert(content.raw)
                    onDismiss()
                }) { Text(stringResource(CommandR.string.cmd_qr_insert)) }
                TextButton(onClick = {
                    CommandExecutor.execute(context, CommandAction.Copy(content.raw))
                    onDismiss()
                }) { Text(stringResource(CommandR.string.cmd_qr_copy)) }
            }
        },
        dismiss = {
            Column {
                TextButton(onClick = {
                    CommandExecutor.execute(context, CommandAction.ShareNotes(content.raw))
                    onDismiss()
                }) { Text(stringResource(CommandR.string.cmd_qr_share)) }
                TextButton(onClick = onDismiss) { Text(stringResource(CommandR.string.cmd_qr_close)) }
            }
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun LinkDialog(link: QrContent.Link, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val apps = remember(link) { QrLinks.specific(context, link) }
    val browser = remember(link) { if (link.isWeb) QrLinks.browser(context) else null }
    val failed = stringResource(CommandR.string.cmd_qr_open_failed)
    fun open(pkg: String?) {
        if (QrLinks.open(context, link, pkg)) onDismiss() else android.widget.Toast.makeText(context, failed, android.widget.Toast.LENGTH_SHORT).show()
    }
    ShownText(
        title = stringResource(CommandR.string.cmd_qr_link_title),
        text = link.uri,
        confirm = {
            // A specific app leads when there is one; with several, the user picks; a browser follows.
            Column {
                apps.forEach { app ->
                    TextButton(onClick = { open(app.packageName) }) { Text(stringResource(CommandR.string.cmd_qr_open_in, app.label)) }
                }
                if (browser != null) {
                    TextButton(onClick = { open(browser) }) { Text(stringResource(CommandR.string.cmd_qr_open_browser)) }
                }
                if (apps.isEmpty() && browser == null) {
                    Text(stringResource(CommandR.string.cmd_qr_no_app), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismiss = {
            Column {
                TextButton(onClick = {
                    CommandExecutor.execute(context, CommandAction.Copy(link.uri))
                    onDismiss()
                }) { Text(stringResource(CommandR.string.cmd_qr_copy)) }
                TextButton(onClick = onDismiss) { Text(stringResource(CommandR.string.cmd_qr_close)) }
            }
        },
        onDismiss = onDismiss,
    )
}
