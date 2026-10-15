/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.preferences.components.controls

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.ModalBottomSheetContent
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.util.bottomSheetHandler
import app.lawnchair.ui.util.preview.PreferenceGroupPreviewContainer
import app.lawnchair.ui.util.preview.PreviewLawnchair
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClickablePreference(
    label: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    confirmationText: String? = null,
    colors: ListItemColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    ),
    hapticToken: MSDLToken? = MSDLToken.TAP_LOW_EMPHASIS,
    startWidget: (@Composable () -> Unit)? = null,
    endWidget: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val bottomSheetHandler = bottomSheetHandler
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(LocalContext.current)
    val handleClick = {
        if (confirmationText != null) {
            mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
            bottomSheetHandler.show {
                PreferenceClickConfirmation(
                    title = label,
                    text = confirmationText,
                    onDismissRequest = { bottomSheetHandler.hide() },
                    onConfirm = onClick,
                )
            }
        } else {
            hapticToken?.let { mMSDLPlayerWrapper.playToken(it) }
            onClick()
        }
    }
    if (onLongClick != null) {
        PreferenceTemplate(
            title = { Text(text = label) },
            modifier = modifier,
            contentModifier = Modifier.combinedClickable(
                onClick = handleClick,
                onLongClick = {
                    mMSDLPlayerWrapper.playToken(MSDLToken.LONG_PRESS)
                    onLongClick()
                },
                indication = ripple(),
                interactionSource = remember { MutableInteractionSource() },
            ),
            description = subtitle?.let { { Text(text = it) } },
            startWidget = startWidget,
            endWidget = endWidget,
            colors = colors,
        )
    } else {
        PreferenceTemplate(
            title = { Text(text = label) },
            modifier = modifier,
            description = subtitle?.let { { Text(text = it) } },
            startWidget = startWidget,
            endWidget = endWidget,
            onClick = handleClick,
            colors = colors,
        )
    }
}

@Composable
fun PreferenceClickConfirmation(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheetContent(
        title = { Text(text = title) },
        text = { Text(text = text) },
        buttons = {
            OutlinedButton(
                onClick = onDismissRequest,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
            Spacer(modifier = Modifier.requiredWidth(8.dp))
            Button(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = stringResource(id = android.R.string.ok))
            }
        },
        modifier = modifier,
    )
}

@PreviewLawnchair
@Composable
private fun ClickablePreferencePreview() {
    LawnchairTheme {
        PreferenceGroupPreviewContainer {
            ClickablePreference(
                label = "Label",
                subtitle = "Subtitle",
                onClick = {},
            )
        }
    }
}
