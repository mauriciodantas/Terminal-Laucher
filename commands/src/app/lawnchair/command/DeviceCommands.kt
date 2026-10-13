package app.lawnchair.command

import androidx.annotation.StringRes
import app.lawnchair.util.Texts

/** "bateria", "armazenamento", "ip", "uptime": one fact about the device; Enter copies it. */
class InfoCommand(id: BuiltInCommand, @StringRes helpRes: Int) : BuiltIn(id, R.string.cmd_usage_info, helpRes) {

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val extras = ctx.extras
        val title = id.word.uppercase()
        val info = extras.info ?: return commandResult(title, Texts.get(R.string.cmd_info_pending), Tone.IDLE, null)
        val text = when (id) {
            BuiltInCommand.BATTERY -> buildString {
                append("${info.batteryPercent}%")
                append(" · ")
                append(Texts.get(if (info.charging) R.string.cmd_battery_charging else R.string.cmd_battery_discharging))
                info.batteryCelsius?.let { append(" · %.1f°C".format(it)) }
            }

            BuiltInCommand.STORAGE -> Texts.get(R.string.cmd_storage_free, bytes(info.storageFree), bytes(info.storageTotal))

            BuiltInCommand.IP -> info.addresses.ifEmpty { listOf(Texts.get(R.string.cmd_ip_none)) }.joinToString("\n")

            BuiltInCommand.UPTIME -> uptime(info.uptimeMillis)

            else -> ""
        }
        return commandResult(title, text, Tone.OK, CommandAction.Copy(text))
    }

    companion object {
        fun bytes(value: Long): String {
            val gb = value / (1024.0 * 1024 * 1024)
            return if (gb >= 1) CalcEvaluator.format(Math.round(gb * 10) / 10.0) + " GB" else CalcEvaluator.format(Math.round(value / (1024.0 * 1024)).toDouble()) + " MB"
        }

        fun uptime(millis: Long): String {
            val minutes = millis / 60_000
            val days = minutes / (60 * 24)
            val hours = minutes / 60 % 24
            val mins = minutes % 60
            return listOfNotNull(days.takeIf { it > 0 }?.let { "${it}d" }, "${hours}h", "${mins}min").joinToString(" ")
        }
    }
}

/** "rede": the connection in use. */
object NetworkCommand : BuiltIn(BuiltInCommand.NETWORK, R.string.cmd_usage_info, R.string.cmd_help_network) {

