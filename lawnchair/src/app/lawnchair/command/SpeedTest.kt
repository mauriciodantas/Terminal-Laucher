package app.lawnchair.command

import app.lawnchair.util.Texts
import com.android.launcher3.R
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where a speed test stands: what has been measured so far and what is being measured now. */
data class SpeedResult(
    val phase: Phase = Phase.LATENCY,
    val latencyMs: Int? = null,
    val jitterMs: Int? = null,
    /** Mbps; while the phase runs, the figure so far. */
    val downMbps: Double? = null,
    val upMbps: Double? = null,
    /** The Cloudflare data center that answered ("GRU"). */
    val server: String? = null,
    val bytesUsed: Long = 0,
) {
    enum class Phase { LATENCY, DOWNLOAD, UPLOAD, DONE }
}

/** The figures of a speed test and how they read. Pure, so it can be unit tested. */
object Speed {

    /** The middle value of [values]; 0 for none. */
    fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    /** How much the latency varies: the mean gap between one sample and the next. */
    fun jitter(values: List<Long>): Long = if (values.size < 2) 0 else values.zipWithNext { a, b -> abs(a - b) }.average().toLong()

    /** Megabits per second for [bytes] in [millis]. */
    fun mbps(bytes: Long, millis: Long): Double = if (millis <= 0) 0.0 else bytes * 8.0 / millis / 1000.0

    /** "245", "38.5", "0.8": no decimals once the figure is large. */
    fun format(mbps: Double): String = when {
        mbps >= 100 -> Math.round(mbps).toString()
        mbps >= 10 -> CalcEvaluator.format(Math.round(mbps * 10) / 10.0)
        else -> CalcEvaluator.format(Math.round(mbps * 100) / 100.0)
    }

    /** The data center code at the end of a "cf-ray" header ("8c1f…-GRU"), or null. */
    fun colo(cfRay: String?): String? = cfRay?.substringAfterLast('-', "")?.takeIf { it.length in 3..4 && it.all(Char::isLetter) }?.uppercase()

    /** The lines the bar shows: what is done, then what is being measured. */
    fun text(r: SpeedResult): String = buildList {
        val running = r.phase != SpeedResult.Phase.DONE
        add(
            if (r.latencyMs != null) {
                Texts.get(R.string.cmd_fast_latency, r.latencyMs, r.jitterMs ?: 0)
            } else {
                Texts.get(R.string.cmd_fast_latency_measuring)
            },
        )
        val measuring = Texts.get(R.string.cmd_fast_measuring)
        when {
            r.downMbps != null -> add(Texts.get(R.string.cmd_fast_down, format(r.downMbps)) + if (running && r.phase == SpeedResult.Phase.DOWNLOAD) " · $measuring" else "")
            r.phase == SpeedResult.Phase.DOWNLOAD -> add(Texts.get(R.string.cmd_fast_down, "…") + " · $measuring")
        }
        when {
            r.upMbps != null -> add(Texts.get(R.string.cmd_fast_up, format(r.upMbps)) + if (running && r.phase == SpeedResult.Phase.UPLOAD) " · $measuring" else "")
            r.phase == SpeedResult.Phase.UPLOAD -> add(Texts.get(R.string.cmd_fast_up, "…") + " · $measuring")
        }
        if (r.phase == SpeedResult.Phase.DONE) {
            add(Texts.get(R.string.cmd_fast_server, r.server ?: "?", Math.round(r.bytesUsed / 1_000_000.0).toInt()))
        }
    }.joinToString("\n")

    /** At most this much data in a test: what the preview warns about on a metered network. */
    const val MAX_MB = 150
}

/**
 * Measures latency, download and upload against Cloudflare's speed test servers, the ones behind
 * speed.cloudflare.com. Download and upload run several streams at once for a few seconds each and
 * stop early once [Speed.MAX_MB] is used, so a fast connection does not spend more than that.
 */
object SpeedTestRunner {

    private const val BASE = "https://speed.cloudflare.com"
    private const val LATENCY_SAMPLES = 10
    private const val STREAMS = 4
    private const val DOWNLOAD_MS = 8_000L
    private const val UPLOAD_MS = 6_000L

    /** The first moments of a transfer, while TCP is still ramping up, are left out of the figure. */
    private const val WARMUP_MS = 1_000L
    private const val TICK_MS = 250L
    private const val CHUNK_UP = 2 * 1024 * 1024
    private const val TIMEOUT_MS = 10_000

