package app.lawnchair.ui.preferences.destinations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.lawnchair.command.UserVariable
import app.lawnchair.command.UserVariableStore
import app.lawnchair.command.UserVariables
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R

/** Which variable the dialog edits: a new one, or one that exists. */
private sealed interface VariableEdit {
    data object New : VariableEdit
    data class Existing(val variable: UserVariable) : VariableEdit
}

/**
 * The texts `{NAME}` stands for in any command of the bar. The same list "export" and "unset" change
 * from the command line.
 */
@Composable
fun CommandVariablesPreferences() {
    val context = LocalContext.current
    var variables by remember { mutableStateOf(UserVariableStore.load(context)) }
    var editing by remember { mutableStateOf<VariableEdit?>(null) }

    fun save(list: List<UserVariable>) {
        variables = list
        UserVariableStore.save(context, list)
    }

    PreferenceLayout(label = stringResource(R.string.cmd_variables_title)) {
        PreferenceGroup(
            heading = stringResource(R.string.cmd_variables_group),
            description = stringResource(R.string.cmd_variables_intro),
        ) {
            ClickablePreference(
                label = stringResource(R.string.cmd_variables_new),
                subtitle = stringResource(R.string.cmd_variables_new_hint),
                onClick = { editing = VariableEdit.New },
            )
            variables.forEach { variable ->
                ClickablePreference(
                    label = "{" + variable.name + "}",
                    subtitle = variable.value,
                    onClick = { editing = VariableEdit.Existing(variable) },
                )
            }
        }
    }

    editing?.let { edit ->
        val current = (edit as? VariableEdit.Existing)?.variable
        VariableDialog(
            current = current,
            existing = variables,
            onSave = { name, value ->
                // Renaming takes the old name away; a name that exists is replaced where it was.
                val base = if (current != null && UserVariables.key(current.name) != UserVariables.key(name)) {
                    UserVariables.remove(variables, current.name)
                } else {
                    variables
                }
                save(UserVariables.set(base, name, value))
                editing = null
            },
            onRemove = {
                current?.let { save(UserVariables.remove(variables, it.name)) }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun VariableDialog(
    current: UserVariable?,
    existing: List<UserVariable>,
    onSave: (String, String) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(current?.name.orEmpty()) }
    var value by remember { mutableStateOf(current?.value.orEmpty()) }
    // Editing keeps the variable's own slot free for its name; the others still count.
    val others = existing.filter { current == null || UserVariables.key(it.name) != UserVariables.key(current.name) }
    val error = UserVariables.validate(name, value, others)
    // Nothing is said about a form still empty.
    val shown = error.takeIf { name.isNotBlank() || value.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (current == null) R.string.cmd_variables_new else R.string.cmd_variables_edit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.trim() },
                    label = { Text(stringResource(R.string.cmd_variables_name)) },
                    prefix = { Text("{") },
                    suffix = { Text("}") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.replace('\n', ' ') },
                    label = { Text(stringResource(R.string.cmd_variables_value)) },
                    supportingText = shown?.let { { Text(it) } },
                    isError = shown != null,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, value) }, enabled = error == null) {
                Text(stringResource(R.string.cmd_variables_save))
            }
        },
        dismissButton = {
            Column {
                if (current != null) {
                    TextButton(onClick = onRemove) { Text(stringResource(R.string.cmd_variables_remove)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            }
        },
    )
}
