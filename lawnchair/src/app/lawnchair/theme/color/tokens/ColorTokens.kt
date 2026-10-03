package app.lawnchair.theme.color.tokens

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Suppress("MemberVisibilityCanBePrivate", "unused")
object ColorTokens {
    val Neutral1_0 = SwatchColorToken(Swatch.Neutral1, Shade.S0)
    val Neutral1_10 = SwatchColorToken(Swatch.Neutral1, Shade.S10)
    val Neutral1_50 = SwatchColorToken(Swatch.Neutral1, Shade.S50)
    val Neutral1_100 = SwatchColorToken(Swatch.Neutral1, Shade.S100)
    val Neutral1_200 = SwatchColorToken(Swatch.Neutral1, Shade.S200)
    val Neutral1_400 = SwatchColorToken(Swatch.Neutral1, Shade.S400)
    val Neutral1_500 = SwatchColorToken(Swatch.Neutral1, Shade.S500)
    val Neutral1_700 = SwatchColorToken(Swatch.Neutral1, Shade.S700)
    val Neutral1_800 = SwatchColorToken(Swatch.Neutral1, Shade.S800)
    val Neutral1_900 = SwatchColorToken(Swatch.Neutral1, Shade.S900)

    val Neutral2_50 = SwatchColorToken(Swatch.Neutral2, Shade.S50)
    val Neutral2_100 = SwatchColorToken(Swatch.Neutral2, Shade.S100)
    val Neutral2_200 = SwatchColorToken(Swatch.Neutral2, Shade.S200)
    val Neutral2_300 = SwatchColorToken(Swatch.Neutral2, Shade.S300)
    val Neutral2_500 = SwatchColorToken(Swatch.Neutral2, Shade.S500)
    val Neutral2_600 = SwatchColorToken(Swatch.Neutral2, Shade.S600)
    val Neutral2_700 = SwatchColorToken(Swatch.Neutral2, Shade.S700)
    val Neutral2_800 = SwatchColorToken(Swatch.Neutral2, Shade.S800)
    val Neutral2_900 = SwatchColorToken(Swatch.Neutral2, Shade.S900)

    val Accent1_10 = SwatchColorToken(Swatch.Accent1, Shade.S10)
    val Accent1_50 = SwatchColorToken(Swatch.Accent1, Shade.S50)
    val Accent1_100 = SwatchColorToken(Swatch.Accent1, Shade.S100)
    val Accent1_200 = SwatchColorToken(Swatch.Accent1, Shade.S200)
    val Accent1_300 = SwatchColorToken(Swatch.Accent1, Shade.S300)
    val Accent1_400 = SwatchColorToken(Swatch.Accent1, Shade.S400)
    val Accent1_500 = SwatchColorToken(Swatch.Accent1, Shade.S500)
    val Accent1_600 = SwatchColorToken(Swatch.Accent1, Shade.S600)
    val Accent1_700 = SwatchColorToken(Swatch.Accent1, Shade.S700)
    val Accent1_800 = SwatchColorToken(Swatch.Accent1, Shade.S800)
    val Accent1_900 = SwatchColorToken(Swatch.Accent1, Shade.S900)

    val Accent2_10 = SwatchColorToken(Swatch.Accent2, Shade.S10)
    val Accent2_50 = SwatchColorToken(Swatch.Accent2, Shade.S50)
    val Accent2_100 = SwatchColorToken(Swatch.Accent2, Shade.S100)
    val Accent2_200 = SwatchColorToken(Swatch.Accent2, Shade.S200)
    val Accent2_300 = SwatchColorToken(Swatch.Accent2, Shade.S300)
    val Accent2_500 = SwatchColorToken(Swatch.Accent2, Shade.S500)
    val Accent2_600 = SwatchColorToken(Swatch.Accent2, Shade.S600)
    val Accent2_800 = SwatchColorToken(Swatch.Accent2, Shade.S800)

    val Accent3_10 = SwatchColorToken(Swatch.Accent3, Shade.S10)
    val Accent3_50 = SwatchColorToken(Swatch.Accent3, Shade.S50)
    val Accent3_100 = SwatchColorToken(Swatch.Accent3, Shade.S100)
    val Accent3_200 = SwatchColorToken(Swatch.Accent3, Shade.S200)
    val Accent3_400 = SwatchColorToken(Swatch.Accent3, Shade.S400)
    val Accent3_600 = SwatchColorToken(Swatch.Accent3, Shade.S600)
    val Accent3_800 = SwatchColorToken(Swatch.Accent3, Shade.S800)

