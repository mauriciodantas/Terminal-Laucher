package app.lawnchair.command

import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents
import com.android.launcher3.R
import java.time.LocalDateTime

/**
 * The commands beyond opening, calling and calculating: timers, reminders, switches, media,
 * notes, the calendar, the device and the scheduled jobs. Each returns the [Analysis] of what was
 * typed after its word. Pure, so it can be unit tested.
 */
internal object MoreCommands {

    private fun result(
        title: String,
        preview: String,
        tone: Tone,
        action: CommandAction?,
        suggestions: List<Suggestion> = emptyList(),
        listTitle: String = Texts.get(R.string.cmd_list_suggestions),
        need: Need? = null,
    ) = Analysis(
        suggestions = suggestions,
        listTitle = listTitle,
        needsContacts = false,
        previewTitle = title,
        preview = preview,
        tone = tone,
        action = action,
        need = need,
    )

    // ---- timer, reminders and scheduled commands ----

    fun timer(arg: String): Analysis {
        val seconds = TimeParse.duration(arg)
        val title = Texts.get(R.string.cmd_timer_title)
        return when {
            arg.isBlank() -> result(title, Texts.get(R.string.cmd_timer_empty), Tone.IDLE, null, listTitle = Texts.get(R.string.cmd_list_format))
            seconds == null -> result(title, Texts.get(R.string.cmd_timer_invalid), Tone.ERROR, null, listTitle = Texts.get(R.string.cmd_list_format))
            else -> result(title, TimeParse.formatDuration(seconds).uppercase(), Tone.OK, CommandAction.SetTimer(seconds), listTitle = Texts.get(R.string.cmd_list_format))
        }
    }

    /** When a reminder or a scheduled command runs, parsed from the start of [words]. */
    private data class When(val at: LocalDateTime, val used: Int)

    /**
     * "em 30m", "30m", "18h", "às 18h", "amanhã 9h", "amanhã às 9h". [used] is how many words it
     * took. A bare "2h" is a clock time; "em 2h" is two hours from now.
     */
    private fun parseWhen(words: List<String>, now: LocalDateTime): When? {
        var i = 0
        var tomorrow = false
        var relative = false
        while (i < words.size && i < 3) {
            val w = words[i]
            when {
                TimeParse.isTomorrow(w) -> tomorrow = true
                TimeParse.isIn(w) -> relative = true
                TimeParse.isAt(w) -> {}
                else -> break
            }
            i++
        }
        // "1h 30m" and "18 horas" are two words.
        for (n in minOf(2, words.size - i) downTo 1) {
            val piece = words.subList(i, i + n).joinToString(" ")
            if (relative || (!tomorrow && TimeParse.isExplicitDuration(piece))) {
                val seconds = TimeParse.duration(piece, bareMinutes = relative) ?: continue
                return When(now.plusSeconds(seconds.toLong()).withNano(0), i + n)
            }
            val clock = TimeParse.clock(piece) ?: continue
            return When(TimeParse.nextAt(clock, now, tomorrow), i + n)
        }
        return null
    }

    fun remind(arg: String, extras: CommandExtras): Analysis {
        val title = Texts.get(R.string.cmd_remind_title)
        val words = arg.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val now = extras.clock()
        val time = parseWhen(words, now)
        val text = time?.let { words.drop(it.used).joinToString(" ") }.orEmpty()
        return when {
            words.isEmpty() -> result(title, Texts.get(R.string.cmd_remind_empty), Tone.IDLE, null, listTitle = Texts.get(R.string.cmd_list_format))

            time == null -> result(title, Texts.get(R.string.cmd_remind_no_time), Tone.ERROR, null, listTitle = Texts.get(R.string.cmd_list_format))

            text.isEmpty() -> result(title, TimeParse.formatClock(time.at, now) + " · " + Texts.get(R.string.cmd_remind_what), Tone.IDLE, null)

            else -> result(
                title,
                TimeParse.formatClock(time.at, now) + " · " + text.uppercase(),
                Tone.OK,
                CommandAction.Schedule(ScheduledJob(0, JobKind.REMIND, text, extras.epochMillis(time.at))),
            )
        }
    }

