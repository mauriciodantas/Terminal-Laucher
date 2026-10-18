package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The launcher's own pieces of the engine: "tema", and a theme or a widget pasted into the bar. */
class LauncherHostTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private fun run(text: String) = CommandEngine.analyze(text, emptyList(), emptyList(), true, emptyList(), emptyList(), CommandExtras())

    // ---- theme ----

    @Test fun themeListsItsOptions() {
        val a = run("tema ")
        assertEquals(
            listOf("tema inverter", "tema claro", "tema escuro", "tema sistema", "tema padrão"),
            a.suggestions.map { it.completion }.take(5),
        )
        // Then each element and what can be done with a whole theme.
        assertTrue(a.suggestions.any { it.completion == "tema destaque " })
        assertTrue(a.suggestions.any { it.completion == "tema salvar " })
        assertNull(a.action)
        assertEquals(Tone.IDLE, a.tone)
    }

    @Test fun themeRunsTheOptionTyped() {
        assertEquals(ThemeAction.Change(ThemeChange.INVERT), run("tema inverter").action)
        assertEquals(ThemeAction.Change(ThemeChange.SYSTEM), run("tema sistema").action)
        assertEquals("TEXTO ⇄ FUNDO", run("tema inverter").preview)
        // The English words always work.
        assertEquals(ThemeAction.Change(ThemeChange.DARK), run("theme dark").action)
        assertEquals(ThemeAction.Change(ThemeChange.LIGHT), run("tema light").action)
    }

    @Test fun themeNarrowsAndRejects() {
        assertEquals(listOf("tema escuro", "tema escurecer "), run("tema esc").suggestions.map { it.completion })
        assertNull(run("tema esc").action)
        assertEquals(Tone.ERROR, run("tema xyz").tone)
        assertNull(run("tema xyz").action)
    }


    @Test
    fun themeWithExplicitFormat() {
        val json = """
            {
              "format": "terminal-theme",
              "version": 1,
              "name": "Neon",
              "accent": "#00ffcc",
              "background": "#050505"
            }
        """.trimIndent()
        val result = JsonImport.classify(json)
        assertTrue(result is LauncherImport.Theme)
        val theme = (result as LauncherImport.Theme).spec
        assertEquals("Neon", theme.name)
        assertEquals(ThemeSpecs.hexColor("#00ffcc"), (theme.accent as ThemeColor.Custom).argb)
    }

    @Test
    fun themeFromAiOutput() {
        val json = """
            {
              "theme": {
                "name": "Matrix",
                "accent": "#00ff00",
                "background": "#000000"
              },
              "note": "Tema estilo Matrix"
            }
        """.trimIndent()
        val result = JsonImport.classify(json)
        assertTrue(result is LauncherImport.Theme)
        val theme = (result as LauncherImport.Theme).spec
        assertEquals("Matrix", theme.name)
    }

    @Test
    fun themeFromBareProperties() {
        val json = """
            {
              "accent": "#ff0077",
              "background": "#111111",
              "crtEffect": true
            }
        """.trimIndent()
        val result = JsonImport.classify(json)
        assertTrue(result is LauncherImport.Theme)
        val theme = (result as LauncherImport.Theme).spec
        assertEquals(true, theme.crtEffect)
        assertEquals(ThemeSpecs.hexColor("#ff0077"), (theme.accent as ThemeColor.Custom).argb)
    }

    @Test
    fun widgetFromLottieAnimation() {
        val json = """
            {
              "v": "5.7.4",
              "fr": 60,
              "ip": 0,
              "op": 120,
              "w": 500,
              "h": 500,
              "nm": "Heartbeat",
              "layers": []
            }
        """.trimIndent()
        val result = JsonImport.classify(json)
        assertTrue(result is LauncherImport.Widget)
        val widget = result as LauncherImport.Widget
        assertEquals("Heartbeat", widget.name)
    }

    @Test
    fun widgetFromExplicitFormat() {
        val json = """
            {
              "format": "terminal-widget",
              "version": 1,
              "kind": "lottie",
              "name": "Radar",
              "command": "w ana",
              "playback": "LOOP"
            }
        """.trimIndent()
        val result = JsonImport.classify(json)
        assertTrue(result is LauncherImport.Widget)
        val widget = result as LauncherImport.Widget
        assertEquals("Radar", widget.name)
    }

    @Test
    fun commandEngineAnalyzesBareJsonTheme() {
        val json = "{\"name\": \"Neon\", \"accent\": \"#00ff00\", \"background\": \"#000000\"}"
        val analysis = CommandEngine.analyze(json, emptyList(), emptyList(), false)
        assertEquals(Tone.OK, analysis.tone)
        assertTrue(analysis.action is ImportAction.Theme)
        assertEquals("IMPORTAR TEMA", analysis.previewTitle)
        assertEquals(1, analysis.suggestions.size)
        assertEquals("IMPORTAR", analysis.suggestions.first().kind)
    }

    @Test
    fun commandEngineAnalyzesBareJsonWidget() {
        val json = "{\"v\": \"5.5.0\", \"nm\": \"Clock\", \"fr\": 30, \"ip\": 0, \"op\": 60, \"layers\": []}"
        val analysis = CommandEngine.analyze(json, emptyList(), emptyList(), false)
        assertEquals(Tone.OK, analysis.tone)
        assertTrue(analysis.action is ImportAction.Widget)
        assertEquals("IMPORTAR WIDGET", analysis.previewTitle)
        assertEquals("Clock", (analysis.action as ImportAction.Widget).name)
    }

    @Test
    fun commandEngineAnalyzesImportKeyword() {
        val json = "importar {\"name\": \"Retro\", \"accent\": \"#ffb000\", \"background\": \"#101010\"}"
        val analysis = CommandEngine.analyze(json, emptyList(), emptyList(), false)
        assertEquals(Tone.OK, analysis.tone)
        assertTrue(analysis.action is ImportAction.Theme)
        assertEquals("IMPORTAR TEMA", analysis.previewTitle)
    }
}
