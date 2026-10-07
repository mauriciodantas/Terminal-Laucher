package app.lawnchair.command

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.LawnchairTheme

/**
 * Opens a command pack the user received (from a chat, a file manager, the share sheet) and asks
 * before adding it. A pack can start other apps once its letters are typed, so nothing is imported
 * without showing what is inside.
 */
class ImportCommandsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pack = incomingUri(intent)?.let { CommandPacks.read(this, it) }
        if (pack == null) {
            Toast.makeText(this, "Arquivo de comandos inválido", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContent {
            LawnchairTheme {
                ImportDialog(pack, onImport = { importPack(pack) }, onDismiss = { finish() })
            }
        }
    }

    private fun importPack(pack: CommandPack) {
        val merge = CommandPacks.merge(CustomActionStore.load(this), AliasStore.load(this), pack)
        CustomActionStore.save(this, merge.actions)
        AliasStore.save(this, merge.aliases)
        val text = "${merge.addedActions} ações e ${merge.addedAliases} apelidos importados" +
            if (merge.skipped > 0) ", ${merge.skipped} ignorados (nome em uso)" else ""
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun incomingUri(intent: Intent): Uri? = when (intent.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        else -> null
    }
}

@Composable
private fun ImportDialog(pack: CommandPack, onImport: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar comandos") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Só importe arquivos de quem você conhece: as ações abrem outros apps. " +
                        "O que você já tem não é substituído.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (pack.actions.isNotEmpty()) {
                    Text("Ações", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    pack.actions.forEach { Text("${it.letter} · ${it.label}", modifier = Modifier.padding(top = 4.dp)) }
                }
                if (pack.aliases.isNotEmpty()) {
                    Text("Apelidos", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    pack.aliases.forEach { Text("${it.name} → ${it.expansion}", modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onImport) { Text("Importar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