    private val schedulePrefix = Regex("^(\\S+)\\s+(.+?)\\s*:(?!\\d)\\s*(.*)$", RegexOption.DOT_MATCHES_ALL)

    /**
     * "em 30m: tema escuro", "às 22h: silencio on", "quando carregar: wifi". Null when [text] does
     * not start like that. [inner] analyzes the command that will run.
     */
    fun schedule(text: String, extras: CommandExtras, inner: (String) -> Analysis): Analysis? {
        val m = schedulePrefix.matchEntire(text.trim()) ?: return null
        val lead = m.groupValues[1]
        val spec = m.groupValues[2].trim()
        val command = m.groupValues[3].trim()
        val now = extras.clock()
        val job: ScheduledJob
        val label: String
        when {
            TimeParse.isWhen(lead) -> {
                val event = TriggerEvent.of(spec) ?: return result(
                    Texts.get(R.string.cmd_rule_title),
                    Texts.get(R.string.cmd_rule_events, TriggerEvent.entries.joinToString(", ") { it.word }),
                    Tone.ERROR,
                    null,
                )
                job = ScheduledJob(0, JobKind.RULE, command, event = event)
                label = Texts.get(R.string.cmd_rule_title) + " · " + event.word.uppercase()
            }

            TimeParse.isIn(lead) || TimeParse.isAt(lead) || TimeParse.isTomorrow(lead) -> {
                val time = parseWhen(listOf(lead) + spec.split(Regex("\\s+")), now)
                    ?.takeIf { it.used == 1 + spec.split(Regex("\\s+")).size }
                    ?: return result(Texts.get(R.string.cmd_schedule_title), Texts.get(R.string.cmd_remind_no_time), Tone.ERROR, null)
                job = ScheduledJob(0, JobKind.COMMAND, command, extras.epochMillis(time.at))
                label = Texts.get(R.string.cmd_schedule_title) + " · " + TimeParse.formatClock(time.at, now)
            }

            else -> return null
        }
        if (command.isEmpty()) return result(label, Texts.get(R.string.cmd_schedule_what), Tone.IDLE, null)
        val analysis = inner(command)
        val prefix = text.trim().substring(0, text.trim().length - command.length)
        return analysis.copy(
            previewTitle = label,
            suggestions = analysis.suggestions.map { it.copy(completion = prefix + it.completion) },
            // A line the bar does not understand is not scheduled as a web search.
            action = analysis.action?.takeIf { analysis.tone != Tone.ERROR }?.let { CommandAction.Schedule(job) },
            preview = if (analysis.action != null) "→ " + analysis.preview else analysis.preview,
        )
    }

    fun scheduled(extras: CommandExtras): Analysis {
        val jobs = Jobs.sorted(extras.jobs)
        val cancel = BuiltInCommand.CANCEL.word
        return result(
            Texts.get(R.string.cmd_jobs_title),
            if (jobs.isEmpty()) Texts.get(R.string.cmd_jobs_empty) else Texts.get(R.string.cmd_jobs_hint),
            Tone.IDLE,
            null,
            suggestions = jobs.mapIndexed { i, job ->
                Suggestion("$cancel ${i + 1}", "#${i + 1} " + Jobs.describe(job, extras), Texts.get(R.string.cmd_kind_job))
            },
            listTitle = Texts.get(R.string.cmd_jobs_title),
        )
    }

    fun cancel(arg: String, extras: CommandExtras): Analysis {
        val jobs = Jobs.sorted(extras.jobs)
        val n = arg.trim().removePrefix("#").toIntOrNull()
        val job = n?.let { jobs.getOrNull(it - 1) }
        val cancel = BuiltInCommand.CANCEL.word
        return result(
            Texts.get(R.string.cmd_cancel_title),
            when {
                job != null -> Jobs.describe(job, extras).uppercase()
                jobs.isEmpty() -> Texts.get(R.string.cmd_jobs_empty)
                else -> Texts.get(R.string.cmd_cancel_empty)
            },
            if (job != null) {
                Tone.OK
            } else if (arg.isBlank()) {
                Tone.IDLE
            } else {
                Tone.ERROR
            },
            job?.let { CommandAction.CancelJob(it) },
            suggestions = if (job != null) {
                emptyList()
            } else {
                jobs.mapIndexed { i, j -> Suggestion("$cancel ${i + 1}", "#${i + 1} " + Jobs.describe(j, extras), Texts.get(R.string.cmd_kind_job)) }
            },
            listTitle = Texts.get(R.string.cmd_jobs_title),
        )
    }

