package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** "fast": the figures of the speed test and how the bar shows them. */
class FastCommandTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private fun run(text: String, x: CommandExtras) = CommandEngine.analyze(text, emptyList(), emptyList(), true, emptyList(), emptyList(), x)

    @Test fun figures() {
        assertEquals(20L, Speed.median(listOf(30, 10, 20)))
        assertEquals(15L, Speed.median(listOf(10, 20, 30, 10)))
        assertEquals(10L, Speed.jitter(listOf(20, 30, 20)))
        // 25 MB in 1 s is 200 Mbps.
        assertEquals(200.0, Speed.mbps(25_000_000, 1_000), 0.001)
        assertEquals("245", Speed.format(245.4))
        assertEquals("GRU", Speed.colo("8c1f2a3b4c5d6e7f-GRU"))
        assertNull(Speed.colo("broken"))
    }

    @Test fun textAsItGoes() {
        val measuring = SpeedResult(phase = SpeedResult.Phase.DOWNLOAD, latencyMs = 18, jitterMs = 2, downMbps = 120.0)
        assertEquals("latência 18 ms · jitter 2 ms\n↓ download 120 Mbps · medindo…", Speed.text(measuring))
        val done = measuring.copy(phase = SpeedResult.Phase.DONE, downMbps = 245.0, upMbps = 380.0, server = "GRU", bytesUsed = 148_600_000)
        assertEquals("latência 18 ms · jitter 2 ms\n↓ download 245 Mbps\n↑ upload 380 Mbps\nCloudflare GRU · 149 MB usados", Speed.text(done))
    }

    @Test fun command() {
        val wifi = CommandExtras(network = NetworkSnapshot(NetKind.WIFI, internet = true))
        assertEquals(CommandAction.SpeedTest, run("fast", wifi).action)
        assertEquals(Tone.OK, run("fast", wifi).tone)
        // On a metered network it says how much it may use; Enter still runs it.
        val cell = CommandExtras(network = NetworkSnapshot(NetKind.CELLULAR, internet = true, metered = true))
        assertEquals(Tone.WARN, run("fast", cell).tone)
        assertTrue(run("fast", cell).preview.contains("150 MB"))
        assertEquals(CommandAction.SpeedTest, run("speedtest", cell).action)
        assertNull(run("fast", CommandExtras(network = NetworkSnapshot(NetKind.NONE))).action)
    }
}
