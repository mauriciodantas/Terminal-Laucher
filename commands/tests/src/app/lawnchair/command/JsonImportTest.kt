package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JsonImportTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    @Test
    fun nonJsonReturnsNull() {
        assertNull(JsonImport.classify("hello world"))
        assertNull(JsonImport.classify("calc 2+2"))
        assertNull(JsonImport.classify(""))
    }

    @Test
    fun incompleteJsonReturnsInvalid() {
        val result = JsonImport.classify("{\"name\": \"test\"")
        assertTrue(result is JsonImportTarget.Invalid)
        assertTrue((result as JsonImportTarget.Invalid).incomplete)
    }

    @Test
    fun unknownJsonStructureReturnsInvalid() {
        val result = JsonImport.classify("{\"foo\": \"bar\", \"count\": 123}")
        assertTrue(result is JsonImportTarget.Invalid)
        assertFalse((result as JsonImportTarget.Invalid).incomplete)
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
        assertTrue(result is JsonImportTarget.Theme)
        val theme = (result as JsonImportTarget.Theme).spec
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
        assertTrue(result is JsonImportTarget.Theme)
        val theme = (result as JsonImportTarget.Theme).spec
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
        assertTrue(result is JsonImportTarget.Theme)
        val theme = (result as JsonImportTarget.Theme).spec
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
        assertTrue(result is JsonImportTarget.Widget)
        val widget = result as JsonImportTarget.Widget
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
        assertTrue(result is JsonImportTarget.Widget)
        val widget = result as JsonImportTarget.Widget
        assertEquals("Radar", widget.name)
    }

    @Test
    fun commandsFromExplicitFormat() {
        val json = """
            {
              "format": "terminal-commands",
              "version": 1,
              "actions": [
                {
                  "letter": "g",
                  "label": "Github",
                  "kind": "INTENT",
                  "template": "https://github.com/{text}",
                  "packages": ["com.github.android"],
                  "arg": "TEXT",
                  "action": "android.intent.action.VIEW"
                }
              ],
              "aliases": [
                {
                  "name": "gh",
                  "expansion": "g lawnchair"
                }
              ]
            }
        """.trimIndent()
        val result = JsonImport.classify(json)
        assertTrue(result is JsonImportTarget.Commands)
        val pack = (result as JsonImportTarget.Commands).pack
        assertEquals(1, pack.actions.size)
        assertEquals("g", pack.actions.first().letter)
        assertEquals(1, pack.aliases.size)
        assertEquals("gh", pack.aliases.first().name)
    }

    @Test
    fun commandsFromSingleAction() {
        val json = """
            {
              "letter": "yt",
              "label": "YouTube",
              "kind": "INTENT",
              "template": "https://youtube.com/results?search_query={text}",
              "packages": ["com.google.android.youtube"],
              "arg": "TEXT"
            }
        """.trimIndent()
        val result = JsonImport.classify(json)
        assertTrue(result is JsonImportTarget.Commands)
        val pack = (result as JsonImportTarget.Commands).pack
        assertEquals(1, pack.actions.size)
        assertEquals("yt", pack.actions.first().letter)
    }

    @Test
    fun commandsFromSingleAlias() {
        val json = """
            {
              "name": "dormir",
              "expansion": "silencio on; alarme 0700"
            }
        """.trimIndent()
        val result = JsonImport.classify(json)
        assertTrue(result is JsonImportTarget.Commands)
        val pack = (result as JsonImportTarget.Commands).pack
        assertEquals(1, pack.aliases.size)
        assertEquals("dormir", pack.aliases.first().name)
        assertEquals("silencio on; alarme 0700", pack.aliases.first().expansion)
    }

    @Test
    fun commandEngineAnalyzesBareJsonTheme() {
        val json = "{\"name\": \"Neon\", \"accent\": \"#00ff00\", \"background\": \"#000000\"}"
        val analysis = CommandEngine.analyze(json, emptyList(), emptyList(), false)
        assertEquals(Tone.OK, analysis.tone)
        assertTrue(analysis.action is CommandAction.ImportTheme)
        assertEquals("IMPORTAR TEMA", analysis.previewTitle)
        assertEquals(1, analysis.suggestions.size)
        assertEquals("IMPORTAR", analysis.suggestions.first().kind)
    }

    @Test
    fun commandEngineAnalyzesBareJsonWidget() {
        val json = "{\"v\": \"5.5.0\", \"nm\": \"Clock\", \"fr\": 30, \"ip\": 0, \"op\": 60, \"layers\": []}"
        val analysis = CommandEngine.analyze(json, emptyList(), emptyList(), false)
        assertEquals(Tone.OK, analysis.tone)
        assertTrue(analysis.action is CommandAction.ImportWidget)
        assertEquals("IMPORTAR WIDGET", analysis.previewTitle)
        assertEquals("Clock", (analysis.action as CommandAction.ImportWidget).name)
    }

    @Test
    fun commandEngineAnalyzesBareJsonCommands() {
        val json = "{\"actions\": [{\"letter\": \"x\", \"label\": \"X\", \"kind\": \"INTENT\", \"template\": \"https://x.com\", \"packages\": [], \"arg\": \"NONE\"}]}"
        val analysis = CommandEngine.analyze(json, emptyList(), emptyList(), false)
        assertEquals(Tone.OK, analysis.tone)
        assertTrue(analysis.action is CommandAction.ImportCommands)
        assertEquals("IMPORTAR COMANDOS", analysis.previewTitle)
    }

    @Test
    fun commandEngineAnalyzesImportKeyword() {
        val json = "importar {\"name\": \"Retro\", \"accent\": \"#ffb000\", \"background\": \"#101010\"}"
        val analysis = CommandEngine.analyze(json, emptyList(), emptyList(), false)
        assertEquals(Tone.OK, analysis.tone)
        assertTrue(analysis.action is CommandAction.ImportTheme)
        assertEquals("IMPORTAR TEMA", analysis.previewTitle)
    }

    @Test
    fun jsonWithSemicolonsNotSplitAsChain() {
        val json = "{\"name\": \"gh\", \"expansion\": \"theme dark; dnd on\"}"
        val parts = CommandEngine.splitChain(json)
        assertEquals(1, parts.size)
        assertEquals(json, parts.first())
    }
}