    // ---- switches ----

    /** True for "on", false for "off", null for neither, in Portuguese, English and the app language. */
    fun state(word: String): Boolean? {
        val w = word.trim().foldAccents()
        if (w.isEmpty()) return null
        return when (w) {
            in TimeParse.words(R.string.cmd_state_on) + setOf("on", "liga", "ligar", "ligado", "ativar", "1") -> true
            in TimeParse.words(R.string.cmd_state_off) + setOf("off", "desliga", "desligar", "desligado", "desativar", "0") -> false
            else -> null
        }
    }

    private fun stateSuggestions(command: BuiltInCommand): List<Suggestion> {
        val on = Texts.get(R.string.cmd_state_on).split(',').first().trim()
        val off = Texts.get(R.string.cmd_state_off).split(',').first().trim()
        return listOf(
            Suggestion("${command.word} $on", on.uppercase(), Texts.get(R.string.cmd_kind_option)),
            Suggestion("${command.word} $off", off.uppercase(), Texts.get(R.string.cmd_kind_option)),
        )
    }

    fun toggle(command: BuiltInCommand, target: SystemToggle, arg: String): Analysis {
        val title = when (target) {
            SystemToggle.WIFI -> "WI-FI"
            SystemToggle.BLUETOOTH -> "BLUETOOTH"
            SystemToggle.TORCH -> Texts.get(R.string.cmd_torch_title)
            SystemToggle.DND -> Texts.get(R.string.cmd_dnd_title)
            SystemToggle.ROTATION -> Texts.get(R.string.cmd_rotation_title)
        }
        val on = state(arg)
        if (arg.isNotBlank() && on == null) {
            return result(title, Texts.get(R.string.cmd_state_invalid), Tone.ERROR, null, stateSuggestions(command))
        }
        val preview = when {
            target == SystemToggle.WIFI || target == SystemToggle.BLUETOOTH -> Texts.get(R.string.cmd_panel_preview, title)
            on == true -> Texts.get(R.string.cmd_state_turn_on)
            on == false -> Texts.get(R.string.cmd_state_turn_off)
            else -> Texts.get(R.string.cmd_state_toggle)
        }
        val suggestions = if (target == SystemToggle.WIFI || target == SystemToggle.BLUETOOTH) emptyList() else stateSuggestions(command)
        return result(title, preview, Tone.OK, CommandAction.Toggle(target, on), suggestions.filter { arg.isBlank() || it.completion.foldAccents().startsWith((command.word + " " + arg).foldAccents()) })
    }

    /** "brilho 40", "brilho auto". */
    fun brightness(arg: String): Analysis {
        val title = Texts.get(R.string.cmd_brightness_title)
        val t = arg.trim().foldAccents()
        if (t.isEmpty()) return result(title, Texts.get(R.string.cmd_brightness_empty), Tone.IDLE, null, listTitle = Texts.get(R.string.cmd_list_format))
        if (t in setOf("auto", "automatico", "automatic") + TimeParse.words(R.string.cmd_word_auto)) {
            return result(title, Texts.get(R.string.cmd_brightness_auto), Tone.OK, CommandAction.Brightness(null))
        }
        val percent = t.removeSuffix("%").trim().toIntOrNull()?.takeIf { it in 0..100 }
            ?: return result(title, Texts.get(R.string.cmd_percent_invalid), Tone.ERROR, null)
        return result(title, "$percent%", Tone.OK, CommandAction.Brightness(percent))
    }

