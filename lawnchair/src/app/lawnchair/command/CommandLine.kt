package app.lawnchair.command

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A full command line run outside the command bar, as if it had been typed there and confirmed with
 * Enter: the same commands, custom actions and aliases. Used by widgets that run a command on a tap.
 */
object CommandLine {

    /** What [text] does with the user's current apps, contacts, actions and aliases. */
    suspend fun analyze(context: Context, text: String): Analysis = withContext(Dispatchers.IO) {
        CommandEngine.analyze(
            text,
            CommandExecutor.loadApps(context),
            CommandExecutor.loadContacts(context),
            CommandExecutor.hasContactsPermission(context),
            CustomActionStore.load(context),
            AliasStore.load(context),
        )
    }

    /**
     * Runs [text] and returns the analysis it ran with, so a caller can explain a failure.
     * [ran] is false when the line is incomplete or no app could handle it.
     */
    suspend fun run(context: Context, text: String): Result {
        val analysis = analyze(context, text)
        val action = analysis.action ?: return Result(analysis, ran = false)
        // Starting activities from the main thread, as the command bar does.
        val ran = withContext(Dispatchers.Main) { CommandExecutor.execute(context, action) }
        return Result(analysis, ran)
    }

    data class Result(val analysis: Analysis, val ran: Boolean)
}
