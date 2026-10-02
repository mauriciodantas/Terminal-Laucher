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

package app.lawnchair.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.android.launcher3.R

private val base = Typography()

private val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.Bold),
)

private val Vt323 = FontFamily(Font(R.font.vt323_regular, FontWeight.Normal))

val Typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Vt323),
    displayMedium = base.displayMedium.copy(fontFamily = Vt323),
    displaySmall = base.displaySmall.copy(fontFamily = Vt323),
    headlineLarge = base.headlineLarge.copy(fontFamily = PlexMono),
    headlineMedium = base.headlineMedium.copy(fontFamily = PlexMono),
    headlineSmall = base.headlineSmall.copy(fontFamily = PlexMono),
    titleLarge = base.titleLarge.copy(fontFamily = PlexMono),
    titleMedium = base.titleMedium.copy(fontFamily = PlexMono),
    titleSmall = base.titleSmall.copy(fontFamily = PlexMono),
    bodyLarge = base.bodyLarge.copy(fontFamily = PlexMono, letterSpacing = 0.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = PlexMono, letterSpacing = 0.1.sp),
    bodySmall = base.bodySmall.copy(fontFamily = PlexMono),
    labelLarge = base.labelLarge.copy(fontFamily = PlexMono),
    labelMedium = base.labelMedium.copy(fontFamily = PlexMono),
    labelSmall = base.labelSmall.copy(fontFamily = PlexMono),
    displayLargeEmphasized = base.displayLargeEmphasized.copy(fontFamily = Vt323),
    displayMediumEmphasized = base.displayMediumEmphasized.copy(fontFamily = Vt323),
    displaySmallEmphasized = base.displaySmallEmphasized.copy(fontFamily = Vt323),
    headlineLargeEmphasized = base.headlineLargeEmphasized.copy(fontFamily = PlexMono),
    headlineMediumEmphasized = base.headlineMediumEmphasized.copy(fontFamily = PlexMono),
    headlineSmallEmphasized = base.headlineSmallEmphasized.copy(fontFamily = PlexMono),
    titleLargeEmphasized = base.titleLargeEmphasized.copy(fontFamily = PlexMono),
    titleMediumEmphasized = base.titleMediumEmphasized.copy(fontFamily = PlexMono),
    titleSmallEmphasized = base.titleSmallEmphasized.copy(fontFamily = PlexMono),
    bodyLargeEmphasized = base.bodyLargeEmphasized.copy(fontFamily = PlexMono),
    bodyMediumEmphasized = base.bodyMediumEmphasized.copy(fontFamily = PlexMono),
    bodySmallEmphasized = base.bodySmallEmphasized.copy(fontFamily = PlexMono),
    labelLargeEmphasized = base.labelLargeEmphasized.copy(fontFamily = PlexMono),
    labelMediumEmphasized = base.labelMediumEmphasized.copy(fontFamily = PlexMono),
    labelSmallEmphasized = base.labelSmallEmphasized.copy(fontFamily = PlexMono),
)