    /** "vol 30", "vol 0". */
    fun volume(arg: String): Analysis {
        val title = Texts.get(R.string.cmd_volume_title)
        val t = arg.trim().foldAccents()
        if (t.isEmpty()) return result(title, Texts.get(R.string.cmd_volume_empty), Tone.IDLE, null, listTitle = Texts.get(R.string.cmd_list_format))
        val percent = if (t in setOf("mudo", "mute") + TimeParse.words(R.string.cmd_word_mute)) {
            0
        } else {
            t.removeSuffix("%").trim().toIntOrNull()?.takeIf { it in 0..100 }
                ?: return result(title, Texts.get(R.string.cmd_percent_invalid), Tone.ERROR, null)
        }
        return result(title, "$percent%", Tone.OK, CommandAction.Volume(percent))
    }

    fun media(key: MediaKey): Analysis {
        val preview = when (key) {
            MediaKey.PLAY -> "▶"
            MediaKey.PAUSE -> "❚❚"
            MediaKey.NEXT -> "▶▶❙"
            MediaKey.PREVIOUS -> "❙◀◀"
        }
        return result(Texts.get(R.string.cmd_media_title), preview, Tone.OK, CommandAction.Media(key))
    }

    // ---- clipboard, settings, notes ----

    fun copy(arg: String): Analysis {
        val text = arg.trim()
        return result(
            Texts.get(R.string.cmd_copy_title),
            if (text.isEmpty()) Texts.get(R.string.cmd_text_empty) else "“$text”",
            if (text.isEmpty()) Tone.IDLE else Tone.OK,
            text.takeIf { it.isNotEmpty() }?.let { CommandAction.Copy(it) },
        )
    }

    fun paste(extras: CommandExtras): Analysis {
        val clip = extras.clipboard?.trim().orEmpty()
        return result(
            Texts.get(R.string.cmd_paste_title),
            if (clip.isEmpty()) Texts.get(R.string.cmd_paste_empty) else "“" + clip.take(PREVIEW_CHARS) + "”",
            if (clip.isEmpty()) Tone.WARN else Tone.OK,
            clip.takeIf { it.isNotEmpty() }?.let { CommandAction.FillLine(it.replace('\n', ' ')) },
        )
    }

    fun settings(arg: String): Analysis {
        val title = Texts.get(R.string.cmd_settings_title)
        val page = SettingsPage.of(arg)
        val word = BuiltInCommand.SETTINGS.word
        val matches = SettingsPage.matching(arg)
        return result(
            title,
            page?.word?.uppercase() ?: if (matches.isEmpty()) Texts.get(R.string.cmd_settings_not_found) else Texts.get(R.string.cmd_settings_empty),
            when {
                page != null -> Tone.OK
                matches.isEmpty() -> Tone.ERROR
                else -> Tone.IDLE
            },
            page?.let { CommandAction.OpenSettings(it) },
            suggestions = (if (page != null) emptyList() else matches).map { Suggestion("$word ${it.word}", it.word, Texts.get(R.string.cmd_kind_option)) },
        )
    }

    fun note(arg: String, extras: CommandExtras): Analysis {
        val text = arg.trim()
        val title = Texts.get(R.string.cmd_note_title)
        if (text.isNotEmpty()) return result(title, "+ “$text”", Tone.OK, CommandAction.AddNote(text))
        val copy = BuiltInCommand.COPY.word
        return result(
            title,
            if (extras.notes.isEmpty()) Texts.get(R.string.cmd_note_empty) else Texts.get(R.string.cmd_note_share, extras.notes.size),
            if (extras.notes.isEmpty()) Tone.IDLE else Tone.OK,
            extras.notes.takeIf { it.isNotEmpty() }?.let { CommandAction.ShareNotes(Notes.export(it)) },
            suggestions = extras.notes.take(EXTRA_SUGGESTIONS).map { Suggestion("$copy ${it.text}", it.text, Texts.get(R.string.cmd_kind_note)) },
            listTitle = Texts.get(R.string.cmd_note_list),
        )
    }

    // ---- calendar and contacts ----

