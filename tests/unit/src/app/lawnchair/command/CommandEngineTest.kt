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
        assertEquals(CommandAction.Custom(CustomActions.WHATSAPP, contacts[2]), run("w bea").action)
        assertEquals("WHATSAPP PARA", run("w bea").previewTitle)
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

    @Test fun pickingAFinishedSuggestionIsRunnableButACommandNameIsNot() {
        listOf("abrir ca", "ligar an", "alarme 630").forEach { typed ->
            run(typed).suggestions.forEach {
                assertFalse(it.completion, run(it.completion).action == null)
            }
        }
        // "calc " only completes the command name, there is nothing to run yet.
        assertNull(run("calc ").action)
        assertNull(run("abrir ").action)
        run("ca").suggestions.filter { it.kind == "COMANDO" }.forEach { assertNull(it.completion, run(it.completion).action) }
    }

    // ---- the message of "w" ----

    @Test fun messageTextFollowsTheContactName() {
        val a = run("w ana chego em 10 min")
        assertEquals(CommandAction.Custom(CustomActions.WHATSAPP, contacts[0], "chego em 10 min"), a.action)
        assertEquals("ANA SOUZA · “chego em 10 min”", a.preview)
    }

    @Test fun fullNameThenMessage() {
        assertEquals(CommandAction.Custom(CustomActions.WHATSAPP, contacts[0], "oi"), run("w ana souza oi").action)
    }

    @Test fun colonSeparatesNameFromMessage() {
        assertEquals(CommandAction.Custom(CustomActions.WHATSAPP, contacts[0], "sou eu"), run("w ana: sou eu").action)
    }

    @Test fun suggestionsKeepTheMessage() {
        val s = run("w an oi tudo bem").suggestions
        assertEquals(listOf("w ana souza oi tudo bem", "w andré lima oi tudo bem"), s.map { it.completion })
    }

    @Test fun pickingASuggestionKeepsTheMessageRunnable() {
        val pick = run("w an oi").suggestions.first().completion
        assertEquals(CommandAction.Custom(CustomActions.WHATSAPP, contacts[0], "oi"), run(pick).action)
    }

    @Test fun spokenLeadInIsDropped() {
        assertEquals("chego logo", CommandEngine.cleanMessage("dizendo que chego logo"))
        assertEquals("oi", CommandEngine.cleanMessage("falando oi"))
        assertEquals("que horas?", CommandEngine.cleanMessage("que horas?"))
        assertEquals(CommandAction.Custom(CustomActions.WHATSAPP, contacts[0], "chego logo"), run("w ana dizendo que chego logo").action)
    }

    @Test fun splitPrefersTheLongestName() {
        assertEquals("ana souza" to "oi", CommandEngine.splitNameAndMessage("ana souza oi", contacts))
        assertEquals("ana" to "oi tudo", CommandEngine.splitNameAndMessage("ana oi tudo", contacts))
        assertEquals("zzz" to "", CommandEngine.splitNameAndMessage("zzz", contacts))
    }

    @Test fun messageWithoutAContactIsNotRunnable() {
        assertNull(run("w zzz oi").action)
        assertNull(run("w ").action)
        assertTrue(run("w ana", granted = false).needsContacts)
    }

    // ---- ações personalizadas ----

    private fun runWith(text: String, vararg actions: CustomAction) =
        CommandEngine.analyze(text, apps, contacts, true, actions.toList())

    private val youtube = CustomAction(
        "yt", "YouTube", ActionKind.INTENT, "https://youtube.com/results?search_query={text}",
        emptyList(), ArgKind.TEXT,
    )
    private val flashlight = CustomAction(
        "f", "Lanterna", ActionKind.SHORTCUT, "torch", listOf("pkg"), ArgKind.NONE,
    )

    @Test fun customLetterAppearsInTheCommandList() {
        val s = runWith("y", youtube).suggestions
        assertEquals("yt ", s.first().completion)
    }

    @Test fun customTextActionTakesTheRest() {
        assertEquals(CommandAction.Custom(youtube, text = "gatos"), runWith("yt gatos", youtube).action)
        assertNull(runWith("yt ", youtube).action)
    }

    @Test fun customActionWithoutArgumentRunsOnTheBareLetter() {
        assertEquals(CommandAction.Custom(flashlight), runWith("f", flashlight).action)
    }

    @Test fun removedPresetStopsAnswering() {
        assertNull(runWith("w ana oi").action?.takeIf { it is CommandAction.Custom })
    }

    @Test fun contactActionUsesItsOwnLetterAndTitle() {
        val tg = CustomAction("tg", "Telegram", ActionKind.INTENT, "tg://resolve?phone={number}", emptyList(), ArgKind.CONTACT)
        val a = runWith("tg bea", tg)
        assertEquals(CommandAction.Custom(tg, contacts[2]), a.action)
        assertEquals("TELEGRAM PARA", a.previewTitle)
    }

    @Test fun letterValidation() {
        val existing = listOf(CustomActions.WHATSAPP)
        assertNull(CustomActions.validateLetter("x", existing))
        assertEquals("\"w\" já está em uso", CustomActions.validateLetter("w", existing))
        assertNull(CustomActions.validateLetter("w", existing, ignore = CustomActions.WHATSAPP))
        assertEquals("\"calc\" já é um comando do sistema", CustomActions.validateLetter("calc", existing))
        assertEquals("Informe uma letra", CustomActions.validateLetter(" ", existing))
    }

    @Test fun probesAreNotBoundToALetterYet() {
        assertTrue(CustomActions.PROBES.all { it.letter.isEmpty() && it.packages.isEmpty() })
        assertTrue(CustomActions.PROBES.any { it.textExtra != null && it.template.isEmpty() })
    }

    @Test fun importKeepsWhatTheUserHasAndSkipsConflicts() {
        val tg = CustomAction("TG", "Telegram", ActionKind.INTENT, "tg://x", emptyList(), ArgKind.CONTACT)
        val clash = CustomAction("w", "Outro", ActionKind.INTENT, "x://", emptyList(), ArgKind.TEXT)
        val reserved = CustomAction("calc", "Calc", ActionKind.INTENT, "x://", emptyList(), ArgKind.TEXT)
        val (list, skipped) = CustomActions.merge(listOf(CustomActions.WHATSAPP), listOf(tg, clash, reserved, tg))
        assertEquals(listOf("w", "tg"), list.map { it.letter })
        assertEquals(3, skipped)
        assertEquals(CustomActions.WHATSAPP, list.first())
    }
}

