package app.lawnchair.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandEngineTest {

    private val apps = listOf("Calendar", "Camera", "Chrome", "Clock", "Contacts", "Gmail", "Maps")
        .map { AppEntry(it, "pkg/$it") }
    private val contacts = listOf(
        ContactEntry("Ana Souza", "111"),
        ContactEntry("André Lima", "222"),
        ContactEntry("Beatriz Rocha", "333"),
        ContactEntry("Carlos Mota", "444"),
    )

    private fun run(text: String, granted: Boolean = true) =
        CommandEngine.analyze(text, apps, contacts, granted)

    // ---- command names and autocomplete ----

    @Test fun emptyTextListsTheCommands() {
        val a = run("")
        assertEquals(listOf("abrir ", "alarme ", "calc ", "ligar "), a.suggestions.map { it.completion })
        assertNull(a.action)
    }

    @Test fun partialNameCompletesTheCommand() {
        val a = run("ca")
        assertEquals("calc ", a.suggestions.first().completion)
        assertTrue(a.suggestions.any { it.completion == "abrir camera" })
        assertEquals("COMPLETE PARA VER O RESULTADO", a.preview)
    }

    @Test fun singleLetterCommandsAreLabelledAsShortcuts() {
        val a = run("t")
        assertEquals("ATALHO", a.suggestions.first { it.completion == "t " }.kind)
        assertEquals("COMANDO", run("ab").suggestions.first().kind)
    }

    @Test fun suggestionsAreLimited() {
        assertTrue(run("c").suggestions.size <= CommandEngine.MAX_SUGGESTIONS)
    }

    @Test fun noMatchOffersAWebSearch() {
        val a = run("zzz")
        assertTrue(a.suggestions.isEmpty())
        assertEquals(CommandAction.WebSearch("zzz"), a.action)
    }

    @Test fun leadingSpacesAreIgnored() {
        assertEquals("calc ", run("  ca").suggestions.first().completion)
    }

    @Test fun ghostIsTheMissingPartOfTheSelection() {
        val s = Suggestion("abrir camera", "Camera", "PROGRAMA")
        assertEquals("mera", CommandEngine.ghost("abrir ca", s))
        assertEquals("mera", CommandEngine.ghost("ABRIR CA", s))
        assertEquals("", CommandEngine.ghost("abrir camera", s))
        assertEquals("", CommandEngine.ghost("xyz", s))
        assertEquals("", CommandEngine.ghost("ab", null))
    }

    // ---- abrir ----

    @Test fun openMatchesByPrefix() {
        val a = run("abrir ca")
        assertEquals(listOf("Calendar", "Camera"), a.suggestions.map { it.label })
        assertEquals(CommandAction.OpenApp(apps[0]), a.action)
        assertEquals("CALENDAR", a.preview)
        assertEquals(Tone.OK, a.tone)
    }

    @Test fun openFallsBackToContains() {
        assertEquals(CommandAction.OpenApp(apps[5]), run("abrir mail").action)
    }

    @Test fun openWithoutArgumentOnlyListsApps() {
        val a = run("abrir ")
        assertEquals(CommandEngine.MAX_SUGGESTIONS, a.suggestions.size)
        assertNull(a.action)
        assertEquals("INFORME O PROGRAMA", a.preview)
    }

    @Test fun openUnknownIsAnError() {
        val a = run("abrir xyz")
        assertEquals("PROGRAMA NÃO ENCONTRADO", a.preview)
        assertEquals(Tone.ERROR, a.tone)
        assertNull(a.action)
    }

    // ---- ligar / w ----

    @Test fun callNeedsContactsAccess() {
        val a = run("ligar ana", granted = false)
        assertTrue(a.needsContacts)
        assertEquals(Tone.WARN, a.tone)
        assertNull(a.action)
    }

    @Test fun callMatchesNameOrAnyWord() {
        assertEquals(CommandAction.Call(contacts[0]), run("ligar ana").action)
        assertEquals(CommandAction.Call(contacts[0]), run("ligar sou").action)
        assertEquals("ligar ana souza", run("ligar an").suggestions.first().completion)
        assertEquals(2, run("ligar an").suggestions.size)
    }

    @Test fun messageUsesTheShortcutW() {
        assertEquals(CommandAction.Message(contacts[2]), run("w bea").action)
        assertEquals("MENSAGEM PARA", run("w bea").previewTitle)
    }

    @Test fun contactWithoutArgumentDoesNotCallAnyone() {
        assertNull(run("ligar ").action)
        assertEquals("INFORME O CONTATO", run("ligar ").preview)
    }

    @Test fun unknownContactIsAnError() {
        assertEquals("CONTATO NÃO ENCONTRADO", run("ligar zzz").preview)
        assertNull(run("ligar zzz").action)
    }

    // ---- alarme ----

    @Test fun alarmAcceptsTheCommonWaysToTypeATime() {
        listOf("0630", "630", "6:30", "06 30").forEach {
            assertEquals(it, CommandAction.SetAlarm(6, 30), run("alarme $it").action)
        }
        assertEquals(CommandAction.SetAlarm(7, 0), run("alarme 7").action)
        assertEquals(CommandAction.SetAlarm(23, 59), run("alarme 2359").action)
    }

    @Test fun alarmRejectsInvalidTimes() {
        listOf("2460", "2400", "0660", "12345", "").forEach {
            assertNull(it, run("alarme $it").action)
        }
        assertEquals("INFORME O HORÁRIO (EX.: 0630)", run("alarme ").preview)
    }

    @Test fun alarmPreviewShowsTheTime() {
        assertEquals("06:30 · PRÓXIMA OCORRÊNCIA", run("alarme 630").preview)
        assertEquals("06:30", run("alarme 630").suggestions.single().label)
    }

    @Test fun parseTimeHandlesEdges() {
        assertEquals(0 to 0, CommandEngine.parseTime("0"))
        assertEquals(null, CommandEngine.parseTime("abc"))
    }

    // ---- calc ----

    @Test fun calcShowsTheResult() {
        val a = run("calc 12*8")
        assertEquals("12*8 = 96", a.preview)
        assertEquals(CommandAction.Calc("12*8", "96"), a.action)
    }

    @Test fun cIsAnAliasOfCalc() {
        assertEquals(CommandAction.Calc("2+2", "4"), run("c 2+2").action)
    }

    @Test fun calcIncompleteHasNoAction() {
        assertEquals("EXPRESSÃO INCOMPLETA", run("calc 12*").preview)
        assertNull(run("calc 12*").action)
        assertEquals("DIGITE UMA CONTA (EX.: 12*8)", run("calc ").preview)
    }

    // ---- t / rota / unknown ----

    @Test fun taskNeedsATitle() {
        assertNull(run("t ").action)
        assertEquals(CommandAction.NewTask("comprar pão"), run("t comprar pão").action)
        assertEquals("COMPRAR PÃO", run("t comprar pão").preview)
    }

    @Test fun routeNeedsADestination() {
        assertNull(run("rota ").action)
        assertEquals(CommandAction.Route("casa"), run("rota casa").action)
    }

    @Test fun unknownCommandWithArgumentsSearchesTheWeb() {
        val a = run("foo bar")
        assertEquals(Tone.ERROR, a.tone)
        assertEquals(CommandAction.WebSearch("foo bar"), a.action)
    }

    // ---- history and keys ----

    @Test fun historyKeepsTheMostRecentWithoutRepeats() {
        var h = listOf<String>()
        h = CommandEngine.pushHistory(h, "calc 1+1")
        h = CommandEngine.pushHistory(h, "alarme 630")
        h = CommandEngine.pushHistory(h, "calc 1+1")
        assertEquals(listOf("calc 1+1", "alarme 630"), h)
    }

    @Test fun historyIsLimitedAndIgnoresBlank() {
        var h = listOf<String>()
        repeat(12) { h = CommandEngine.pushHistory(h, "c $it") }
        assertEquals(CommandEngine.MAX_HISTORY, h.size)
        assertEquals("c 11", h.first())
        assertEquals(h, CommandEngine.pushHistory(h, "   "))
    }

    @Test fun historyKeyWalksBackAndStops() {
        assertEquals(-1, CommandEngine.olderIndex(-1, 0))
        assertEquals(0, CommandEngine.olderIndex(-1, 3))
        assertEquals(2, CommandEngine.olderIndex(2, 3))
    }

    @Test fun nextSuggestionWraps() {
        assertEquals(1, CommandEngine.nextSuggestion(0, 3))
        assertEquals(0, CommandEngine.nextSuggestion(2, 3))
        assertEquals(0, CommandEngine.nextSuggestion(0, 0))
    }

    @Test fun everyRunnableCommandHasAnAction() {
        listOf("abrir camera", "ligar ana", "w ana", "alarme 630", "calc 1+1", "c 1+1", "t x", "rota x").forEach {
            assertFalse(it, run(it).action == null)
        }
    }
}