    fun agenda(extras: CommandExtras): Analysis {
        val title = Texts.get(R.string.cmd_agenda_title)
        val events = extras.events
        if (events == null) {
            return result(title, Texts.get(R.string.cmd_agenda_pending), Tone.WARN, null, need = if (extras.calendarGranted) null else Need.CALENDAR)
        }
        val now = extras.clock()
        val nowMillis = extras.epochMillis(now)
        val upcoming = events.filter { it.end > nowMillis }.sortedBy { it.begin }
        val lines = upcoming.take(EXTRA_SUGGESTIONS).map { e ->
            val time = if (e.allDay) Texts.get(R.string.cmd_agenda_all_day) else TimeParse.formatClock(extras.timeOf(e.begin), now)
            "$time  ${e.title}"
        }
        return result(
            title,
            if (lines.isEmpty()) Texts.get(R.string.cmd_agenda_empty) else lines.joinToString("\n"),
            if (lines.isEmpty()) Tone.IDLE else Tone.OK,
            CommandAction.OpenCalendar(nowMillis),
        )
    }

    /** "ana?": the number and e-mail of a contact; Enter copies the number. */
    fun contactInfo(query: String, contacts: List<ContactEntry>, granted: Boolean): Analysis? {
        if (!granted) return null
        val q = query.trim().foldAccents()
        if (q.isEmpty()) return null
        val match = contacts.firstOrNull { c -> c.name.foldAccents().let { it.startsWith(q) || it.split(' ').any { w -> w.startsWith(q) } } }
            ?: return null
        val lines = listOf(match.name.uppercase(), match.number) + listOfNotNull(match.email.takeIf { it.isNotEmpty() })
        return result(Texts.get(R.string.cmd_contact_info_title), lines.joinToString("\n"), Tone.OK, CommandAction.Copy(match.number))
    }

    // ---- the device ----

