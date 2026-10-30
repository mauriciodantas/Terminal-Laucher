package app.lawnchair.command

import org.junit.Assert.assertEquals
import app.lawnchair.util.ResourceTexts
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ThemeShapeTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private fun run(text: String) = CommandEngine.analyze(text, emptyList(), emptyList(), true, emptyList(), emptyList(), CommandExtras())

    @Test fun commandSetsTheShapes() {
        assertEquals(ThemeAction.Apply(ThemeSpec(iconShape = "hexagon")), run("tema shape hexagon").action)
        assertEquals(ThemeAction.Apply(ThemeSpec(folderShape = "squircle")), run("tema foldershape Squircle").action)
        assertNull(run("tema shape blob").action)
    }

    @Test fun shapesAreReadInAnyCaseAndUnknownOnesDropped() {
        val spec = ThemeSpecs.fromJson(org.json.JSONObject("""{"iconShape":"Rounded-Hexagon","folderShape":"blob"}"""))
        assertEquals("roundedhexagon", spec.iconShape)
        assertNull(spec.folderShape)
    }

    @Test fun shapesSurviveTheFileAndOverlay() {
        val spec = ThemeSpec(name = "x", iconShape = "squircle", folderShape = "circle")
        assertEquals(spec, ThemeSpecs.decode(ThemeSpecs.encode(spec)))
        assertEquals("hexagon", ThemeSpec(iconShape = "circle").overlaidWith(ThemeSpec(iconShape = "hexagon")).iconShape)
        assertEquals("circle", ThemeSpec(iconShape = "circle").overlaidWith(ThemeSpec()).iconShape)
        assertTrue(ThemeSpec().copy(iconShape = "circle").isEmpty.not())
    }

    @Test fun promptNamesTheShapes() {
        assertTrue(LauncherAiPrompts.THEME_RULES.contains("iconShape"))
        assertTrue(LauncherAiPrompts.THEME_RULES.contains("squircle"))
    }

    @Test fun guidePromptPrefersRealImagesFromTheCatalog() {
        assertTrue(LauncherAiPrompts.THEME_MASTER_PROMPT.contains(LauncherAiPrompts.IMAGE_SOURCES_URL))
        assertTrue(LauncherAiPrompts.THEME_MASTER_PROMPT.contains("\"wallpaper\": {\"url\""))
        assertTrue(LauncherAiPrompts.THEME_MASTER_PROMPT.contains("thumburl"))
        assertTrue(LauncherAiPrompts.THEME_MASTER_PROMPT.contains("crops it"))
        assertTrue(LauncherAiPrompts.THEME_MASTER_PROMPT.contains("must be dark where text goes"))
        // The in-app command keeps the SVG: its reply is read for drawings only and a model there may not browse.
        assertFalse(LauncherAiPrompts.THEME_RULES.contains(LauncherAiPrompts.IMAGE_SOURCES_URL))
        assertFalse(LauncherAiPrompts.CREATE_TAIL.contains(LauncherAiPrompts.IMAGE_SOURCES_URL))
    }

    @Test fun guidePromptKeepsTheImageSearchGoingWhenASourceFails() {
        val prompt = LauncherAiPrompts.THEME_MASTER_PROMPT
        assertTrue(prompt.contains("429"))
        assertTrue(prompt.contains("go to the next row of the catalog at once"))
        assertTrue(prompt.contains("at least three different sources"))
        assertTrue(prompt.contains("last resort"))
    }

    @Test fun promptsAskForOneComposedPaletteFromTheWallpaper() {
        assertTrue(LauncherAiPrompts.THEME_RULES.contains("COMPOSE THE THEME AS ONE PIECE"))
        assertTrue(LauncherAiPrompts.THEME_MASTER_PROMPT.contains("the picture wins"))
        assertTrue(LauncherAiPrompts.THEME_MASTER_PROMPT.contains("near-monochrome"))
    }

    @Test fun aLinkWallpaperOfTheGuideReplyIsRead() {
        val spec = ThemeSpecs.fromJson(org.json.JSONObject("""{"name":"x","wallpaper":{"url":"https://upload.wikimedia.org/a.jpg","lockScreen":true},"showWallpaper":true,"wallpaperDim":0.5}"""))
        assertEquals(ThemeImage.Url("https://upload.wikimedia.org/a.jpg"), spec.wallpaper?.image)
        assertTrue(spec.wallpaper?.lockScreen == true)
    }

    @Test fun aGuideReplyWithALinkWallpaperKeepsItAndBase64IsDropped() {
        val reply = """```json
{"name":"x","accent":"#6ef5aa","background":"#051419","wallpaper":{"url":"https://upload.wikimedia.org/a.jpg"},"lockWallpaper":{"url":"https://upload.wikimedia.org/b.jpg"},"note":"credit"}
```"""
        val (spec, note) = LauncherAiPrompts.theme(reply)!!
        assertEquals(ThemeImage.Url("https://upload.wikimedia.org/a.jpg"), spec.wallpaper?.image)
        assertEquals(ThemeImage.Url("https://upload.wikimedia.org/b.jpg"), spec.lockWallpaper)
        assertEquals(true, spec.showWallpaper)
        assertEquals("credit", note)
    }

    @Test fun glanceSurvivesTheFileAndTheOverlay() {
        val spec = ThemeSpec(
            name = "x",
            glance = GlanceSpec(show = false, animation = "sonar", lottie = GlanceLottie.Url("https://example.com/a.json"), tint = true, shortcuts = false, agenda = false, autoRotate = true),
        )
        assertEquals(spec, ThemeSpecs.decode(ThemeSpecs.encode(spec)))
        val json = GlanceLottie.Json("""{"layers":[]}""")
        assertEquals(GlanceSpec(show = true, animation = "wave", lottie = json), GlanceSpecs.fromJson(org.json.JSONObject("""{"show":"true","animation":"Wave","lottie":{"json":{"layers":[]}}}""")))
        val merged = ThemeSpec(glance = GlanceSpec(show = false, animation = "radar")).overlaidWith(ThemeSpec(glance = GlanceSpec(animation = "bars", chats = false)))
        assertEquals(GlanceSpec(show = false, animation = "bars", chats = false), merged.glance)
        assertEquals(GlanceSpec(show = false), ThemeSpec().overlaidWith(ThemeSpec(glance = GlanceSpec(show = false))).glance)
        assertTrue(ThemeSpec(glance = GlanceSpec(show = false)).isEmpty.not())
    }

    @Test fun glanceLookSurvivesTheFileAndTheOverlay() {
        val look = GlanceLook(preset = "centered", align = "end", radius = 8, titleBar = "outline", glow = false, statusRam = false, hiddenShortcuts = listOf("camera"))
        val spec = ThemeSpec(name = "x", glance = GlanceSpec(look = look))
        assertEquals(spec, ThemeSpecs.decode(ThemeSpecs.encode(spec)))
        val merged = ThemeSpec(glance = GlanceSpec(look = look)).overlaidWith(ThemeSpec(glance = GlanceSpec(look = GlanceLook(align = "start", dim = 50))))
        assertEquals(look.copy(align = "start", dim = 50), merged.glance?.look)
        assertTrue(GlanceSpec(look = look).items.any { it.startsWith("style centered") })
    }

    @Test fun glanceLookReadsOnlyWhatItUnderstands() {
        val read = GlanceSpecs.lookFromJson(org.json.JSONObject("""{"preset":"Disco","align":"CENTER","radius":99,"clockSize":"20","borderWidth":"x","hiddenShortcuts":["Camera","laser","camera"],"statusItems":{"ram":false}}"""))
        assertEquals(GlanceLook(align = "center", radius = 16, clockSize = 60, hiddenShortcuts = listOf("camera"), statusRam = false), read)
        assertNull(GlanceSpecs.lookFromJson(org.json.JSONObject("""{"preset":"x"}""")))
    }

    @Test fun promptsExplainTheGlanceLook() {
        listOf("preset", "clockFormat", "hiddenShortcuts", "statusItems", "radius", "urgent", "dim", "textSize").forEach {
            assertTrue(it, LauncherAiPrompts.THEME_RULES.contains(it))
        }
        GlanceSpecs.PRESETS.forEach { assertTrue(it, LauncherAiPrompts.THEME_RULES.contains(it)) }
    }

    @Test fun glanceReadsOnlyWhatItUnderstands() {
        val read = GlanceSpecs.fromJson(org.json.JSONObject("""{"animation":"disco","lottie":"file:///etc/passwd","show":"maybe","targets":{"weather":false}}"""))
        assertEquals(GlanceSpec(weather = false), read)
        assertNull(GlanceSpecs.fromJson(org.json.JSONObject("""{"animation":"none"}""").put("x", 1))?.animation?.takeIf { it != "off" })
        assertNull(GlanceSpecs.fromJson(org.json.JSONObject("""{"lottie":{"json":{"nolayers":1}}}""")))
        assertEquals(GlanceLottie.Url("https://example.com/a.json"), GlanceSpecs.fromJson(org.json.JSONObject("""{"lottie":"https://example.com/a.json"}"""))?.lottie)
    }

    @Test fun promptsExplainTheGlancePanel() {
        assertTrue(LauncherAiPrompts.THEME_RULES.contains("glance"))
        assertTrue(LauncherAiPrompts.THEME_RULES.contains("sonar"))
        assertTrue(LauncherAiPrompts.THEME_RULES.contains("Animation is optional"))
        assertTrue(LauncherAiPrompts.THEME_RULES.contains("never just because the field exists"))
        assertTrue(LauncherAiPrompts.THEME_MASTER_PROMPT.contains("glance.lottie"))
        assertFalse(LauncherAiPrompts.THEME_RULES.contains("glance.lottie"))
    }

    @Test fun aGuideReplyKeepsItsGlance() {
        val (spec, _) = LauncherAiPrompts.theme("""{"accent":"#6ef5aa","background":"#051419","glance":{"show":false}}""")!!
        assertEquals(GlanceSpec(show = false), spec.glance)
    }

    @Test fun aThemeThatOnlyHidesThePanelIsRead() {
        val (spec, _) = LauncherAiPrompts.theme("""{"name":"Sem painel","glance":{"show":false}}""")!!
        assertEquals(GlanceSpec(show = false), spec.glance)
    }
}