    @JvmField val SearchResultSmallIcon = DayNightColorToken(Accent1_100, Accent2_800)

    @JvmField val SurfaceContainerHighest = DayNightColorToken(Neutral1_500.setLStar(90.0), PhosphorColorToken(0.1f))

    @JvmField val SurfaceContainerLow = DayNightColorToken(Neutral1_500.setLStar(96.0), PhosphorColorToken(0.05f))

    val Scrim = Neutral1_0
    val Shadow = Neutral1_0

    val SurfaceLight = Neutral1_500.setLStar(98.0)
    val SurfaceDark = PhosphorColorToken(0f)

    @JvmField val Surface = DayNightColorToken(SurfaceLight, SurfaceDark)

    val SurfaceVariantLight = Neutral2_100
    val SurfaceVariantDark = PhosphorColorToken(0.1f)

    @JvmField val SurfaceDimColor = DayNightColorToken(Neutral2_600.setLStar(87.0), PhosphorColorToken(0f))

    @JvmField val SurfaceBrightColor = DayNightColorToken(Neutral2_600.setLStar(98.0), PhosphorColorToken(0.1f))

    @JvmField val ColorAccent = DayNightColorToken(Accent1_600, PhosphorColorToken(1.0f))

    @JvmField val ColorBackground = DayNightColorToken(Neutral1_50, PhosphorColorToken(0f))

    @JvmField val ColorBackgroundFloating = DayNightColorToken(Neutral2_50, PhosphorColorToken(0.05f))

    @JvmField val ColorPrimary = DayNightColorToken(Neutral1_50, PhosphorColorToken(0f))

    @JvmField val TextColorPrimary = DayNightColorToken(Neutral1_900, PhosphorColorToken(1.0f))

    @JvmField val TextColorPrimaryInverse = TextColorPrimary.inverse()

    @JvmField val TextColorSecondary = DayNightColorToken(StaticColorToken(0xde000000), PhosphorColorToken(0.62f))

    @JvmField val AllAppsHeaderProtectionColor = DayNightColorToken(SurfaceContainerHighest, SurfaceContainerLow)

    @JvmField val AllAppsScrimColor = PhosphorColorToken(0f).setAlpha(.60f)

    @JvmField val AllAppsTabBackground = DayNightColorToken(Neutral1_100, PhosphorColorToken(0.1f))

    @JvmField val AllAppsTabBackgroundSelected = DayNightColorToken(Accent1_600, PhosphorColorToken(1.0f))

    @JvmField val FocusHighlight = DayNightColorToken(PhosphorColorToken(0.2f), PhosphorColorToken(0.2f))

    @JvmField val FocusHighlightBlur = DayNightColorToken(PhosphorColorToken(0.2f), PhosphorColorToken(0.2f))

    @JvmField val GroupHighlight = DayNightColorToken(PhosphorColorToken(0f), PhosphorColorToken(0f))

    @JvmField val GroupHighlightBlur = DayNightColorToken(PhosphorColorToken(0f), PhosphorColorToken(0f))

    @JvmField val OverviewScrimColor = DayNightColorToken(Neutral2_100.setLStar(87.0), Neutral1_800)

    @JvmField val OverviewScrimOverBlurColor = DayNightColorToken(
        StaticColorToken(0x80FFFFFF),
        StaticColorToken(0x80000000),
    )

    @JvmField val OverviewScrim = OverviewScrimColor
        .withPreferences { prefs ->
            val translucent = prefs.recentsTranslucentBackground.get()
            val translucentIntensity = prefs.recentsTranslucentBackgroundAlpha.get()
            if (translucent) setAlpha(translucentIntensity) else this
        }

    @JvmField val OverviewScrimOverBlur = OverviewScrimOverBlurColor
        .withPreferences { prefs ->
            val translucent = prefs.recentsTranslucentBackground.get()
            val translucentIntensity = prefs.recentsTranslucentBackgroundAlpha.get()
            if (translucent) setAlpha(translucentIntensity) else this
        }

