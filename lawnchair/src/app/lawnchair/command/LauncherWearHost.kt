package app.lawnchair.command

import android.content.Context
import app.lawnchair.command.R as CommandR
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.theme.LauncherGround
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.util.Texts
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mdantas.terminal.wear.bridge.WearHost
import net.mdantas.terminal.wear.bridge.WearOutcome
import net.mdantas.terminal.wear.bridge.WearPlan
import net.mdantas.terminal.wear.protocol.ConfirmKind
import net.mdantas.terminal.wear.protocol.ConfirmPrompt
import net.mdantas.terminal.wear.protocol.RunStatus
import net.mdantas.terminal.wear.protocol.ThemeSource
import net.mdantas.terminal.wear.protocol.WatchAction
import net.mdantas.terminal.wear.protocol.WatchCommand
import net.mdantas.terminal.wear.protocol.WatchTheme
import net.mdantas.terminal.wear.protocol.WatchThemes
import net.mdantas.terminal.wear.protocol.Where

/**
 * The watch as a remote control of the bar. A line from the watch goes through the same analysis
 * the bar makes ([CommandLineRunner.analyze]) and the same executors; the rules that tell what it
 * does and when it asks for a confirmation are the engine's ([WatchRules]).
 */
object LauncherWearHost : WearHost {

    override val notificationIcon: Int = R.drawable.ic_command_notification

    override val watchedPreferences = listOf("nostromo" to setOf("custom_actions", "command_aliases", "api_providers", "command_favorites"))

    override suspend fun plan(context: Context, line: String): WearPlan {
        val analysis = CommandLineRunner.analyze(context, line)
        val action = analysis.action
            ?: return WearPlan.Unsupported(analysis.preview.ifBlank { Texts.get(CommandR.string.cmd_watch_not_a_command) })
        val effect = WatchRules.effect(action)
        if (effect == WatchRules.Effect.UNSUPPORTED) return WearPlan.Unsupported(Texts.get(CommandR.string.cmd_watch_unsupported))
        val where = whereOf(effect)
        val caution = WatchRules.caution(analysis, CommandSources.load(context, CommandActivity.loadHistory(context)).providers)
            ?: return WearPlan.Run(where)
        val kind = if (caution.kind == WatchRules.CautionKind.MUTATING) ConfirmKind.MUTATING else ConfirmKind.SENSITIVE
        return WearPlan.Confirm(ConfirmPrompt("", kind, caution.title, caution.detail, caution.confirmLabel), where)
    }

    override suspend fun run(context: Context, line: String): WearOutcome = runLine(context, line, depth = 0)

    private suspend fun runLine(context: Context, line: String, depth: Int): WearOutcome {
        val analysis = CommandLineRunner.analyze(context, line)
        val action = analysis.action
            ?: return failed(analysis.preview.ifBlank { Texts.get(CommandR.string.cmd_watch_not_a_command) })
        val effect = WatchRules.effect(action)
        if (effect == WatchRules.Effect.UNSUPPORTED) return WearOutcome(RunStatus.UNSUPPORTED, message = Texts.get(CommandR.string.cmd_watch_unsupported))
        val out = WatchRules.outcome(effect, execute(context, action))
        if (!out.ok) return failed(out.message.ifBlank { context.getString(R.string.command_failed) })
        val where = whereOf(effect)
        val step = out.action ?: return WearOutcome(RunStatus.OK, where, out.text)
        return finish(context, step, where, out.text, depth)
    }

    /** What the bar does once a chain ends with an action; the watch gets the final result. */
    private suspend fun finish(context: Context, step: StepAction, where: Where, text: String?, depth: Int): WearOutcome {
        StepActions.problem(step, depth)?.let { return failed(it) }
        return when (step) {
            // The watch cannot start the page itself: it offers the link, which opens by a tap on the phone.
            is StepAction.Open -> WearOutcome(RunStatus.OK, where, text, action = WatchAction.OpenOnPhone(step.uri))

            is StepAction.Copy -> WearOutcome(RunStatus.OK, where, text ?: step.text, action = WatchAction.Copy(step.text))

            is StepAction.Share -> WearOutcome(RunStatus.UNSUPPORTED, message = Texts.get(CommandR.string.cmd_watch_unsupported))

            is StepAction.Run -> {
                // A line that would ask for a confirmation in the bar waits for the user there.
                when (plan(context, step.line)) {
                    is WearPlan.Run -> runLine(context, step.line, depth + 1)
                    is WearPlan.Confirm -> failed(Texts.get(CommandR.string.cmd_watch_needs_confirm))
                    is WearPlan.Unsupported -> WearOutcome(RunStatus.UNSUPPORTED, message = Texts.get(CommandR.string.cmd_watch_unsupported))
                }
            }
        }
    }

