package app.lawnchair.ui.preferences.destinations

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.lawnchair.command.ApiProvider
import app.lawnchair.command.AuthSpec
import app.lawnchair.command.CustomActionStore
import app.lawnchair.command.HttpResult
import app.lawnchair.command.HttpRunner
import app.lawnchair.command.KeyPlacement
import app.lawnchair.command.ProviderStore
import app.lawnchair.command.Providers
import app.lawnchair.command.SecretStore
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.ClickableIcon
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.CommandGuide
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which provider the dialog edits: a new one, or one that exists. */
private sealed interface ProviderEdit {
    data object New : ProviderEdit
    data class Existing(val provider: ApiProvider) : ProviderEdit
}

/**
 * Advanced: the services API commands talk to and their keys. A command names a provider and gives
 * a path; the provider here holds the address and how to authenticate, and its secret is kept
 * encrypted on the device. [open] opens one provider right away, as the bar's CONFIGURE does.
 */
@Composable
fun CommandProvidersPreferences(open: String = "") {
    val context = LocalContext.current
    val navController = LocalNavController.current
    var saved by remember { mutableStateOf(ProviderStore.load(context)) }
    val all = Providers.all(saved)
    val actions = remember { CustomActionStore.load(context) }
    var configured by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(saved) {
        configured = withContext(Dispatchers.IO) { SecretStore.configured(context, Providers.all(saved).map { it.id }) }
    }
    var editing by remember { mutableStateOf(Providers.of(open, all)?.let { ProviderEdit.Existing(it) } as ProviderEdit?) }

    PreferenceLayout(
        label = stringResource(R.string.cmd_providers_title),
        actions = {
            ClickableIcon(
                imageVector = Icons.Rounded.HelpOutline,
                onClick = { navController.navigate(CommandGuide) },
            )
        },
    ) {
        PreferenceGroup(
            heading = stringResource(R.string.cmd_providers_group),
            description = stringResource(R.string.cmd_providers_intro),
        ) {
            ClickablePreference(
                label = stringResource(R.string.cmd_providers_new),
                subtitle = stringResource(R.string.cmd_providers_new_hint),
                onClick = { editing = ProviderEdit.New },
            )
            all.forEach { provider ->
                val uses = actions.count { it.provider == provider.id }
                val state = when {
                    Providers.isBuiltIn(provider.id) -> stringResource(R.string.cmd_providers_builtin)
                    !provider.auth.needsSecret -> stringResource(R.string.cmd_providers_open)
                    provider.id in configured -> stringResource(R.string.cmd_providers_key_saved)
                    else -> stringResource(R.string.cmd_providers_key_missing)
                }
                ClickablePreference(
                    label = provider.name,
                    subtitle = listOfNotNull(
                        provider.id,
                        provider.host,
                        state,
                        uses.takeIf { it > 0 }?.let { stringResource(R.string.cmd_providers_uses, it) },
                    ).joinToString(" · "),
                    onClick = { editing = ProviderEdit.Existing(provider) },
                )
            }
        }
        PreferenceGroup(heading = stringResource(R.string.cmd_pref_group_advanced)) {
            NavigationActionPreference(
                label = stringResource(R.string.cmd_guide_title),
                destination = CommandGuide,
                subtitle = stringResource(R.string.cmd_guide_subtitle),
            )
        }
    }

    editing?.let { edit ->
        val current = (edit as? ProviderEdit.Existing)?.provider
        ProviderDialog(
            current = current,
            existing = saved,
            hasSecret = current?.id in configured,
            uses = current?.let { p -> actions.count { it.provider == p.id } } ?: 0,
            onSave = { provider, secret ->
                saved = saved.filter { it.id != current?.id && it.id != provider.id } + provider
                ProviderStore.save(context, saved)
                when {
                    !provider.auth.needsSecret -> {
                        SecretStore.remove(context, provider.id)
                        configured = configured - provider.id
                    }

                    // The list says "key saved" right away, even when nothing else changed.
                    !secret.isNullOrEmpty() -> {
                        SecretStore.put(context, provider.id, secret)
                        configured = configured + provider.id
                    }
                }
                editing = null
            },
            onRemoveSecret = {
                current?.let { SecretStore.remove(context, it.id) }
                configured = configured - current?.id.orEmpty()
            },
            onRemove = {
                current?.let { p ->
                    saved = saved.filter { it.id != p.id }
                    ProviderStore.save(context, saved)
                    SecretStore.remove(context, p.id)
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

private enum class AuthKind { NONE, KEY, BEARER, BASIC }

@Composable
private fun ProviderDialog(
    current: ApiProvider?,
    existing: List<ApiProvider>,
    hasSecret: Boolean,
    uses: Int,
    onSave: (ApiProvider, String?) -> Unit,
    onRemoveSecret: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val builtIn = current != null && Providers.isBuiltIn(current.id)
    val auth = current?.auth ?: AuthSpec.None
    var id by remember { mutableStateOf(current?.id.orEmpty()) }
    var name by remember { mutableStateOf(current?.name.orEmpty()) }
    var base by remember { mutableStateOf(current?.baseUrl ?: "https://") }
    var hosts by remember { mutableStateOf(current?.hosts?.joinToString(", ").orEmpty()) }
    var kind by remember {
        mutableStateOf(
            when (auth) {
                AuthSpec.None -> AuthKind.NONE
                is AuthSpec.ApiKey -> AuthKind.KEY
                AuthSpec.Bearer -> AuthKind.BEARER
                is AuthSpec.Basic -> AuthKind.BASIC
            },
        )
    }
    var keyName by remember { mutableStateOf((auth as? AuthSpec.ApiKey)?.name.orEmpty()) }
    var placement by remember { mutableStateOf((auth as? AuthSpec.ApiKey)?.placement ?: KeyPlacement.HEADER) }
    var prefix by remember { mutableStateOf((auth as? AuthSpec.ApiKey)?.prefix.orEmpty()) }
    var user by remember { mutableStateOf((auth as? AuthSpec.Basic)?.user.orEmpty()) }
    var secret by remember { mutableStateOf("") }
    var secretSaved by remember { mutableStateOf(hasSecret) }
    var testPath by remember { mutableStateOf(current?.testPath.orEmpty()) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    val draft = ApiProvider(
        id = id.trim().lowercase(),
        name = name.trim(),
        baseUrl = base.trim(),
        hosts = hosts.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        auth = when (kind) {
            AuthKind.NONE -> AuthSpec.None
            AuthKind.KEY -> AuthSpec.ApiKey(keyName.trim(), placement, prefix)
            AuthKind.BEARER -> AuthSpec.Bearer
            AuthKind.BASIC -> AuthSpec.Basic(user.trim())
        },
        // What the form does not show stays as it came: fixed headers, the help link, the pack.
        headers = current?.headers.orEmpty(),
        testPath = testPath.trim(),
        keyHelp = current?.keyHelp.orEmpty(),
        pack = current?.pack.orEmpty(),
    )
    val error = if (builtIn) {
        null
    } else {
        (if (current == null) Providers.validateId(draft.id, existing) else null) ?: Providers.validate(draft)
    }
    val okLabel = stringResource(R.string.cmd_providers_test_ok)
    val refusedLabel = stringResource(R.string.cmd_providers_test_refused)

    fun changed() {
        result = null
    }

    @Composable
    fun Field(value: String, onChange: (String) -> Unit, label: Int, enabled: Boolean = !builtIn, hint: String? = null, secret: Boolean = false) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onChange(it)
                changed()
            },
            label = { Text(stringResource(label)) },
            supportingText = hint?.let { { Text(it) } },
            enabled = enabled,
            singleLine = true,
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = if (secret) {
                KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
            } else {
                KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }

    @Composable
    fun Choice(selected: Boolean, label: Int, onClick: () -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clickable(enabled = !builtIn) {
                    onClick()
                    changed()
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, enabled = !builtIn, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(current?.name ?: stringResource(R.string.cmd_providers_new)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (builtIn) {
                    Text(
                        stringResource(R.string.cmd_providers_builtin_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Field(id, { id = it }, R.string.cmd_providers_id, enabled = current == null, hint = stringResource(R.string.cmd_providers_id_hint))
                Field(name, { name = it }, R.string.cmd_providers_name)
                Field(base, { base = it }, R.string.cmd_providers_base)
                Field(hosts, { hosts = it }, R.string.cmd_providers_hosts, hint = stringResource(R.string.cmd_providers_hosts_hint))

                Text(
                    stringResource(R.string.cmd_providers_auth),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Choice(kind == AuthKind.NONE, R.string.cmd_providers_auth_none) { kind = AuthKind.NONE }
                Choice(kind == AuthKind.KEY, R.string.cmd_providers_auth_key) { kind = AuthKind.KEY }
                Choice(kind == AuthKind.BEARER, R.string.cmd_providers_auth_bearer) { kind = AuthKind.BEARER }
                Choice(kind == AuthKind.BASIC, R.string.cmd_providers_auth_basic) { kind = AuthKind.BASIC }
                if (kind == AuthKind.KEY) {
                    Field(keyName, { keyName = it }, R.string.cmd_providers_key_name)
                    Choice(placement == KeyPlacement.HEADER, R.string.cmd_providers_in_header) { placement = KeyPlacement.HEADER }
                    Choice(placement == KeyPlacement.QUERY, R.string.cmd_providers_in_query) { placement = KeyPlacement.QUERY }
                    Field(prefix, { prefix = it }, R.string.cmd_providers_prefix)
                }
                if (kind == AuthKind.BASIC) Field(user, { user = it }, R.string.cmd_providers_user)
                if (kind != AuthKind.NONE) {
                    Field(
                        secret,
                        { secret = it },
                        R.string.cmd_providers_secret,
                        enabled = true,
                        hint = if (secretSaved) stringResource(R.string.cmd_providers_secret_saved) else null,
                        secret = true,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (secretSaved) {
                            TextButton(onClick = {
                                onRemoveSecret()
                                secretSaved = false
                                changed()
                            }) { Text(stringResource(R.string.cmd_providers_secret_remove), color = MaterialTheme.colorScheme.error) }
                        }
                        current?.keyHelp?.takeIf { it.isNotEmpty() }?.let { help ->
                            TextButton(onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(help)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }) { Text(stringResource(R.string.cmd_providers_get_key)) }
                        }
                    }
                    Text(
                        stringResource(R.string.cmd_providers_security),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Field(testPath, { testPath = it }, R.string.cmd_providers_test_path, enabled = !builtIn)
                if (current != null && current.headers.isNotEmpty()) {
                    Text(
                        current.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (uses > 0 && !builtIn) {
                    Text(
                        stringResource(R.string.cmd_providers_used_by, uses),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                val shown = if (testing) stringResource(R.string.command_running) to true else result
                shown?.let { (text, ok) ->
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            DialogButtons(
                saveEnabled = error == null && !builtIn,
                testEnabled = !testing && (builtIn || Providers.validate(draft) == null),
                canRemove = current != null && !builtIn,
                onSave = { onSave(draft, secret.ifEmpty { null }) },
                onTest = {
                    testing = true
                    val provider = current?.takeIf { builtIn } ?: draft
                    val typed = secret
                    scope.launch {
                        val answer = withContext(Dispatchers.IO) {
                            val key = typed.ifEmpty { provider.id.takeIf { it.isNotEmpty() }?.let { SecretStore.get(context, it) }.orEmpty() }
                            HttpRunner.get(provider, provider.testPath, key)
                        }
                        result = when (answer) {
                            is HttpResult.Answer -> okLabel.format(answer.code) to true
                            is HttpResult.Refused -> refusedLabel.format(answer.code) to false
                            else -> HttpRunner.describe(answer, provider) to false
                        }
                        testing = false
                    }
                },
                onRemove = onRemove,
                onCancel = onDismiss,
            )
        },
    )
}
