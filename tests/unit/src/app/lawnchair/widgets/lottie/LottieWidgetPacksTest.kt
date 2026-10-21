package app.lawnchair.widgets.lottie

import app.lawnchair.command.ApiProvider
import app.lawnchair.command.CommandPack
import app.lawnchair.command.CommandWidgetPack
import app.lawnchair.command.CommandWidgetSchedule
import app.lawnchair.command.WidgetPacks
import app.lawnchair.util.ResourceTexts
import app.lawnchair.widgets.lottie.LottieWidgetStore.Playback
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A shared Lottie widget keeps how it plays, and the commands it needs travel with it. */
class LottieWidgetPacksTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private val noCommands = CommandPack(emptyList(), emptyList())

    @Test fun theProvidersTheCommandNeedsTravelWithTheAnimation() {
        val provider = ApiProvider(id = "jw", name = "JustWatch", baseUrl = "https://apis.justwatch.com")
        val pack = LottieWidgetPack("a.json", "{}".toByteArray(), "novos", Playback.LOOP, false, CommandPack(emptyList(), emptyList(), providers = listOf(provider)))
        val out = ByteArrayOutputStream().also { LottieWidgetPacks.write(pack, it) }.toByteArray()
        assertEquals(listOf(provider), LottieWidgetPacks.read(ByteArrayInputStream(out)).commands.providers)
    }

    @Test fun aLottiePackIsNotACommandWidget() {
        val lottie = LottieWidgetPack("a.json", "{}".toByteArray(), "lanterna", Playback.LOOP, false, noCommands)
        val out = ByteArrayOutputStream().also { LottieWidgetPacks.write(lottie, it) }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) { WidgetPacks.read(ByteArrayInputStream(out)) }
        assertEquals(WidgetPacks.KIND_LOTTIE, WidgetPacks.kindOf(ByteArrayInputStream(out)))
    }

    @Test fun aCommandPackIsNotALottieWidget() {
        val pack = CommandWidgetPack("clima recife", CommandWidgetSchedule(), noCommands)
        val out = ByteArrayOutputStream().also { WidgetPacks.write(pack, it) }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) { LottieWidgetPacks.read(ByteArrayInputStream(out)) }
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
