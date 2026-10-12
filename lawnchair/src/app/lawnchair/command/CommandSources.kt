package app.lawnchair.command

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.CalendarContract
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

/** Reads what [CommandExtras] holds from the device: calendar, location, battery, clipboard... */
object CommandSources {

    fun granted(context: Context, permission: String): Boolean = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun calendarGranted(context: Context) = granted(context, Manifest.permission.READ_CALENDAR)

    fun locationGranted(context: Context) = granted(context, Manifest.permission.ACCESS_COARSE_LOCATION) ||
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    /** What the device holds for the command bar, all but the clipboard (read when the window has focus). */
    fun load(context: Context, history: List<String>, selection: String? = null): CommandExtras {
        val calendar = calendarGranted(context)
        val location = locationGranted(context)
        return CommandExtras(
            history = history,
            selection = selection,
            location = if (location) lastLocation(context) else null,
            locationGranted = location,
            rates = RatesStore.load(context),
            events = if (calendar) loadEvents(context) else null,
            calendarGranted = calendar,
            notes = NoteStore.load(context),
            jobs = JobStore.load(context),
            info = snapshot(context),
            taskApp = TaskApp.label(context),
            ai = AiStore.load(context)?.label,
            theme = runCatching { ThemeStore.current(context) }.getOrNull(),
            themes = ThemeStore.load(context),
            themePresets = ThemeStore.presets(context),
            // Read once from the app's assets and kept by the listing afterwards.
            fonts = kotlinx.coroutines.runBlocking { ThemeStore.fonts(context) },
        )
    }

    /** The copied text, or null. Android only lets the app in front read it. */
    fun clipboard(context: Context): String? = runCatching {
        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
        if (clip.itemCount == 0) null else clip.getItemAt(0).coerceToText(context)?.toString()
    }.getOrNull()

    /** Events from now to the end of tomorrow. */
    fun loadEvents(context: Context): List<CalendarEntry> = runCatching {
        val now = System.currentTimeMillis()
        val end = java.time.LocalDate.now().plusDays(2).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString())
            .appendPath(end.toString())
            .build()
        val events = mutableListOf<CalendarEntry>()
        context.contentResolver.query(
            uri,
            arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
            ),
            null,
            null,
            CalendarContract.Instances.BEGIN + " ASC",
        )?.use { cursor ->
            while (cursor.moveToNext() && events.size < MAX_EVENTS) {
                events += CalendarEntry(
                    title = cursor.getString(0).orEmpty(),
                    begin = cursor.getLong(1),
                    end = cursor.getLong(2),
                    allDay = cursor.getInt(3) == 1,
                )
            }
        }
        events
    }.getOrDefault(emptyList())

    /** "lat,long" of the freshest location any provider remembers. */
    @Suppress("MissingPermission")
    fun lastLocation(context: Context): String? = runCatching {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val best = manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time } ?: return null
        String.format(Locale.US, "%.6f,%.6f", best.latitude, best.longitude)
    }.getOrNull()

    fun snapshot(context: Context): SystemSnapshot? = runCatching {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temperature = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val stat = StatFs(Environment.getDataDirectory().path)
        SystemSnapshot(
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else 0,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            batteryCelsius = temperature.takeIf { it != Int.MIN_VALUE }?.let { it / 10.0 },
            storageFree = stat.availableBytes,
            storageTotal = stat.totalBytes,
            addresses = addresses(),
            uptimeMillis = SystemClock.elapsedRealtime(),
        )
    }.getOrNull()

    /** The IPv4 addresses of the interfaces that are up: "wlan0 192.168.0.10". */
    private fun addresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nic -> nic.inetAddresses.toList().filterIsInstance<Inet4Address>().map { "${nic.name} ${it.hostAddress}" } }
    }.getOrDefault(emptyList())

    /** Runs ping and gives its summary lines, or the error. */
    fun ping(host: String): String = runCatching {
        val process = ProcessBuilder("ping", "-c", "3", "-W", "2", host).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(PING_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)) process.destroy()
        val lines = output.lines().filter { it.isNotBlank() }
        // The statistics at the end say all that matters; the replies before are shown when they are few.
        lines.takeLast(2).joinToString("\n").ifEmpty { output.trim() }
    }.getOrElse { it.message ?: "ping failed" }

    private const val MAX_EVENTS = 20
    private const val PING_TIMEOUT_S = 10L
}