    suspend fun run(onProgress: (SpeedResult) -> Unit): SpeedResult = withContext(Dispatchers.IO) {
        val used = AtomicLong(0)
        val limit = Speed.MAX_MB * 1_000_000L
        var result = SpeedResult()
        onProgress(result)

        // Latency: empty downloads one after another, timed to the first byte.
        val samples = mutableListOf<Long>()
        var server: String? = null
        repeat(LATENCY_SAMPLES) {
            ensureActive()
            val start = System.nanoTime()
            val c = open("$BASE/__down?bytes=0")
            // Read to the end and not disconnected, so the next sample reuses the connection and
            // times only the round trip, not a new TLS handshake.
            c.inputStream.use { it.readBytes() }
            samples += (System.nanoTime() - start) / 1_000_000
            server = server ?: Speed.colo(c.getHeaderField("cf-ray"))
        }
        // The first request also opens the connection; it is not part of the latency.
        val timed = if (samples.size > 2) samples.drop(1) else samples
        result = result.copy(
            phase = SpeedResult.Phase.DOWNLOAD,
            latencyMs = Speed.median(timed).toInt(),
            jitterMs = Speed.jitter(timed).toInt(),
            server = server,
        )
        onProgress(result)

        val down = transfer(DOWNLOAD_MS, used, limit, {
            result = result.copy(downMbps = it, bytesUsed = used.get())
            onProgress(result)
        }) { counted ->
            val size = 25_000_000
            val c = open("$BASE/__down?bytes=$size")
            try {
                c.inputStream.use { drain(it, counted) }
            } finally {
                c.disconnect()
            }
        }
        result = result.copy(phase = SpeedResult.Phase.UPLOAD, downMbps = down, bytesUsed = used.get())
        onProgress(result)

        val payload = ByteArray(CHUNK_UP).also { java.util.Random().nextBytes(it) }
        val up = transfer(UPLOAD_MS, used, limit, {
            result = result.copy(upMbps = it, bytesUsed = used.get())
            onProgress(result)
        }) { counted ->
            val c = open("$BASE/__up").apply {
                requestMethod = "POST"
                doOutput = true
                setFixedLengthStreamingMode(CHUNK_UP)
                setRequestProperty("Content-Type", "application/octet-stream")
            }
            try {
                c.outputStream.use { out ->
                    var sent = 0
                    while (sent < CHUNK_UP) {
                        val n = minOf(64 * 1024, CHUNK_UP - sent)
                        out.write(payload, sent, n)
                        sent += n
                        counted(n.toLong())
                    }
                }
                c.inputStream.use { it.readBytes() }
            } finally {
                c.disconnect()
            }
        }
        result = result.copy(phase = SpeedResult.Phase.DONE, upMbps = up, bytesUsed = used.get())
        onProgress(result)
        result
    }

    /**
     * Runs [request] on [STREAMS] streams over and over for [durationMs], counting the bytes each
     * one moves, and reports the throughput as it goes. Gives the figure after the warm-up.
     */
    private suspend fun transfer(
        durationMs: Long,
        used: AtomicLong,
        limit: Long,
        onRate: (Double) -> Unit,
        request: (counted: (Long) -> Unit) -> Unit,
    ): Double = coroutineScope {
        val moved = AtomicLong(0)
        val start = System.currentTimeMillis()
        var warmBytes = -1L
        var warmAt = 0L
        val deadline = start + durationMs
        val job = coroutineContext[kotlinx.coroutines.Job]

        // Closing the bar cancels the test; a stream blocked in a read notices at its next chunk.
        fun over() = System.currentTimeMillis() >= deadline || used.get() >= limit || job?.isActive == false
        val counted: (Long) -> Unit = { n ->
            moved.addAndGet(n)
            used.addAndGet(n)
            if (over()) throw StopTransfer()
        }
        val workers = (1..STREAMS).map {
            async(Dispatchers.IO) {
                while (isActive && !over()) {
                    try {
                        request(counted)
                    } catch (_: StopTransfer) {
                        break
                    }
                }
            }
        }
        val ticker = launch {
            while (isActive && !over()) {
                delay(TICK_MS)
                val now = System.currentTimeMillis()
                if (warmBytes < 0 && now - start >= WARMUP_MS) {
                    warmBytes = moved.get()
                    warmAt = now
                }
                val rate = if (warmBytes >= 0 && now > warmAt) Speed.mbps(moved.get() - warmBytes, now - warmAt) else Speed.mbps(moved.get(), now - start)
                onRate(rate)
            }
        }
        workers.awaitAll()
        ticker.cancel()
        val end = System.currentTimeMillis()
        if (warmBytes >= 0 && end > warmAt) Speed.mbps(moved.get() - warmBytes, end - warmAt) else Speed.mbps(moved.get(), end - start)
    }

    private class StopTransfer : RuntimeException()

    private fun drain(input: InputStream, counted: (Long) -> Unit) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            counted(n.toLong())
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        useCaches = false
        setRequestProperty("Cache-Control", "no-cache")
    }
}
