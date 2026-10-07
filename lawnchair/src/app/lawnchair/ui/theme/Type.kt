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

/**
 * The terminal typography. [font] replaces IBM Plex Mono for text when the user picked another one;
 * the display sizes keep the pixel font unless [pixelDisplay] is off.
 */
fun terminalTypography(font: FontFamily? = null, pixelDisplay: Boolean = true): Typography {
    val text = font ?: PlexMono
    val display = if (pixelDisplay) Vt323 else text
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = display),
        displayMedium = base.displayMedium.copy(fontFamily = display),
        displaySmall = base.displaySmall.copy(fontFamily = display),
        headlineLarge = base.headlineLarge.copy(fontFamily = text),
        headlineMedium = base.headlineMedium.copy(fontFamily = text),
        headlineSmall = base.headlineSmall.copy(fontFamily = text),
        titleLarge = base.titleLarge.copy(fontFamily = text),
        titleMedium = base.titleMedium.copy(fontFamily = text),
        titleSmall = base.titleSmall.copy(fontFamily = text),
        bodyLarge = base.bodyLarge.copy(fontFamily = text, letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = text, letterSpacing = 0.1.sp),
        bodySmall = base.bodySmall.copy(fontFamily = text),
        labelLarge = base.labelLarge.copy(fontFamily = text),
        labelMedium = base.labelMedium.copy(fontFamily = text),
        labelSmall = base.labelSmall.copy(fontFamily = text),
        displayLargeEmphasized = base.displayLargeEmphasized.copy(fontFamily = display),
        displayMediumEmphasized = base.displayMediumEmphasized.copy(fontFamily = display),
        displaySmallEmphasized = base.displaySmallEmphasized.copy(fontFamily = display),
        headlineLargeEmphasized = base.headlineLargeEmphasized.copy(fontFamily = text),
        headlineMediumEmphasized = base.headlineMediumEmphasized.copy(fontFamily = text),
        headlineSmallEmphasized = base.headlineSmallEmphasized.copy(fontFamily = text),
        titleLargeEmphasized = base.titleLargeEmphasized.copy(fontFamily = text),
        titleMediumEmphasized = base.titleMediumEmphasized.copy(fontFamily = text),
        titleSmallEmphasized = base.titleSmallEmphasized.copy(fontFamily = text),
        bodyLargeEmphasized = base.bodyLargeEmphasized.copy(fontFamily = text),
        bodyMediumEmphasized = base.bodyMediumEmphasized.copy(fontFamily = text),
        bodySmallEmphasized = base.bodySmallEmphasized.copy(fontFamily = text),
        labelLargeEmphasized = base.labelLargeEmphasized.copy(fontFamily = text),
        labelMediumEmphasized = base.labelMediumEmphasized.copy(fontFamily = text),
        labelSmallEmphasized = base.labelSmallEmphasized.copy(fontFamily = text),
    )
}

val Typography = terminalTypography()
