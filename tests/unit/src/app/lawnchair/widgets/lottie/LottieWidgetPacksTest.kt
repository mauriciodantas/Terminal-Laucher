package app.lawnchair.widgets.lottie

import app.lawnchair.command.CommandAlias
import app.lawnchair.command.CustomActions
import app.lawnchair.util.ResourceTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A shared widget carries the user's own letters and aliases that its command runs. */
class LottieWidgetPacksTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private val actions = CustomActions.DEFAULTS
    private val letter = actions.first()

    @Test fun builtInCommandsNeedNothing() {
        val pack = LottieWidgetPacks.dependencies("tema escuro; lanterna", actions, emptyList())
        assertTrue(pack.actions.isEmpty() && pack.aliases.isEmpty())
    }

    @Test fun lettersAndAliasesAreCollected() {
        val night = CommandAlias("noite", "tema escuro; ${letter.letter} oi")
        val bed = CommandAlias("cama", "noite")
        val other = CommandAlias("outro", "lanterna")
        val pack = LottieWidgetPacks.dependencies("cama", actions, listOf(night, bed, other))
        assertEquals(listOf(bed, night), pack.aliases)
        assertEquals(listOf(letter), pack.actions)
    }

    @Test fun scheduledCommandsCount() {
        val pack = LottieWidgetPacks.dependencies("em 30m: ${letter.letter} oi", actions, emptyList())
        assertEquals(listOf(letter), pack.actions)
    }

    @Test fun aliasLoopsEnd() {
        val a = CommandAlias("a", "b")
        val b = CommandAlias("b", "a")
        assertEquals(2, LottieWidgetPacks.dependencies("a", actions, listOf(a, b)).aliases.size)
    }
}
