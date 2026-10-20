package app.lawnchair.widgets.command

import app.lawnchair.command.CommandAlias
import app.lawnchair.command.CommandPack
import app.lawnchair.command.CommandWidgetSchedule
import app.lawnchair.util.ResourceTexts
import app.lawnchair.command.CommandWidgetSchedule.Kind
import app.lawnchair.widgets.lottie.LottieWidgetPack
import app.lawnchair.widgets.lottie.LottieWidgetPacks
import app.lawnchair.widgets.lottie.LottieWidgetStore.Playback
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/** A shared command widget keeps its command, schedule and the aliases its command needs. */
class CommandWidgetPacksTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private fun bytes(pack: CommandWidgetPack) = ByteArrayOutputStream().also { CommandWidgetPacks.write(pack, it) }.toByteArray()

    @Test fun packKeepsCommandScheduleAndAliases() {
        val alias = CommandAlias("noite", "tema escuro")
        val pack = CommandWidgetPack(
            command = "clima recife",
            schedule = CommandWidgetSchedule(Kind.WEEKLY, hour = 7, minute = 30, weekdays = setOf(Calendar.MONDAY, Calendar.FRIDAY)),
            commands = CommandPack(emptyList(), listOf(alias)),
        )
        assertEquals(pack, CommandWidgetPacks.read(ByteArrayInputStream(bytes(pack))))
    }

    @Test fun importedIntervalBelowTheMinimumIsRaised() {
        val pack = CommandWidgetPack("ping 1.1.1.1", CommandWidgetSchedule(Kind.INTERVAL, intervalMinutes = 1), CommandPack(emptyList(), emptyList()))
        val read = CommandWidgetPacks.read(ByteArrayInputStream(bytes(pack)))
        assertEquals(CommandWidgetSchedule.MIN_INTERVAL_MINUTES, read.schedule.intervalMinutes)
    }

    @Test fun aLottiePackIsNotACommandWidget() {
        val lottie = LottieWidgetPack("a.json", "{}".toByteArray(), "lanterna", Playback.LOOP, false, CommandPack(emptyList(), emptyList()))
        val out = ByteArrayOutputStream().also { LottieWidgetPacks.write(lottie, it) }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) { CommandWidgetPacks.read(ByteArrayInputStream(out)) }
    }

    @Test fun aCommandPackIsNotALottieWidget() {
        val pack = CommandWidgetPack("clima recife", CommandWidgetSchedule(), CommandPack(emptyList(), emptyList()))
        assertThrows(IllegalArgumentException::class.java) { LottieWidgetPacks.read(ByteArrayInputStream(bytes(pack))) }
    }

    @Test fun anEmptyOrHugeCommandIsRefused() {
        val empty = CommandWidgetPack("   ", CommandWidgetSchedule(), CommandPack(emptyList(), emptyList()))
        assertThrows(IllegalArgumentException::class.java) { CommandWidgetPacks.read(ByteArrayInputStream(bytes(empty))) }
        val huge = CommandWidgetPack("a".repeat(2001), CommandWidgetSchedule(), CommandPack(emptyList(), emptyList()))
        assertThrows(IllegalArgumentException::class.java) { CommandWidgetPacks.read(ByteArrayInputStream(bytes(huge))) }
    }
}
