package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class VoiceCommandTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private fun n(s: String) = VoiceCommand.normalize(s)

    @Test fun synonymsBecomeTheCommands() {
        assertEquals("ligar ana", n("chamar ana"))
        assertEquals("ligar ana", n("Ligar Ana"))
        assertEquals("abrir chrome", n("abre chrome"))
        assertEquals("t comprar pão", n("lembrar comprar pão"))
        assertEquals("rota casa", n("navegar casa"))
    }

    @Test fun fillerWordsAreDropped() {
        assertEquals("ligar ana", n("ligar para ana"))
        assertEquals("ligar ana souza", n("ligar para a ana souza"))
        assertEquals("abrir chrome", n("abrir o chrome"))
        assertEquals("abrir câmera", n("abrir o aplicativo câmera"))
    }

    @Test fun messageCommandsGoToWhatsapp() {
        assertEquals("w ana", n("mensagem para ana"))
        assertEquals("w ana", n("whatsapp ana"))
        assertEquals("w ana", n("enviar mensagem para a ana"))
    }

    @Test fun alarmDropsTheLeadingPreposition() {
        assertEquals("alarme 6:30", n("alarme às 6:30"))
        assertEquals("alarme 0630", n("despertador para 0630"))
    }

    @Test fun mathWordsBecomeSymbols() {
        assertEquals("calc 12*8", n("calcular 12 vezes 8"))
        assertEquals("calc 10/2", n("calcular 10 dividido por 2"))
        assertEquals("calc 5+3-1", n("calc 5 mais 3 menos 1"))
        assertEquals("calc 50%", n("conta 50 por cento"))
        assertEquals("calc 1,5*2", n("calcular 1 vírgula 5 vezes 2"))
    }

    @Test fun unknownSentencesAreKept() {
        assertEquals("previsão do tempo", n("Previsão do tempo"))
        assertEquals("", n("   "))
    }

    @Test fun aBareCommandKeepsTheSpaceForAutocomplete() {
        assertEquals("ligar ", n("ligar"))
    }

    @Test fun spokenCommandsBecomeRunnableActions() {
        val apps = listOf(AppEntry("Camera", "pkg/Camera"))
        val contacts = listOf(ContactEntry("Ana Souza", "111"))
        fun action(spoken: String) =
            CommandEngine.analyze(n(spoken), apps, contacts, true).action

        assertEquals(CommandAction.OpenApp(apps[0]), action("abrir o camera"))
        assertEquals(CommandAction.Call(contacts[0]), action("chamar a ana"))
        assertEquals(CommandAction.Custom(CustomActions.WHATSAPP, contacts[0]), action("mensagem para ana"))
        assertEquals(CommandAction.SetAlarm(6, 30), action("alarme às 6:30"))
        assertEquals(CommandAction.Calc("12*8", "96"), action("calcular 12 vezes 8"))
        // Incomplete or unknown speech does not run anything.
        assertEquals(null, action("ligar"))
        assertEquals(null, action("abrir xyz"))
    }

    @Test fun spokenMessageCarriesItsText() {
        val contacts = listOf(ContactEntry("Ana Souza", "111"))
        val a = CommandEngine.analyze(n("enviar mensagem para a ana dizendo que chego logo"), emptyList(), contacts, true)
        assertEquals(CommandAction.Custom(CustomActions.WHATSAPP, contacts[0], "chego logo"), a.action)
    }
}