    /**
     * "rede": the connection in use, one fact a line, and Enter copies it. What the system does not
     * give is left out; the Wi-Fi name says why when it is hidden.
     */
    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val extras = ctx.extras
        val title = BuiltInCommand.NETWORK.word.uppercase()
        val net = extras.network ?: return commandResult(title, Texts.get(R.string.cmd_info_pending), Tone.IDLE, null)
        if (net.kind == NetKind.NONE) return commandResult(title, Texts.get(R.string.cmd_net_offline), Tone.WARN, null)
        val text = networkText(net)
        return commandResult(title + " · " + kindName(net.kind), text, if (net.internet) Tone.OK else Tone.WARN, CommandAction.Copy(text))
    }

    private fun kindName(kind: NetKind): String = when (kind) {
        NetKind.WIFI -> "WI-FI"
        NetKind.CELLULAR -> Texts.get(R.string.cmd_net_cellular)
        NetKind.ETHERNET -> "ETHERNET"
        NetKind.NONE -> ""
    }

    /** The lines of "rede", in the order they matter: the state, the link, then the addresses. */
    fun networkText(net: NetworkSnapshot): String = buildList {
        add(
            listOfNotNull(
                Texts.get(if (net.internet) R.string.cmd_net_internet else R.string.cmd_net_no_internet),
                Texts.get(R.string.cmd_net_metered).takeIf { net.metered },
                "VPN".takeIf { net.vpn },
            ).joinToString(" · "),
        )
        when (net.kind) {
            NetKind.WIFI -> {
                add("SSID " + (net.ssid ?: Texts.get(R.string.cmd_net_ssid_hidden)))
                net.signalDbm?.let { add(Texts.get(R.string.cmd_net_signal, it, Texts.get(signalQuality(it)))) }
                listOfNotNull(
                    net.frequencyMhz?.let { band(it) },
                    net.wifiStandard?.let { "Wi-Fi $it" },
                    net.linkMbps?.takeIf { it > 0 }?.let { "$it Mbps" },
                ).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" · ")) }
            }

            NetKind.CELLULAR -> net.carrier?.takeIf { it.isNotBlank() }?.let { add(Texts.get(R.string.cmd_net_carrier, it)) }

            else -> {}
        }
        if (net.kind != NetKind.WIFI || net.linkMbps == null) {
            // Without a link speed, the system's estimate is the best figure there is.
            if (net.downKbps != null && net.upKbps != null && net.downKbps > 0) {
                add(Texts.get(R.string.cmd_net_estimate, mbps(net.downKbps), mbps(net.upKbps)))
            }
        }
        if (net.addresses.isNotEmpty()) add("IP " + net.addresses.joinToString(" · "))
        net.gateway?.let { add("GATEWAY $it") }
        if (net.dns.isNotEmpty() || net.privateDns != null) {
            add("DNS " + (listOfNotNull(net.privateDns?.let { Texts.get(R.string.cmd_net_private_dns, it) }) + net.dns).joinToString(" · "))
        }
    }.joinToString("\n")

    /** The band of a Wi-Fi channel at [mhz]. */
    fun band(mhz: Int): String = when {
        mhz in 2400..2500 -> "2.4 GHz"
        mhz in 4900..5900 -> "5 GHz"
        mhz in 5925..7125 -> "6 GHz"
        mhz >= 57000 -> "60 GHz"
        else -> "$mhz MHz"
    }

    /** How a Wi-Fi signal of [dbm] is read: -50 and up excellent, to -60 good, to -70 fair, below weak. */
    fun signalQuality(dbm: Int): Int = when {
        dbm >= -50 -> R.string.cmd_net_signal_excellent
        dbm >= -60 -> R.string.cmd_net_signal_good
        dbm >= -70 -> R.string.cmd_net_signal_fair
        else -> R.string.cmd_net_signal_weak
    }

    private fun mbps(kbps: Int): String = CalcEvaluator.format(Math.round(kbps / 100.0) / 10.0)
}

/** "fast": a speed test. */
object FastCommand : BuiltIn(BuiltInCommand.FAST, R.string.cmd_usage_info, R.string.cmd_help_fast) {

    /**
     * "fast": a speed test. Enter starts it; on mobile data or another metered network the preview
     * warns how much data it may use first, and Enter is the go-ahead.
     */
    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val extras = ctx.extras
        val title = Texts.get(R.string.cmd_fast_title)
        val net = extras.network
        if (net?.kind == NetKind.NONE) return commandResult(title, Texts.get(R.string.cmd_net_offline), Tone.ERROR, null)
        return if (net?.metered == true) {
            commandResult(title, Texts.get(R.string.cmd_fast_metered, Speed.MAX_MB), Tone.WARN, CommandAction.SpeedTest)
        } else {
            commandResult(title, Texts.get(R.string.cmd_fast_ready), Tone.OK, CommandAction.SpeedTest)
        }
    }
}

/** "ping 1.1.1.1": how long a host takes to answer, shown in the bar. */
object PingCommand : BuiltIn(BuiltInCommand.PING, R.string.cmd_usage_ping, R.string.cmd_help_ping) {

    override val requiresArgument = true

    private val hostPattern = Regex("^[a-zA-Z0-9.:-]+$")

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val host = arg.trim()
        val title = "PING"
        return when {
            host.isEmpty() -> commandResult(title, Texts.get(R.string.cmd_ping_empty), Tone.IDLE, null, listTitle = Texts.get(R.string.cmd_list_destination))
            !hostPattern.matches(host) -> commandResult(title, Texts.get(R.string.cmd_ping_invalid), Tone.ERROR, null)
            else -> commandResult(title, host.uppercase(), Tone.OK, CommandAction.Ping(host))
        }
    }
}