    fun info(command: BuiltInCommand, extras: CommandExtras): Analysis {
        val title = command.word.uppercase()
        val info = extras.info ?: return result(title, Texts.get(R.string.cmd_info_pending), Tone.IDLE, null)
        val text = when (command) {
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
        return result(title, text, Tone.OK, CommandAction.Copy(text))
    }

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

    /**
     * "rede": the connection in use, one fact a line, and Enter copies it. What the system does not
     * give is left out; the Wi-Fi name says why when it is hidden.
     */
    fun network(extras: CommandExtras): Analysis {
        val title = BuiltInCommand.NETWORK.word.uppercase()
        val net = extras.network ?: return result(title, Texts.get(R.string.cmd_info_pending), Tone.IDLE, null)
        if (net.kind == NetKind.NONE) return result(title, Texts.get(R.string.cmd_net_offline), Tone.WARN, null)
        val text = networkText(net)
        return result(title + " · " + kindName(net.kind), text, if (net.internet) Tone.OK else Tone.WARN, CommandAction.Copy(text))
    }

    private fun kindName(kind: NetKind): String = when (kind) {
        NetKind.WIFI -> "WI-FI"
        NetKind.CELLULAR -> Texts.get(R.string.cmd_net_cellular)
        NetKind.ETHERNET -> "ETHERNET"
        NetKind.NONE -> ""
    }

    /**
     * "fast": a speed test. Enter starts it; on mobile data or another metered network the preview
     * warns how much data it may use first, and Enter is the go-ahead.
     */
    fun fast(extras: CommandExtras): Analysis {
        val title = Texts.get(R.string.cmd_fast_title)
        val net = extras.network
        if (net?.kind == NetKind.NONE) return result(title, Texts.get(R.string.cmd_net_offline), Tone.ERROR, null)
        return if (net?.metered == true) {
            result(title, Texts.get(R.string.cmd_fast_metered, Speed.MAX_MB), Tone.WARN, CommandAction.SpeedTest)
        } else {
            result(title, Texts.get(R.string.cmd_fast_ready), Tone.OK, CommandAction.SpeedTest)
        }
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

    private val hostPattern = Regex("^[a-zA-Z0-9.:-]+$")

    fun ping(arg: String): Analysis {
        val host = arg.trim()
        val title = "PING"
        return when {
            host.isEmpty() -> result(title, Texts.get(R.string.cmd_ping_empty), Tone.IDLE, null, listTitle = Texts.get(R.string.cmd_list_destination))
            !hostPattern.matches(host) -> result(title, Texts.get(R.string.cmd_ping_invalid), Tone.ERROR, null)
            else -> result(title, host.uppercase(), Tone.OK, CommandAction.Ping(host))
        }
    }

    // ---- AI ----

    /**
     * "ia [pergunta]" talks, "ia fazer [pedido]" turns the request into a command and "ia criar
     * [descrição]" writes a new one. Until a key is saved Enter opens the settings where it goes.
     */
    fun ai(arg: String, extras: CommandExtras): Analysis {
        val title = Texts.get(R.string.cmd_ai_title)
        val service = extras.ai ?: return result(title, Texts.get(R.string.cmd_ai_locked), Tone.WARN, CommandAction.Ai(AiMode.SETUP, ""))
        val text = arg.trim()
        val first = text.takeWhile { !it.isWhitespace() }
        val mode = AiMode.of(first)
        var prompt = if (mode != null) text.substring(first.length).trim() else text
        var chosen = mode ?: AiMode.CHAT
        // "ia criar tema de halloween" asks for a theme, not for a command.
        val next = prompt.takeWhile { !it.isWhitespace() }
        val nextWord = next.foldAccents()
        if (chosen == AiMode.CREATE && nextWord.isNotEmpty() && (nextWord in AiMode.THEME.words() || nextWord in BuiltInCommand.THEME.words())) {
            chosen = AiMode.THEME
            prompt = prompt.substring(next.length).trim()
        }
        val word = BuiltInCommand.AI.word
        // While nothing or only part of a mode word is typed, the modes are offered.
        val offers = if (mode == null && ' ' !in text) {
            AiMode.OFFERED.filter { it.word.foldAccents().startsWith(first.foldAccents()) }
                .map { Suggestion("$word ${it.word} ", modeLabel(it), Texts.get(R.string.cmd_kind_option)) }
        } else {
            emptyList()
        }
        val heading = title + " · " + modeLabel(chosen) + " · " + service.substringBefore(" · ").uppercase()
        if (chosen == AiMode.THEME) {
            // A theme needs no request either: Enter alone asks for a surprise.
            return result(heading, prompt.ifEmpty { Texts.get(R.string.cmd_ai_theme_empty) }, Tone.OK, CommandAction.Ai(chosen, prompt), suggestions = offers)
        }
        if (chosen == AiMode.FOLDERS) {
            // Folders need no request: Enter alone sorts the apps the AI's way.
            val (request, force) = AiFolders.splitForce(prompt)
            val hint = request.ifEmpty { Texts.get(R.string.cmd_ai_folders_empty) }
            val text = if (force) hint + " · " + Texts.get(R.string.cmd_ai_folders_force) else hint
            return result(heading, text, if (force) Tone.WARN else Tone.OK, CommandAction.Ai(chosen, request, force), suggestions = offers)
        }
        return when {
            prompt.isEmpty() -> result(heading, Texts.get(emptyHint(chosen)), Tone.IDLE, null, suggestions = offers)
            else -> result(heading, prompt, Tone.OK, CommandAction.Ai(chosen, prompt), suggestions = offers)
        }
    }

    private fun modeLabel(mode: AiMode): String = Texts.get(
        when (mode) {
            AiMode.RUN -> R.string.cmd_ai_mode_run
            AiMode.CREATE -> R.string.cmd_ai_mode_create
            AiMode.FOLDERS -> R.string.cmd_ai_mode_folders
            AiMode.THEME -> R.string.cmd_ai_mode_theme
            else -> R.string.cmd_ai_mode_chat
        },
    )

    private fun emptyHint(mode: AiMode): Int = when (mode) {
        AiMode.RUN -> R.string.cmd_ai_run_empty
        AiMode.CREATE -> R.string.cmd_ai_create_empty
        else -> R.string.cmd_ai_chat_empty
    }

    private const val PREVIEW_CHARS = 120
    const val EXTRA_SUGGESTIONS = 6
}
