package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ThemeLeftoverTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private val defaults = ThemeSpec(
        accent = ThemeColor.Default,
        background = ThemeColor.Default,
        font = ThemeFont.Default,
        pixelClock = true,
        showWallpaper = false,
        wallpaperDim = 0f,
        colorAsWallpaper = true,
        iconShape = "circle",
        animation = "monitor",
    )

    @Test fun nothingToAskWhenEverythingIsDefault() {
        assertTrue(ThemeSpecs.leftovers(ThemeSpec(accent = ThemeColor.Custom(1)), defaults, defaults).isEmpty())
    }

    @Test fun fontAndWallpaperOfAnOldThemeAreOffered() {
        val now = defaults.copy(font = ThemeFont.Google("Space Mono"), showWallpaper = true)
        val left = ThemeSpecs.leftovers(ThemeSpec(accent = ThemeColor.Custom(1)), now, defaults)
        assertEquals(listOf(ThemeElement.FONT, ThemeElement.WALLPAPER), left)
    }

    @Test fun whatTheNewThemeSetsIsNotAskedAbout() {
        val now = defaults.copy(font = ThemeFont.Google("Space Mono"), showWallpaper = true)
        val spec = ThemeSpec(font = ThemeFont.System("monospace"), wallpaper = ThemeWallpaper(ThemeImage.Url("https://a.b/c.png")))
        assertTrue(ThemeSpecs.leftovers(spec, now, defaults).isEmpty())
    }

    @Test fun anElementWithoutAKnownDefaultIsLeftAlone() {
        val now = defaults.copy(iconShape = "hexagon")
        assertTrue(ThemeSpecs.leftovers(ThemeSpec(), now, defaults.copy(iconShape = null)).isEmpty())
    }

    @Test fun customAnimationCountsAsAChange() {
        val now = defaults.copy(customAnimation = CustomAnimation.Particles())
        assertEquals(listOf(ThemeElement.ANIMATION), ThemeSpecs.leftovers(ThemeSpec(), now, defaults))
    }

    @Test fun aChangedGlancePanelIsOfferedBackUnlessTheThemeBringsIt() {
        val now = defaults.copy(glance = GlanceSpec(shortcuts = false))
        assertEquals(listOf(ThemeElement.GLANCE), ThemeSpecs.leftovers(ThemeSpec(accent = ThemeColor.Custom(1)), now, defaults))
        assertTrue(ThemeSpecs.leftovers(ThemeSpec(glance = GlanceSpec(chats = false)), now, defaults).isEmpty())
        assertTrue(ThemeSpecs.leftovers(ThemeSpec(), defaults, defaults).isEmpty())
    }

    @Test fun theGlancePanelAndTheLockScreenAreNotWordsOfTheCommand() {
        assertNull(ThemeElement.of("glance"))
        assertNull(ThemeElement.of("bloqueio"))
    }

    @Test fun imagesAThemePlacedAreOfferedBackUnlessTheNewThemeBringsOne() {
        val placed = listOf(ThemeElement.WALLPAPER, ThemeElement.LOCK)
        val plain = ThemeSpec(accent = ThemeColor.Custom(1))
        assertEquals(placed, ThemeSpecs.leftovers(plain, defaults, defaults, placed))
        val image = ThemeImage.Url("https://a.b/c.png")
        assertEquals(listOf(ThemeElement.LOCK), ThemeSpecs.leftovers(plain.copy(wallpaper = ThemeWallpaper(image)), defaults, defaults, placed))
        assertTrue(ThemeSpecs.leftovers(plain.copy(wallpaper = ThemeWallpaper(image, lockScreen = true)), defaults, defaults, placed).isEmpty())
        assertTrue(ThemeSpecs.leftovers(plain, defaults, defaults).isEmpty())
    }

    @Test fun defaultsAreAppliedOnlyToTheChosenElements() {
        val chosen = ThemeSpecs.defaultsFor(listOf(ThemeElement.FONT, ThemeElement.WALLPAPER), defaults)
        assertEquals(ThemeSpec(font = ThemeFont.Default, showWallpaper = false), chosen)
        assertNull(chosen.accent)
        val merged = chosen.overlaidWith(ThemeSpec(accent = ThemeColor.Custom(1)))
        assertEquals(ThemeColor.Custom(1), merged.accent)
        assertEquals(ThemeFont.Default, merged.font)
    }

    @Test fun savedThemesAreWholeAndSingleElementsAreNot() {
        val extras = CommandExtras(host = HostData(themes = listOf(ThemeSpec(name = "Noite", accent = ThemeColor.Custom(1)))))
        fun run(text: String) = CommandEngine.analyze(text, emptyList(), emptyList(), true, emptyList(), emptyList(), extras).action as ThemeAction.Apply
        assertTrue(run("tema noite").whole)
        assertFalse(run("tema destaque #fff").whole)
    }
}
