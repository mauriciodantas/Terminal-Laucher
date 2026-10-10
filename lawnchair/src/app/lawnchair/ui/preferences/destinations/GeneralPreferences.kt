/*
 * Copyright 2022, Lawnchair
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

package app.lawnchair.ui.preferences.destinations

import androidx.compose.ui.res.stringResource
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import com.android.launcher3.R

internal val phosphorEntries = listOf(
    ListPreferenceEntry<ColorOption>(ColorOption.CustomColor(0xFF7DFFB2)) { stringResource(R.string.complementary_green) },
    ListPreferenceEntry<ColorOption>(ColorOption.CustomColor(0xFFFFA63D)) { stringResource(R.string.complementary_amber) },
    ListPreferenceEntry<ColorOption>(ColorOption.CustomColor(0xFF6FD8FF)) { stringResource(R.string.color_cyan) },
    ListPreferenceEntry<ColorOption>(ColorOption.CustomColor(0xFFE8F0E0)) { stringResource(R.string.color_white) },
)
