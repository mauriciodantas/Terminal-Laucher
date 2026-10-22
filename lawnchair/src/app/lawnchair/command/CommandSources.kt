package app.lawnchair.command

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.CalendarContract
import android.telephony.TelephonyManager
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
            variables = UserVariableStore.load(context),
            jobs = JobStore.load(context),
            info = snapshot(context),
            taskApp = TaskApp.label(context),
            ai = AiStore.load(context)?.label,
            network = network(context),
            advancedMode = AdvancedModeStore.load(context),
            host = HostData(
                theme = runCatching { ThemeStore.current(context) }.getOrNull(),
                themes = ThemeStore.load(context),
                themePresets = ThemeStore.presets(context),
                // Read once from the app's assets and kept by the listing afterwards.
                fonts = kotlinx.coroutines.runBlocking { ThemeStore.fonts(context) },
            ),
        ).withProviders(context)
    }

    /** [this] with the API providers and which of them have their secret saved. */
    fun CommandExtras.withProviders(context: Context): CommandExtras {
        val providers = ProviderStore.all(context)
        return copy(providers = providers, configuredProviders = SecretStore.configured(context, providers.filter { it.auth.needsSecret }.map { it.id }))
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

    /**
     * The connection in use. The Wi-Fi name needs precise location, which the app does not ask for,
     * so on recent Android it is usually left out; the rest needs no permission the user grants.
     */
    @Suppress("DEPRECATION")
    fun network(context: Context): NetworkSnapshot? = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val active = cm.activeNetwork ?: return NetworkSnapshot(NetKind.NONE)
        val caps = cm.getNetworkCapabilities(active) ?: return NetworkSnapshot(NetKind.NONE)
        val link = cm.getLinkProperties(active)
        val kind = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetKind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetKind.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetKind.ETHERNET
            else -> NetKind.NONE
        }
        // Behind a VPN the active network is the VPN itself; the link underneath tells the kind.
        val vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        val under = if (vpn || kind == NetKind.NONE) {
            cm.allNetworks.mapNotNull { cm.getNetworkCapabilities(it) }.firstOrNull { !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) }
        } else {
            null
        }
        val realKind = if (kind != NetKind.NONE) {
            kind
        } else {
            when {
                under?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> NetKind.WIFI
                under?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> NetKind.CELLULAR
                under?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> NetKind.ETHERNET
                else -> NetKind.NONE
            }
        }
        val wifi = if (realKind == NetKind.WIFI) context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo else null
        val ssid = wifi?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        val addresses = link?.linkAddresses.orEmpty().map { it.address }
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            .sortedBy { if (it is Inet4Address) 0 else 1 }
            .mapNotNull { it.hostAddress?.substringBefore('%') }
        val gateway = link?.routes.orEmpty().firstOrNull { it.isDefaultRoute && it.gateway != null && !it.gateway!!.isAnyLocalAddress }?.gateway?.hostAddress
        NetworkSnapshot(
            kind = realKind,
            internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            vpn = vpn,
            ssid = ssid,
            signalDbm = wifi?.rssi?.takeIf { it in -126..0 },
            frequencyMhz = wifi?.frequency?.takeIf { it > 0 },
            wifiStandard = if (wifi != null && Build.VERSION.SDK_INT >= 30) {
                when (wifi.wifiStandard) {
                    4 -> 4
                    5 -> 5
                    6 -> 6
                    8 -> 7
                    else -> null
                }
            } else {
                null
            },
            linkMbps = wifi?.linkSpeed?.takeIf { it > 0 },
            carrier = if (realKind == NetKind.CELLULAR) context.getSystemService(TelephonyManager::class.java)?.networkOperatorName else null,
            downKbps = caps.linkDownstreamBandwidthKbps.takeIf { it > 0 },
            upKbps = caps.linkUpstreamBandwidthKbps.takeIf { it > 0 },
            addresses = addresses,
            gateway = gateway,
            dns = link?.dnsServers.orEmpty().mapNotNull { it.hostAddress?.substringBefore('%') },
            privateDns = if (Build.VERSION.SDK_INT >= 28 && link?.isPrivateDnsActive == true) link.privateDnsServerName ?: "auto" else null,
            interfaceName = link?.interfaceName,
        )
    }.onFailure { android.util.Log.w("CommandSources", "network", it) }.getOrNull()

    /** Runs ping and gives its summary lines, or the error. */
    fun ping(host: String): String = runCatching {
        val process = ProcessBuilder("ping", "-c", "3", "-W", "2", host).redirectErrorStream(true).start()
        // The watchdog ends a ping that never answers; reading the output would otherwise block past the timeout.
        val watchdog = kotlin.concurrent.thread(isDaemon = true) {
            if (!process.waitFor(PING_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly()
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        watchdog.interrupt()
        val lines = output.lines().filter { it.isNotBlank() }
        // The statistics at the end say all that matters; the replies before are shown when they are few.
        lines.takeLast(2).joinToString("\n").ifEmpty { output.trim() }
    }.getOrElse { it.message ?: "ping failed" }

    private const val MAX_EVENTS = 20
    private const val PING_TIMEOUT_S = 10L
}
