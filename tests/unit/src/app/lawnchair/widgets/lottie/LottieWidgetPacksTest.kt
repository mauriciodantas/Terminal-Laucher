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

    @Test fun packPreservesShowResult() {
        val packWithResult = LottieWidgetPack(
            name = "test.json",
            animation = byteArrayOf(1, 2, 3),
            command = "ping 1.1.1.1",
            playback = LottieWidgetStore.Playback.LOOP,
            themeColors = true,
            commands = app.lawnchair.command.CommandPack(emptyList(), emptyList()),
            showResult = true,
            resultDurationSeconds = 120,
        )
        val out = java.io.ByteArrayOutputStream()
        LottieWidgetPacks.write(packWithResult, out)
        val readPack = LottieWidgetPacks.read(java.io.ByteArrayInputStream(out.toByteArray()))
        assertTrue(readPack.showResult)
        assertEquals(120, readPack.resultDurationSeconds)
        assertEquals(packWithResult, readPack)
    }

    @Test fun defaultAnimationIsValidLottie() {
        val json = org.json.JSONObject(DefaultWidgetAnimation.JSON)
        assertEquals("5.5.7", json.getString("v"))
        assertEquals(30, json.getInt("fr"))
        assertEquals(200, json.getInt("w"))
        assertEquals(200, json.getInt("h"))
        val layers = json.getJSONArray("layers")
        assertTrue(layers.length() >= 3)
    }
}