    @JvmField val SearchboxHighlight = DayNightColorToken(Neutral2_600.setLStar(98.0), PhosphorColorToken(0.1f))

    @JvmField val SearchboxHighlightBlur = SearchboxHighlight.setAlpha(.54f)

    @JvmField val DotColor = StaticColorToken(0xFFFF5A45)

    @JvmField val FolderBackgroundColor = DayNightColorToken(Neutral1_50.setLStar(94.0), PhosphorColorToken(0.05f))

    @JvmField val FolderIconBorderColor = PhosphorColorToken(0.32f)

    @JvmField val FolderPaginationColor = DayNightColorToken(Accent1_600, Accent1_200)

    @JvmField val FolderPreviewColor = DayNightColorToken(Accent2_200, PhosphorColorToken(0.1f))

    @JvmField val PopupColorPrimary = DayNightColorToken(Accent2_50, PhosphorColorToken(0.07f))

    @JvmField val PopupColorSecondary = DayNightColorToken(Neutral2_100, PhosphorColorToken(0.05f))

    @JvmField val PopupColorTertiary = DayNightColorToken(Neutral2_300, PhosphorColorToken(0.32f))

    @JvmField val PopupShadeFirst = DayNightColorToken(PopupColorPrimary.setLStar(98.0), PopupColorPrimary.setLStar(20.0))

    @JvmField val PopupShadeSecond = DayNightColorToken(PopupColorPrimary.setLStar(95.0), PopupColorPrimary.setLStar(15.0))

    @JvmField val PopupShadeThird = DayNightColorToken(PopupColorPrimary.setLStar(90.0), PopupColorPrimary.setLStar(10.0))

    @JvmField val PopupArrow = PopupShadeFirst

    @JvmField val QsbIconTintPrimary = DayNightColorToken(Accent3_400, Accent3_100)

    @JvmField val QsbIconTintSecondary = DayNightColorToken(Accent1_500, Accent1_400)

    @JvmField val QsbIconTintTertiary = DayNightColorToken(Accent2_300, Accent2_10)

    @JvmField val QsbIconTintQuaternary = DayNightColorToken(Accent1_600, Accent1_200)

    @JvmField val WallpaperPopupScrim = Neutral1_900

    @JvmField val WidgetsPickerScrim = Scrim.setAlpha(.32f)

    @JvmField val AccentRippleColor = DayNightColorToken(Accent2_50, Accent1_300)

    @JvmField val WorkspaceAccentColor = DarkTextColorToken(Accent1_100, Accent1_900)

    @JvmField val DropTargetHoverTextColor = DarkTextColorToken(Accent1_900, Accent1_100)

    @JvmField val WidgetListRowColor = DayNightColorToken(Neutral1_10, Neutral2_800)

    @JvmField val PrimaryButton = DayNightColorToken(Accent1_600, PhosphorColorToken(1.0f))

    @JvmField val WidgetAddButtonBackgroundColor = PrimaryButton

    val SwitchThumbOn = Accent1_100
    val SwitchThumbOff = DayNightColorToken(Neutral2_300, Neutral1_400)
    val SwitchThumbDisabled = DayNightColorToken(Neutral2_100, Neutral1_700)

    val SwitchTrackOn = DayNightColorToken(Accent1_600, Accent2_500.setLStar(51.0))
    val SwitchTrackOff = DayNightColorToken(Neutral2_500.setLStar(45.0), Neutral1_700)

    @JvmField val PredictedPlateColor = Accent1_300

    // Material 3 Expressive
    @JvmField val ExpressiveAllApps = DayNightColorToken(Accent1_100, Accent1_800).setAlpha(0.5f)

    @JvmField val BottomSheetBackgroundColorBlurFallback = DayNightColorToken(Accent2_200, Accent2_800)

    @JvmField val shade_panel_fg_color = DayNightColorToken(
        Accent1_100.setAlpha(0.32f),
        Accent1_800.setAlpha(0.32f),
    )

    @JvmField val shade_panel_bg_color = Surface.setAlpha(0.32f)

    @JvmField val pageIndicatorDotColor = DayNightColorToken(
        Accent1_600,
        Accent1_500,
    )
}

@Composable
fun colorToken(token: ColorToken): Color {
    val context = LocalContext.current
    val intColor = token.resolveColor(context)
    return Color(intColor)
}
