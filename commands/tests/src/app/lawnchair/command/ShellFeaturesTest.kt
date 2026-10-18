package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** User variables, echo, "&&", the numbered history and shared text. */
class ShellFeaturesTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private val apps = listOf("Chrome", "Kindle", "Maps").map { AppEntry(it, "pkg/$it") }
    private val contacts = listOf(ContactEntry("Ana Souza", "111", "ana@x.com"))
    private val base = CommandExtras(now = LocalDateTime.of(2026, 10, 11, 10, 0), zone = ZoneOffset.UTC)

    private fun run(text: String, x: CommandExtras = base) =
        CommandEngine.analyze(text, apps, contacts, true, CustomActions.DEFAULTS, emptyList(), x)

    // ---- variables ----

    @Test fun exportSavesAVariable() {
        val a = run("export CASA=\"Rua X, 123\"")
        assertEquals(CommandAction.SetVariable("CASA", "Rua X, 123"), a.action)
        assertEquals(CommandAction.SetVariable("CASA", "Rua X"), run("export CASA=Rua X").action)
        assertEquals(CommandAction.SetVariable("CASA", "Rua X"), run("exportar CASA Rua X").action)
    }

    @Test fun exportRefusesBadNamesAndBuiltInOnes() {
        assertNull(run("export 1a=x").action)
        assertNull(run("export clip=x").action)
        assertNull(run("export text=x").action)
        assertNull(run("export CASA").action)
    }

    @Test fun aVariableFillsAnyCommand() {
        val x = base.copy(variables = listOf(UserVariable("CASA", "Rua X, 123")))
        assertEquals(CommandAction.Route("Rua X, 123"), run("rota {CASA}", x).action)
        // The name does not care about case.
        assertEquals(CommandAction.Route("Rua X, 123"), run("rota {casa}", x).action)
        // An unknown name stays as typed.
        assertEquals(CommandAction.Route("{outra}"), run("rota {outra}", x).action)
    }

    @Test fun savedCommandsDoNotFillUserVariables() {
        val x = base.copy(variables = listOf(UserVariable("CASA", "Rua X")))
        assertEquals("Rua X", Variables.expand("{CASA}", x).text)
        assertEquals("{CASA}", Variables.expand("{CASA}", x, includeUser = false).text)
    }

    @Test fun unsetForgetsAVariable() {
        val x = base.copy(variables = listOf(UserVariable("CASA", "Rua X")))
        assertEquals(CommandAction.UnsetVariable("CASA"), run("unset casa", x).action)
        assertNull(run("unset outra", x).action)
    }

    @Test fun aVariableSetEarlierInAChainIsThereForTheNext() {
        val chain = run("export CASA=\"Rua X; ap 2\"; rota {CASA}").action as CommandAction.Chain
        assertEquals(CommandAction.SetVariable("CASA", "Rua X; ap 2"), chain.steps[0])
        assertEquals(CommandAction.Route("Rua X; ap 2"), chain.steps[1])
    }

    @Test fun variablesEncodeAndDecode() {
        val list = listOf(UserVariable("A", "um \"dois\""), UserVariable("B", "tres"))
        assertEquals(list, UserVariables.decode(UserVariables.encode(list)))
        assertEquals(emptyList<UserVariable>(), UserVariables.decode("not json"))
    }

    // ---- chains ----

    @Test fun andThenMarksTheStepsThatNeedTheOneBefore() {
        val chain = run("tema escuro && abrir kindle; abrir maps").action as CommandAction.Chain
        assertEquals(3, chain.steps.size)
        assertEquals(listOf(false, true, false), chain.andThen)
    }

    @Test fun aSemicolonAtTheEndEndsTheCommand() {
        val a = run("export CASA=\"Rua X, 123\";")
        assertEquals(CommandAction.SetVariable("CASA", "Rua X, 123"), a.action)
        val two = run("tema escuro; abrir kindle;").action as CommandAction.Chain
        assertEquals(2, two.steps.size)
        // "&&" at the end still waits for the next command.
        assertNull(run("tema escuro &&").action)
    }

    @Test fun separatorsInsideQuotesStay() {
        assertEquals(listOf("nota \"a; b && c\"", "abrir maps"), CommandEngine.splitChain("nota \"a; b && c\"; abrir maps"))
    }

    // ---- echo ----

    @Test fun echoShowsTheIntentWithoutRunning() {
        val a = run("echo abrir kindle")
        assertEquals(CommandAction.FillLine("abrir kindle"), a.action)
        assertTrue(a.intent.contains("pkg"))
        assertEquals(Tone.OK, a.tone)
    }

    @Test fun echoOfAChainShowsEveryStep() {
        val a = run("echo tema escuro && abrir kindle; abrir maps")
        assertEquals(CommandAction.FillLine("tema escuro && abrir kindle; abrir maps"), a.action)
        assertTrue(a.intent.contains("&&"))
        assertTrue(a.intent.contains("; "))
    }

    @Test fun echoOfALineThatDoesNotWorkRunsNothing() {
        assertNull(run("echo rota").action)
        assertNull(run("echo").action)
    }

    // ---- history ----

    private val history = listOf("abrir kindle", "tema escuro", "abrir maps")

    @Test fun bangRunsAHistoryEntry() {
        val x = base.copy(history = history)
        assertEquals(CommandAction.OpenApp(apps[1]), run("!1", x).action)
        assertEquals(CommandAction.OpenApp(apps[2]), run("!3", x).action)
        assertEquals(CommandAction.OpenApp(apps[1]), run("!abrir", x).action)
        assertNull(run("!9", x).action)
        assertNull(run("!zzz", x).action)
    }

    @Test fun historyListsTheNumberedEntries() {
        val x = base.copy(history = history)
        val all = run("history", x)
        assertEquals(3, all.suggestions.size)
        assertEquals("!2", all.suggestions[1].kind)
        val filtered = run("history MAPS", x)
        assertEquals(listOf("abrir maps"), filtered.suggestions.map { it.completion })
        assertEquals("!3", filtered.suggestions.single().kind)
        assertEquals(CommandAction.ClearHistory, run("history clear", x).action)
        assertNull(run("history clear").action)
    }

    @Test fun recallSkipsBangsAndRepeats() {
        assertEquals("abrir kindle", CommandEngine.recall("1", listOf("!!", "!2", "abrir kindle")))
        assertNotNull(CommandEngine.recall("ab", history))
        assertFalse(CommandEngine.recallable(listOf("!!", "a")).contains("!!"))
    }
}