class AliasTest {

    private val apps = listOf("Chrome", "Maps").map { AppEntry(it, "pkg/$it") }
    private val contacts = listOf(ContactEntry("Maria Souza", "111"), ContactEntry("Ana Lima", "222"))
    private val aliases = listOf(
        CommandAlias("mae", "ligar maria"),
        CommandAlias("z", "w ana"),
        CommandAlias("net", "abrir chrome"),
    )

    private fun run(text: String) = CommandEngine.analyze(text, apps, contacts, true, CustomActions.DEFAULTS, aliases)

    @Test fun anAliasTypedInFullRunsItsCommand() {
        val a = run("net")
        assertEquals(CommandAction.OpenApp(apps[0]), a.action)
    }

    @Test fun aliasesIgnoreCaseAndAccents() {
        assertEquals("ligar maria", Aliases.resolve("MÃE", listOf(CommandAlias("mãe", "ligar maria"))).trim())
        assertEquals("ligar maria", Aliases.resolve("mae", listOf(CommandAlias("Mãe", "ligar maria"))).trim())
    }

    @Test fun whatFollowsTheAliasIsKept() {
        assertEquals("w ana oi tudo bem", Aliases.resolve("z oi tudo bem", aliases))
        val a = run("z oi")
        val action = a.action as CommandAction.Custom
        assertEquals("Ana Lima", action.contact?.name)
        assertEquals("oi", action.text)
    }

    @Test fun aWordThatIsNotAnAliasIsUntouched() {
        assertEquals("abrir chrome", Aliases.resolve("abrir chrome", aliases))
        assertEquals("maria", Aliases.resolve("maria", aliases))
    }

    @Test fun aPartialAliasIsSuggestedFirst() {
        val a = run("ma")
        assertEquals("mae", a.suggestions.first().completion)
        assertEquals("APELIDO", a.suggestions.first().kind)
    }

    @Test fun anAliasCannotShadowACommandOrACustomLetter() {
        assertTrue(Aliases.validate("abrir", "ligar maria", emptyList(), CustomActions.DEFAULTS) != null)
        assertTrue(Aliases.validate("w", "ligar maria", emptyList(), CustomActions.DEFAULTS) != null)
        assertTrue(Aliases.validate("mae", "ligar maria", aliases, CustomActions.DEFAULTS) != null)
        assertTrue(Aliases.validate("dois palavras", "ligar maria", emptyList(), CustomActions.DEFAULTS) != null)
        assertTrue(Aliases.validate("loop", "loop agora", emptyList(), CustomActions.DEFAULTS) != null)
        assertNull(Aliases.validate("pai", "ligar joao", aliases, CustomActions.DEFAULTS))
    }

    @Test fun spokenAliasIsNotTurnedIntoABuiltInVerb() {
        val zap = listOf(CommandAlias("zap", "w ana"))
        assertEquals("zap oi", VoiceCommand.normalize("Zap oi", zap))
        assertEquals("w oi", VoiceCommand.normalize("zap oi"))
        assertEquals("ligar maria", VoiceCommand.normalize("chamar maria", zap))
    }
}
