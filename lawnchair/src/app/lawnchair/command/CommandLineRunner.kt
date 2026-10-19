package app.lawnchair.command

import android.content.Context
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A full command line run outside the command bar, as if it had been typed there and confirmed with
 * Enter: the same commands, custom actions and aliases. Used by widgets that run a command on a tap.
 */
object CommandLineRunner {

    /** What [text] does with the user's current apps, contacts, actions and aliases. */
    suspend fun analyze(context: Context, text: String): Analysis = withContext(Dispatchers.IO) {
        CommandEngine.analyze(
            text,
            CommandExecutor.loadApps(context),
            CommandExecutor.loadContacts(context),
            CommandExecutor.hasContactsPermission(context),
            CustomActionStore.load(context),
            AliasStore.load(context),
            CommandSources.load(context, CommandActivity.loadHistory(context)),
        )
    }

    /**
     * Runs [text] and returns the analysis it ran with, so a caller can explain a failure.
     * [ran] is false when the line is incomplete or no app could handle it.
     * [output] holds the result of the command when it produces one.
     */
    suspend fun run(context: Context, text: String): Result {
        val analysis = analyze(context, text)
        val action = analysis.action ?: return Result(analysis, ran = false)
        // Starting activities from the main thread, as the command bar does.
        return when (action) {
            is CommandAction.Ping -> {
                val ping = withContext(Dispatchers.IO) { CommandSources.ping(action.host) }
                Result(analysis, ran = ping.isNotEmpty(), output = ping)
            }

            is CommandAction.Weather -> {
                val weather = withContext(Dispatchers.IO) { Weather.run(action) }
                Result(analysis, ran = weather.isNotEmpty(), output = weather)
            }

            // A script and a chain run as in the bar, with a sandbox of their own for this run.
            is CommandAction.Chain -> withSteps(context) { steps ->
                val outcome = ChainRunner.run(action, steps)
                Result(analysis, ran = outcome.ok, output = if (outcome.ok) outcome.result.shown().ifBlank { null } else ChainRunner.failure(action, outcome))
            }

            is CommandAction.Custom -> {
                if (action.action.kind == ActionKind.SCRIPT) {
                    withSteps(context) { steps ->
                        val result = steps.run(action, null)
                        Result(analysis, ran = result.ok, output = result.shown().ifBlank { null })
                    }
                } else if (action.action.kind == ActionKind.API) {
                    val providers = ProviderStore.load(context)
                    val out = withContext(Dispatchers.IO) {
                        ApiCall.run(action.action, action.text, providers, SecretStore.lookup(context))
                    }
                    Result(analysis, ran = true, output = out)
                } else {
                    val ran = withContext(Dispatchers.Main) { CommandExecutor.execute(context, action) }
                    Result(analysis, ran = ran, output = if (ran) analysis.preview.ifBlank { analysis.previewTitle } else null)
                }
            }

            is CommandAction.Calc -> {
                val ran = withContext(Dispatchers.Main) { CommandExecutor.execute(context, action) }
                Result(analysis, ran = ran, output = if (ran) context.getString(R.string.command_copied, action.result) else null)
            }

            is CommandAction.Copy -> {
                val ran = withContext(Dispatchers.Main) { CommandExecutor.execute(context, action) }
                Result(analysis, ran = ran, output = if (ran) context.getString(R.string.command_copied, action.text.lineSequence().first()) else null)
            }

            else -> {
                val ran = withContext(Dispatchers.Main) { CommandExecutor.execute(context, action) }
                val out = if (ran) analysis.preview.ifBlank { analysis.previewTitle } else null
                Result(analysis, ran = ran, output = out)
            }
        }
    }

    private suspend fun <T> withSteps(context: Context, block: suspend (StepExecutor) -> T): T {
        val scripts = ScriptRunner(context)
        val extras = CommandSources.load(context, CommandActivity.loadHistory(context))
        return try {
            block(StepExecutor(context, scripts, { extras.providers }, { extras }))
        } finally {
            scripts.close()
        }
    }

    data class Result(val analysis: Analysis, val ran: Boolean, val output: String? = null)
}