    private suspend fun execute(context: Context, action: CommandAction): StepResult = when (action) {
        // The watch asks for the value; the bar's copy to the clipboard is not wanted here.
        is CommandAction.Calc -> StepResult.ok(text = action.result)

        is CommandAction.Chain -> CommandLineRunner.withSteps(context) { steps -> ChainRunner.run(action, steps).let { if (it.ok) it.result else StepResult.error(ChainRunner.failure(action, it)) } }

        is CommandAction.Weather, is CommandAction.Ping -> CommandLineRunner.withSteps(context) { it.run(action, null) }

        is CommandAction.Custom -> when (action.action.kind) {
            ActionKind.API, ActionKind.SCRIPT -> CommandLineRunner.withSteps(context) { it.run(action, null) }
            else -> withContext(Dispatchers.Main) { CommandExecutor.executeStep(context, action) }
        }

        else -> withContext(Dispatchers.Main) { CommandExecutor.executeStep(context, action) }
    }

    private fun failed(message: String) = WearOutcome(RunStatus.ERROR, message = message)

    private fun whereOf(effect: WatchRules.Effect): Where = when (effect) {
        WatchRules.Effect.TEXT -> Where.TEXT
        WatchRules.Effect.OPEN -> Where.PHONE_OPEN
        WatchRules.Effect.SEND -> Where.PHONE_SEND
        WatchRules.Effect.DONE, WatchRules.Effect.UNSUPPORTED -> Where.NONE
    }

    // ---- the list ----

    override suspend fun watchCommands(context: Context, usage: (String) -> Int): List<WatchCommand> = withContext(Dispatchers.IO) {
        val actions = CustomActionStore.load(context)
        val aliases = AliasStore.load(context)
        // Only the favorites travel: the watch is for what the user reaches for often.
        val favorites = FavoriteStore.load(context)
        val fromActions = actions.filter { it.letter.lowercase() in favorites }.map { a ->
            WatchCommand(
                letter = a.letter,
                label = a.label,
                arg = argOf(a.arg),
                where = whereOf(WatchRules.effect(a)),
                confirms = WatchRules.confirms(a),
                usage = usage(a.letter),
            )
        }
        val fromAliases = aliases.filter { it.name.lowercase() in favorites }.mapNotNull { alias ->
            // What the alias expands to tells where it lands; one that needs more than a word has no action yet.
            val action = CommandLineRunner.analyze(context, alias.name).action
            val effect = action?.let(WatchRules::effect)
            if (effect == WatchRules.Effect.UNSUPPORTED) return@mapNotNull null
            WatchCommand(
                letter = alias.name,
                label = alias.name,
                arg = if (action == null || "{text}" in alias.expansion) net.mdantas.terminal.wear.protocol.ArgKind.TEXT else net.mdantas.terminal.wear.protocol.ArgKind.NONE,
                where = effect?.let(::whereOf) ?: Where.TEXT,
                confirms = WatchRules.confirms(alias, actions),
                usage = usage(alias.name),
            )
        }
        (fromActions + fromAliases).distinctBy { it.letter.lowercase() }
    }

    private fun argOf(arg: ArgKind) = when (arg) {
        ArgKind.NONE -> net.mdantas.terminal.wear.protocol.ArgKind.NONE
        ArgKind.TEXT -> net.mdantas.terminal.wear.protocol.ArgKind.TEXT
        ArgKind.CONTACT -> net.mdantas.terminal.wear.protocol.ArgKind.CONTACT
        ArgKind.CONTACT_AND_TEXT -> net.mdantas.terminal.wear.protocol.ArgKind.CONTACT_TEXT
    }

    // ---- the theme ----

    /** The colors as the home screen draws them now: the pair after the system mode, the tones over that ground. */
    override suspend fun watchTheme(context: Context): WatchTheme {
        val prefs = PreferenceManager2.getInstance(context)
        val chosen = ThemeProvider.INSTANCE.get(context).chosenPhosphorColor
        val (accent, ground) = LauncherGround.drawn(chosen, prefs.launcherBackgroundColor.firstCached(), context)
        return WatchThemes.fromColors(
            source = ThemeSource.LAUNCHER_SPEC,
            accent = accent,
            background = ground,
            secondary = LauncherGround.secondary(context, ground),
            outline = LauncherGround.resolve(prefs.strokeColorStyle.firstCached(), context),
            onAccent = LauncherGround.onAccent(accent, ground),
            secondaryMix = LauncherGround.SECONDARY_MIX,
        )
    }
}
