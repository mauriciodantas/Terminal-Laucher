package app.lawnchair.command

import android.content.Context
import com.android.launcher3.R
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs one command of the bar and returns its [StepResult], whatever its kind: an API asks its
 * host, a script runs in [scripts], the weather and ping fetch their text, and the rest go to
 * [CommandExecutor]. It is what a chain ("a | b", "a || b") runs each command with, and what
 * the bar uses for a single API or script, so both end the same way.
 */
class StepExecutor(
    private val context: Context,
    private val scripts: ScriptRunner,
    private val providers: () -> List<ApiProvider>,
    private val extras: () -> CommandExtras,
) : StepRunner {

    override suspend fun run(step: CommandAction, stdin: StepResult?): StepResult = when (step) {
        is CommandAction.Custom -> when (step.action.kind) {
            ActionKind.API -> withContext(Dispatchers.IO) {
                ApiCall.request(step.action, step.text, providers(), SecretStore.lookup(context), stdin = step.stdin)
            }

            ActionKind.SCRIPT -> {
                val script = step.action.script
                if (script == null) {
                    StepResult.error(context.getString(R.string.command_failed))
                } else {
                    scripts.run(script, ScriptInput(step.text, stdin), scriptContext(step.action.letter))
                }
            }

            else -> withContext(Dispatchers.Main) { CommandExecutor.executeStep(context, step) }
        }

        is CommandAction.Weather -> withContext(Dispatchers.IO) { StepResult.ok(text = Weather.run(step)) }

        is CommandAction.Ping -> withContext(Dispatchers.IO) {
            CommandSources.ping(step.host).let { if (it.isBlank()) StepResult.error(context.getString(R.string.command_failed)) else StepResult.ok(text = it) }
        }

        // The result goes on the clipboard and on to the next command.
        is CommandAction.Calc -> withContext(Dispatchers.Main) { CommandExecutor.executeStep(context, step).let { if (it.ok) StepResult.ok(text = step.result) else it } }

        is CommandAction.Chain -> ChainRunner.run(step, this).result

        else -> withContext(Dispatchers.Main) { CommandExecutor.executeStep(context, step) }
    }

    /** What "more" adds to the answer of the paginated API [step]: the next items and where to go on from. */
    suspend fun more(step: CommandAction.Custom, state: PageMore): MoreText = withContext(Dispatchers.IO) {
        ApiCall.more(step.action, step.text, providers(), SecretStore.lookup(context), state, stdin = step.stdin)
    }

    /** What a script gets as `ctx`: the date and time (never the clipboard or the location), the language, its name. */
    fun scriptContext(command: String): ScriptContext = ScriptContext(Variables.plain(extras()), Locale.getDefault().toLanguageTag(), command)
}
