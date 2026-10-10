package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The command words follow the app language, and the Portuguese and English words keep working. */
class CommandLanguageTest {

    private val apps = listOf(AppEntry("Camera", "pkg/Camera"))
    private val contacts = listOf(ContactEntry("Ana Souza", "111"))

    private fun action(typed: String) = CommandEngine.analyze(typed, apps, contacts, true).action

    @After
    fun backToPortuguese() = ResourceTexts.install()

    @Test fun englishWordsRunTheCommands() {
        ResourceTexts.installLanguage(null)
        assertEquals(CommandAction.OpenApp(apps[0]), action("open camera"))
        assertEquals(CommandAction.Call(contacts[0]), action("call ana"))
        assertEquals(CommandAction.SetAlarm(6, 30), action("alarm 0630"))
        assertEquals(CommandAction.Route("home"), action("route home"))
        assertTrue(CommandEngine.analyze("op", apps, contacts, true).suggestions.any { it.completion == "open " })
    }

    @Test fun portugueseWordsWorkInAnyLanguage() {
        ResourceTexts.installLanguage("values-de")
        assertEquals(CommandAction.OpenApp(apps[0]), action("abrir camera"))
        assertEquals(CommandAction.Call(contacts[0]), action("ligar ana"))
    }

    @Test fun localWordsMatchWithoutAccents() {
        ResourceTexts.installLanguage("values-de")
        assertEquals(CommandAction.OpenApp(apps[0]), action("öffnen camera"))
        assertEquals(CommandAction.OpenApp(apps[0]), action("offnen camera"))
        assertEquals("öffnen camera", CommandEngine.analyze("öffnen cam", apps, contacts, true).suggestions.first().completion)
    }

    @Test fun voiceUsesTheWordsOfTheLanguage() {
        ResourceTexts.installLanguage(null)
        assertEquals("call ana", VoiceCommand.normalize("phone ana"))
        assertEquals("call ana", VoiceCommand.normalize("call to ana"))
        assertEquals("open chrome", VoiceCommand.normalize("launch the chrome"))
        assertEquals("calc 12*8", VoiceCommand.normalize("calculate 12 times 8"))
        assertEquals("calc 1.5+2", VoiceCommand.normalize("calc 1 point 5 plus 2"))
        assertEquals("alarm 6:30", VoiceCommand.normalize("alarm at 6:30"))
        assertEquals("w ana", VoiceCommand.normalize("send message to ana"))
        assertEquals("chego logo", CommandEngine.cleanMessage("chego logo"))
        assertEquals("on my way", CommandEngine.cleanMessage("saying that on my way"))
    }

    @Test fun everyLanguageReservesItsWords() {
        ResourceTexts.installLanguage("values-fr")
        val taken = CustomActions.validateLetter("appeler", emptyList())
        assertEquals("\"appeler\" est déjà une commande du système", taken)
        assertEquals("\"open\" est déjà une commande du système", CustomActions.validateLetter("open", emptyList()))
    }

    @Test fun aliasesMayUseVowelSigns() {
        ResourceTexts.installLanguage("values-hi")
        assertNull(Aliases.validate("माँ", "कॉल maria", emptyList(), emptyList()))
    }
}
