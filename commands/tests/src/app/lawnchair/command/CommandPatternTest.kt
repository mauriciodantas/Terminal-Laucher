package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommandPatternTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private val tracking = CustomAction(
        "rastreio", "Correios", ActionKind.INTENT,
        "https://rastreamento.correios.com.br/app/index.php?objeto={text}",
        emptyList(), ArgKind.TEXT, pattern = "[A-Z]{2}\\d{9}BR",
    )

    private fun run(text: String, vararg custom: CustomAction) =
        CommandEngine.analyze(text, emptyList(), emptyList(), false, CustomActions.DEFAULTS + custom)

    @Test fun aShapedPatternIsAccepted() {
        assertNull(CommandPattern.validate(""))
        assertNull(CommandPattern.validate("[A-Z]{2}\\d{9}BR"))
        assertNull(CommandPattern.validate("(?i)voo [a-z]{2}\\d{3,4}"))
    }

    @Test fun aBrokenLongOrBroadPatternIsRefused() {
        assertEquals("Expressão regular inválida", CommandPattern.validate("([A-Z"))
        assertEquals("Use no máximo 200 caracteres", CommandPattern.validate("a".repeat(201)))
        listOf(".*", ".+", "\\w+", "[a-z ]*", "\\S*", "\\d+").forEach {
            assertNotNull(it, CommandPattern.validate(it))
        }
        assertEquals("", CommandPattern.usable(".*"))
        assertEquals("[A-Z]{2}\\d{9}BR", CommandPattern.usable("[A-Z]{2}\\d{9}BR"))
    }

    @Test fun aPatternThatBacktracksForeverIsNoMatch() {
        val slow = Regex("(a+)+b")
        val started = System.nanoTime()
        assertFalse(CommandPattern.matches(slow, "a".repeat(40) + "c"))
        assertTrue((System.nanoTime() - started) / 1_000_000 < 1000)
    }

    @Test fun textThePatternTakesRunsTheAction() {
        val a = run("AB123456789BR", tracking)
        val action = a.action as CommandAction.Custom
        assertEquals("rastreio", action.action.letter)
        assertEquals("AB123456789BR", action.text)
        assertTrue(a.intent.endsWith("objeto=AB123456789BR"))
        // The pattern matches the whole line only.
        assertFalse(run("codigo AB123456789BR", tracking).action is CommandAction.Custom)
        // The letter still works as before.
        assertEquals("AB123456789BR", (run("rastreio AB123456789BR", tracking).action as CommandAction.Custom).text)
    }

    @Test fun twoActionsThatTakeTheSameTextAreListed() {
        val other = tracking.copy(letter = "track", label = "17Track", template = "https://t.17track.net/#nums={text}")
        val a = run("AB123456789BR", tracking, other)
        assertNull(a.action)
        assertTrue(a.picksOnEnter)
        assertEquals(listOf("rastreio AB123456789BR", "track AB123456789BR"), a.suggestions.map { it.completion })
    }

    @Test fun thePatternIsSavedAndShared() {
        val back = CustomActionStore.decode(CustomActionStore.encode(listOf(tracking))).single()
        assertEquals(tracking, back)
        // A list saved before patterns existed reads with none.
        val old = """[{"letter":"x","label":"X","kind":"INTENT","template":"x://{text}","packages":[],"arg":"TEXT"}]"""
        assertEquals("", CustomActionStore.decode(old).single().pattern)
    }

    @Test fun theImportPreviewShowsThePattern() {
        val entry = PackRules.preview(emptyList(), emptyList(), CommandPack(listOf(tracking), emptyList())).actions.single()
        assertTrue(entry.detail.endsWith("\nTambém roda com texto digitado sozinho que bata com [A-Z]{2}\\d{9}BR"))
    }

    @Test fun anImportDropsAPatternThatCannotBeUsed() {
        val broad = tracking.copy(letter = "tudo", pattern = ".*")
        val merged = PackRules.merge(emptyList(), emptyList(), CommandPack(listOf(tracking, broad), emptyList()))
        assertEquals(listOf("[A-Z]{2}\\d{9}BR", ""), merged.actions.map { it.pattern })
    }
}
