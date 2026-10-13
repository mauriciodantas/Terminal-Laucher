package app.lawnchair.command

import app.lawnchair.util.Texts
import java.time.LocalDateTime

/** "alarme 630", "alarme 7h30 seg-sex": an alarm in the clock app, once or on some days. */
object AlarmCommand : BuiltIn(BuiltInCommand.ALARM, R.string.cmd_usage_alarm, R.string.cmd_help_alarm) {

    override val requiresArgument = true

    /** "630", "6:30", "0630" and "06 30" all give 06:30. Null when it is not a valid time. */
    fun parseTime(arg: String): Pair<Int, Int>? {
        val d = arg.filter { it.isDigit() }
        if (d.isEmpty() || d.length > 4) return null
        val hh: Int
        val mm: Int
        if (d.length >= 3) {
            hh = d.dropLast(2).toInt()
            mm = d.takeLast(2).toInt()
        } else {
            hh = d.toInt()
            mm = 0
        }
        return if (hh in 0..23 && mm in 0..59) hh to mm else null
    }

    /** "7h30 seg-sex": the time, and the days when the words after it name days. */
    fun splitAlarm(arg: String): Pair<String, List<Int>> {
        val words = arg.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (k in 1 until words.size) {
            val days = TimeParse.days(words.drop(k).joinToString(" ")) ?: continue
            return words.take(k).joinToString(" ") to days
        }
        return arg to emptyList()
    }

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val (timeText, days) = splitAlarm(arg)
        val time = parseTime(timeText)
        val clock = time?.let { "%02d:%02d".format(it.first, it.second) }
        val label = clock?.let { if (days.isEmpty()) it else it + " · " + TimeParse.formatDays(days) }
        return Analysis(
            suggestions = clock?.takeIf { days.isEmpty() }?.let { listOf(Suggestion(BuiltInCommand.ALARM.word + " " + it, it, Texts.get(R.string.cmd_kind_time))) }.orEmpty(),
            listTitle = Texts.get(R.string.cmd_list_format),
            needsContacts = false,
            previewTitle = Texts.get(R.string.cmd_alarm_title),
            preview = label?.let { Texts.get(R.string.cmd_alarm_next, it) } ?: Texts.get(R.string.cmd_alarm_empty),
            tone = if (label != null) Tone.OK else Tone.IDLE,
            action = time?.let { CommandAction.SetAlarm(it.first, it.second, days) },
        )
    }
}

/** "timer 5m": a countdown in the clock app. */
object TimerCommand : BuiltIn(BuiltInCommand.TIMER, R.string.cmd_usage_timer, R.string.cmd_help_timer) {

    override val requiresArgument = true

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val seconds = TimeParse.duration(arg)
        val title = Texts.get(R.string.cmd_timer_title)
        return when {
            arg.isBlank() -> commandResult(title, Texts.get(R.string.cmd_timer_empty), Tone.IDLE, null, listTitle = Texts.get(R.string.cmd_list_format))
            seconds == null -> commandResult(title, Texts.get(R.string.cmd_timer_invalid), Tone.ERROR, null, listTitle = Texts.get(R.string.cmd_list_format))
            else -> commandResult(title, TimeParse.formatDuration(seconds).uppercase(), Tone.OK, CommandAction.SetTimer(seconds), listTitle = Texts.get(R.string.cmd_list_format))
        }
    }
}

/** "lembrar em 30m tirar o bolo": a notification at a time. */
object RemindCommand : BuiltIn(BuiltInCommand.REMIND, R.string.cmd_usage_remind, R.string.cmd_help_remind) {

    override val requiresArgument = true

    override fun analyze(arg: String, ctx: CommandContext): Analysis = analyze(arg, ctx.extras)

    fun analyze(arg: String, extras: CommandExtras): Analysis {
        val title = Texts.get(R.string.cmd_remind_title)
        val words = arg.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val now = extras.clock()
        val time = Scheduling.parseWhen(words, now)
        val text = time?.let { words.drop(it.used).joinToString(" ") }.orEmpty()
        return when {
            words.isEmpty() -> commandResult(title, Texts.get(R.string.cmd_remind_empty), Tone.IDLE, null, listTitle = Texts.get(R.string.cmd_list_format))

            time == null -> commandResult(title, Texts.get(R.string.cmd_remind_no_time), Tone.ERROR, null, listTitle = Texts.get(R.string.cmd_list_format))

            text.isEmpty() -> commandResult(title, TimeParse.formatClock(time.at, now) + " · " + Texts.get(R.string.cmd_remind_what), Tone.IDLE, null)

            else -> commandResult(
                title,
                TimeParse.formatClock(time.at, now) + " · " + text.uppercase(),
                Tone.OK,
                CommandAction.Schedule(ScheduledJob(0, JobKind.REMIND, text, extras.epochMillis(time.at))),
            )
        }
    }
}

/** "agendados": the reminders, timed commands and rules waiting to run; picking one cancels it. */
object ScheduledCommand : BuiltIn(BuiltInCommand.SCHEDULED, R.string.cmd_usage_scheduled, R.string.cmd_help_scheduled) {

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val extras = ctx.extras
        val jobs = Jobs.sorted(extras.jobs)
        val cancel = BuiltInCommand.CANCEL.word
        return commandResult(
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
}

/** "cancelar 2": cancels the second job of "agendados". */
object CancelCommand : BuiltIn(BuiltInCommand.CANCEL, R.string.cmd_usage_cancel, R.string.cmd_help_cancel) {

    override val requiresArgument = true

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val extras = ctx.extras
        val jobs = Jobs.sorted(extras.jobs)
        val n = arg.trim().removePrefix("#").toIntOrNull()
        val job = n?.let { jobs.getOrNull(it - 1) }
        val cancel = BuiltInCommand.CANCEL.word
        return commandResult(
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
}

/**
 * "em 30m: tema escuro", "às 22h: silencio on", "quando carregar: wifi": any command at a time or
 * when something happens. Not a command of its own: it is the start of a line.
 */
internal object Scheduling {

    /** When a reminder or a scheduled command runs, parsed from the start of [words]. */
    data class When(val at: LocalDateTime, val used: Int)

    /**
     * "em 30m", "30m", "18h", "às 18h", "amanhã 9h", "amanhã às 9h". [used] is how many words it
     * took. A bare "2h" is a clock time; "em 2h" is two hours from now.
     */
    fun parseWhen(words: List<String>, now: LocalDateTime): When? {
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
                val event = TriggerEvent.of(spec) ?: return commandResult(
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
                    ?: return commandResult(Texts.get(R.string.cmd_schedule_title), Texts.get(R.string.cmd_remind_no_time), Tone.ERROR, null)
                job = ScheduledJob(0, JobKind.COMMAND, command, extras.epochMillis(time.at))
                label = Texts.get(R.string.cmd_schedule_title) + " · " + TimeParse.formatClock(time.at, now)
            }

            else -> return null
        }
        if (command.isEmpty()) return commandResult(label, Texts.get(R.string.cmd_schedule_what), Tone.IDLE, null)
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
}
