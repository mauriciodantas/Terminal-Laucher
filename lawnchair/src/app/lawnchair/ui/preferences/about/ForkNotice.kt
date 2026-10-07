package app.lawnchair.ui.preferences.about

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroupHeading
import com.android.launcher3.BuildConfig
import com.android.launcher3.R

private const val ORIGINAL_REPOSITORY = "https://github.com/LawnchairLauncher/lawnchair"

/**
 * The fork notice: states that Terminal is a fork of Lawnchair, that its maintainer claims no
 * rights over the original project's intellectual property, and lists the fork's own data.
 */
fun LazyListScope.forkNoticeItems() {
    item {
        PreferenceGroupHeading(stringResource(R.string.about_fork_heading))
    }
    item {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.about_fork_intro),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.about_fork_no_claim),
                style = MaterialTheme.typography.bodyMedium,
            )
            InfoRow(stringResource(R.string.about_fork_app_label), stringResource(R.string.derived_app_name))
            InfoRow(stringResource(R.string.about_fork_package_label), BuildConfig.APPLICATION_ID)
            InfoRow(stringResource(R.string.about_fork_maintainer_label), stringResource(R.string.about_fork_maintainer_value))
            InfoRow(stringResource(R.string.about_fork_base_label), stringResource(R.string.about_fork_base_value))
            InfoRow(stringResource(R.string.about_fork_license_label), stringResource(R.string.about_fork_license_value))
        }
    }
}

/** Copyright lines of the original project, kept as they appear in its license file. */
@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun OriginalProjectNotice() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.about_original_copyright),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.about_original_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ClickablePreference(
            label = stringResource(R.string.about_original_repo),
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, ORIGINAL_REPOSITORY.toUri())
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                }
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}
