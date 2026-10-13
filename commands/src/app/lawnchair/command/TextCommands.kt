package app.lawnchair.command

import app.lawnchair.util.Texts

/** "copiar texto": puts the text on the clipboard. */
object CopyCommand : BuiltIn(BuiltInCommand.COPY, R.string.cmd_usage_copy, R.string.cmd_help_copy) {

    override val requiresArgument = true

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val text = arg.trim()
        return commandResult(
            Texts.get(R.string.cmd_copy_title),
            if (text.isEmpty()) Texts.get(R.string.cmd_text_empty) else "“$text”",
            if (text.isEmpty()) Tone.IDLE else Tone.OK,
            text.takeIf { it.isNotEmpty() }?.let { CommandAction.Copy(it) },
        )
    }
}

/** "colar": the copied text goes on the command line, to be edited or run. */
object PasteCommand : BuiltIn(BuiltInCommand.PASTE, R.string.cmd_usage_paste, R.string.cmd_help_paste) {

    private const val PREVIEW_CHARS = 120

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val extras = ctx.extras
        val clip = extras.clipboard?.trim().orEmpty()
        return commandResult(
            Texts.get(R.string.cmd_paste_title),
            if (clip.isEmpty()) Texts.get(R.string.cmd_paste_empty) else "“" + clip.take(PREVIEW_CHARS) + "”",
            if (clip.isEmpty()) Tone.WARN else Tone.OK,
            clip.takeIf { it.isNotEmpty() }?.let { CommandAction.FillLine(it.replace('\n', ' ')) },
        )
    }
}

/** "nota comprar pão" keeps a note; "nota" alone shares them all. */
object NoteCommand : BuiltIn(BuiltInCommand.NOTE, R.string.cmd_usage_note, R.string.cmd_help_note) {

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val extras = ctx.extras
        val text = arg.trim()
        val title = Texts.get(R.string.cmd_note_title)
        if (text.isNotEmpty()) return commandResult(title, "+ “$text”", Tone.OK, CommandAction.AddNote(text))
        val copy = BuiltInCommand.COPY.word
        return commandResult(
            title,
            if (extras.notes.isEmpty()) Texts.get(R.string.cmd_note_empty) else Texts.get(R.string.cmd_note_share, extras.notes.size),
            if (extras.notes.isEmpty()) Tone.IDLE else Tone.OK,
            extras.notes.takeIf { it.isNotEmpty() }?.let { CommandAction.ShareNotes(Notes.export(it)) },
            suggestions = extras.notes.take(CommandEngine.EMPTY_SUGGESTIONS).map { Suggestion("$copy ${it.text}", it.text, Texts.get(R.string.cmd_kind_note)) },
            listTitle = Texts.get(R.string.cmd_note_list),
        )
    }
}

/** "agenda": what is left of today in the calendar; Enter opens it. */
object AgendaCommand : BuiltIn(BuiltInCommand.AGENDA, R.string.cmd_usage_agenda, R.string.cmd_help_agenda) {

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val extras = ctx.extras
        val title = Texts.get(R.string.cmd_agenda_title)
        val events = extras.events
        if (events == null) {
            return commandResult(title, Texts.get(R.string.cmd_agenda_pending), Tone.WARN, null, need = if (extras.calendarGranted) null else Need.CALENDAR)
        }
        val now = extras.clock()
        val nowMillis = extras.epochMillis(now)
        val upcoming = events.filter { it.end > nowMillis }.sortedBy { it.begin }
        val lines = upcoming.take(CommandEngine.EMPTY_SUGGESTIONS).map { e ->
            val time = if (e.allDay) Texts.get(R.string.cmd_agenda_all_day) else TimeParse.formatClock(extras.timeOf(e.begin), now)
            "$time  ${e.title}"
        }
        return commandResult(
            title,
            if (lines.isEmpty()) Texts.get(R.string.cmd_agenda_empty) else lines.joinToString("\n"),
            if (lines.isEmpty()) Tone.IDLE else Tone.OK,
            CommandAction.OpenCalendar(nowMillis),
        )
    }
}
