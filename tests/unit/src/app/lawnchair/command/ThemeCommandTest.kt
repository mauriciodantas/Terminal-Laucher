package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** "tema" with each element, saved and ready-made themes, the .tthm file and "ia tema". */
class ThemeCommandTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private val green = ThemeColor.Custom(0xFF33FF66.toInt())
    private val black = ThemeColor.Custom(0xFF000000.toInt())
    private val current = ThemeSpec(
        accent = green,
        background = black,
        outline = ThemeColor.Default,
        dock = ThemeColor.Default,
        folder = ThemeColor.Default,
        followSystem = false,
        font = ThemeFont.Default,
        pixelClock = true,
        iconEffect = true,
        widgetEffect = true,
        widgetIntensity = 1f,
        crtEffect = false,
        crtIntensity = 1f,
        showWallpaper = false,
        wallpaperDim = 0.5f,
        colorAsWallpaper = false,
    )
    private val night = ThemeSpec(name = "Noite", accent = ThemeColor.Custom(0xFFFFB000.toInt()), background = ThemeColor.Custom(0xFF101020.toInt()))
    private val gameBoy = ThemeSpec(name = "Game Boy", accent = ThemeColor.Custom(0xFF0F380F.toInt()), background = ThemeColor.Custom(0xFF9BBC0F.toInt()))
    private val extras = CommandExtras(
        theme = current,
        themes = listOf(night),
        themePresets = listOf(gameBoy),
        fonts = listOf("Space Mono", "Space Grotesk", "Roboto Mono"),
    )

    private fun run(text: String, x: CommandExtras = extras) = CommandEngine.analyze(text, emptyList(), emptyList(), true, emptyList(), emptyList(), x)

    // ---- elements ----

    @Test fun colorsOfEachElement() {
        assertEquals(CommandAction.ThemeApply(ThemeSpec(accent = ThemeColor.Custom(0xFFFFB000.toInt()))), run("tema destaque #ffb000").action)
        assertEquals(CommandAction.ThemeApply(ThemeSpec(dock = ThemeColor.Custom(0xFF112233.toInt()))), run("tema dock #123").action)
        assertEquals(CommandAction.ThemeApply(ThemeSpec(folder = ThemeColor.Default)), run("tema pasta padrão").action)
        // The English words always work.
        assertEquals(CommandAction.ThemeApply(ThemeSpec(outline = ThemeColor.Custom(0xFFFFFFFF.toInt()))), run("theme outline ffffff").action)
        assertEquals(Tone.ERROR, run("tema fundo azulado").tone)
        assertNull(run("tema fundo azulado").action)
    }

    @Test fun refusesTextThatCannotBeRead() {
        // Green text over a background of nearly the same green.
        val a = run("tema fundo #30f060")
        assertEquals(Tone.ERROR, a.tone)
        assertNull(a.action)
        // Readable, but not comfortably: it runs with a warning.
        val weak = run("tema fundo #808080")
        assertEquals(Tone.WARN, weak.tone)
        assertNotNull(weak.action)
    }

    @Test fun fontsFromTheList() {
        assertEquals(CommandAction.ThemeApply(ThemeSpec(font = ThemeFont.Google("Space Mono"))), run("tema fonte space mono").action)
        assertEquals(CommandAction.ThemeApply(ThemeSpec(font = ThemeFont.System("monospace"))), run("tema fonte monospace").action)
        assertEquals(CommandAction.ThemeApply(ThemeSpec(font = ThemeFont.Default)), run("tema fonte padrao").action)
        val partial = run("tema fonte space")
        assertNull(partial.action)
        assertEquals(listOf("tema fonte Space Mono", "tema fonte Space Grotesk"), partial.suggestions.map { it.completion })
        assertEquals(Tone.ERROR, run("tema fonte xyzzy").tone)
    }

    @Test fun switchesEffectsAndPercentages() {
        assertEquals(CommandAction.ThemeApply(ThemeSpec(pixelClock = false)), run("tema relogio off").action)
        assertEquals(CommandAction.ThemeApply(ThemeSpec(crtEffect = true, crtIntensity = 0.4f)), run("tema crt 40%").action)
        assertEquals(CommandAction.ThemeApply(ThemeSpec(widgetEffect = false)), run("tema widgets 0").action)
        assertEquals(CommandAction.ThemeApply(ThemeSpec(wallpaperDim = 0.7f)), run("tema escurecer 70").action)
        // Alone, a switch flips what is on screen.
        assertEquals(CommandAction.ThemeApply(ThemeSpec(crtEffect = true)), run("tema crt").action)
        assertEquals(CommandAction.ThemeApply(ThemeSpec(showWallpaper = true)), run("theme wallpaper").action)
        assertEquals(Tone.ERROR, run("tema escurecer 300").tone)
    }

    // ---- whole themes ----

    @Test fun themesByName() {
        assertEquals(CommandAction.ThemeApply(night), run("tema noite").action)
        assertEquals(CommandAction.ThemeApply(gameBoy), run("tema game boy").action)
        assertEquals(listOf("tema Game Boy"), run("tema gam").suggestions.map { it.completion })
        assertEquals(Tone.ERROR, run("tema qualquer coisa").tone)
        // The modes still come first.
        assertEquals(CommandAction.Theme(ThemeChange.RESET), run("tema padrão").action)
    }

    @Test fun saveShareAndDelete() {
        assertEquals(CommandAction.ThemeSave("Praia"), run("tema salvar Praia").action)
        assertEquals(Tone.WARN, run("tema salvar noite").tone)
        // A ready-made look keeps its name.
        assertNull(run("tema salvar game boy").action)
        assertEquals(CommandAction.ThemeShare(""), run("tema compartilhar").action)
        assertEquals(CommandAction.ThemeShare("Game Boy"), run("tema compartilhar game boy").action)
        assertEquals(CommandAction.ThemeDelete("Noite"), run("tema apagar noite").action)
        assertNull(run("tema apagar game boy").action)
    }

    // ---- the file ----

    @Test fun fileRoundTrip() {
        val full = current.copy(name = "Verde", font = ThemeFont.Google("Space Mono"), crtIntensity = 0.4f)
        assertEquals(full, ThemeSpecs.decode(ThemeSpecs.encode(full)))
        assertEquals(night, ThemeSpecs.decode(ThemeSpecs.encode(night)))
        assertEquals("Verde.tthm", ThemeSpecs.fileName(full))
    }

    @Test fun strayFilesAreNotThemes() {
        assertTrue(runCatching { ThemeSpecs.decode("""{"format":"terminal-commands"}""") }.isFailure)
        assertTrue(runCatching { ThemeSpecs.decode("""{"format":"terminal-theme","version":99}""") }.isFailure)
        // Broken values are left out instead of failing the theme; numbers are kept in range.
        val spec = ThemeSpecs.decode("""{"format":"terminal-theme","accent":"red","background":"#000","crtIntensity":250,"font":{"kind":"google","family":"<b>"}}""")
        assertEquals(ThemeSpec(background = black, crtIntensity = 1f), spec)
    }

    @Test fun savedThemesByName() {
        val list = ThemeSpecs.upsert(listOf(night, gameBoy), night.copy(accent = green))
        assertEquals(2, list.size)
        assertEquals(green, list.first().accent)
        assertEquals(listOf(gameBoy), ThemeSpecs.remove(list, "NOITE"))
        assertEquals(list, ThemeSpecs.decodeList(ThemeSpecs.encodeList(list)))
    }

    @Test fun contrast() {
        assertEquals(21.0, ThemeSpecs.contrast(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0.01)
        assertTrue(ThemeSpecs.unreadable(ThemeSpec(accent = black), ThemeSpec(background = black)))
        assertFalse(ThemeSpecs.unreadable(ThemeSpec(accent = green), null))
    }

    // ---- ia tema ----

    @Test fun aiTheme() {
        val x = CommandExtras(ai = "Claude · claude-opus-5-5")
        assertEquals(CommandAction.Ai(AiMode.THEME, "neon"), run("ia tema neon", x).action)
        // Enter alone asks for a surprise.
        assertEquals(CommandAction.Ai(AiMode.THEME, ""), run("ia tema", x).action)
        assertTrue(AiPrompts.system(AiMode.THEME, AiContext(theme = current)).contains("CURRENT THEME"))
        // Asking "criar" for a theme is a theme, in either language.
        assertEquals(CommandAction.Ai(AiMode.THEME, "de halloween"), run("ia criar tema de halloween", x).action)
        assertEquals(CommandAction.Ai(AiMode.THEME, "for halloween"), run("ai create theme for halloween", x).action)
        assertEquals(CommandAction.Ai(AiMode.CREATE, "busca no imdb"), run("ia criar busca no imdb", x).action)
        // Typed some other way, "criar" still tells the AI how to send a theme.
        assertTrue(AiPrompts.system(AiMode.CREATE, AiContext()).contains("\"theme\""))
    }

    @Test fun aiThemeReply() {
        val (spec, note) = AiPrompts.theme(
            "Here it is:\n```json\n{\"name\": \"Neon 80\", \"accent\": \"#ff2bd6\", \"background\": \"#0b0221\", " +
                "\"font\": {\"kind\": \"google\", \"family\": \"VT323\"}, \"crtEffect\": true, \"crtIntensity\": 0.5, \"note\": \"Rosa sobre roxo.\"}\n```",
        )!!
        assertEquals("Neon 80", spec.name)
        assertEquals(ThemeFont.Google("VT323"), spec.font)
        assertEquals(0.5f, spec.crtIntensity)
        assertEquals("Rosa sobre roxo.", note)
        // Unreadable or empty replies are not themes.
        assertNull(AiPrompts.theme("{\"accent\": \"#000000\", \"background\": \"#010101\"}"))
        assertNull(AiPrompts.theme("{\"note\": \"no\"}"))
        assertNull(AiPrompts.theme("sorry"))
        // From "criar", the theme comes wrapped; commands do not count as one.
        val created = AiPrompts.createdTheme("{\"theme\": {\"name\": \"Halloween\", \"accent\": \"#ff7518\", \"background\": \"#0e0714\"}, \"note\": \"Abóbora.\"}")!!
        assertEquals("Halloween", created.first.name)
        assertEquals("Abóbora.", created.second)
        assertNull(AiPrompts.createdTheme("{\"aliases\": [{\"name\": \"halloween\", \"expansion\": \"theme dark\"}]}"))
    }
}
