package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The help under the preview explains the command being typed. */
class CommandHelpTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private fun help(text: String, aliases: List<CommandAlias> = emptyList()) = CommandHelp.of(text, CustomActions.DEFAULTS, aliases)

    @Test fun builtInWords() {
        assertEquals("TIMER", help("timer")?.title)
        assertEquals("TIMER", help("timer 10")?.title)
        assertEquals("LANTERNA", help("flashlight")?.title)
        assertTrue(help("c 2+2")!!.text.contains("calc"))
        assertNotNull(help("t pão"))
        assertNotNull(help("!!"))
        assertNotNull(help("?"))
    }

    @Test fun nothingToExplain() {
        assertNull(help(""))
        assertNull(help("   "))
        assertNull(help("chrome"))
    }

    @Test fun chainsAndSchedules() {
        assertEquals("BRILHO", help("tema escuro; brilho 40")?.title)
        assertTrue(help("em 30m")!!.text.contains("carregar"))
        assertEquals("SILÊNCIO", help("às 22h: silencio liga")?.title)
    }

    @Test fun customLettersAndAliases() {
        val letter = CustomActions.DEFAULTS.first()
        assertTrue(help(letter.letter + " ")!!.text.contains(letter.usage))
        val alias = CommandAlias("noite", "tema escuro")
        val text = help("noite", listOf(alias))!!.text
        assertTrue(text.contains("tema escuro"))
        assertTrue(text.contains("inverter"))
        // An alias that names itself does not loop.
        assertNotNull(help("x", listOf(CommandAlias("x", "x"))))
    }

    @Test fun analysisCarriesHelp() {
        val analysis = CommandEngine.analyze("volume 30", emptyList(), emptyList(), true)
        assertEquals("VOLUME", analysis.help?.title)
    }
}
