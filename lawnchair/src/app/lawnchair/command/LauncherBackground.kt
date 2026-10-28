package app.lawnchair.command

import android.content.Context
import android.content.Intent
import app.lawnchair.command.R as CommandR
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs the lines the bar handed to [BackgroundRunService]: the AI questions and the commands that
 * wait on a network or a script. The bar can be closed meanwhile; the result waits in the
 * notification and in the launcher bar's list of runs.
 */
object LauncherBackground : BackgroundRunner {

    private lateinit var app: Context

    fun install(context: Context) {
        app = context.applicationContext
        BackgroundRuns.runner = this
    }

    override val icon: Int get() = R.drawable.ic_command_notification

    override fun openBar(jobId: Int): Intent = Intent(app, CommandActivity::class.java)
        .putExtra(BackgroundRuns.EXTRA_JOB, jobId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    override suspend fun run(job: BackgroundJob, history: List<AiTurn>, report: (Int, Int, String?) -> Unit): BackgroundOutcome {
        val analysis = CommandLineRunner.analyze(app, job.command)
        val action = analysis.action ?: return BackgroundOutcome(false, app.getString(R.string.cmd_run_failed, job.command))
        return when (action) {
            is CommandAction.Ai -> askAi(action, history, report)

            is CommandAction.Chain -> CommandLineRunner.withSteps(app) { steps ->
                val total = action.steps.size
                var done = 0
                report(0, total, null)
                val counting = StepRunner { step, stdin ->
                    report(++done, total, null)
                    steps.run(step, stdin)
                }
                val outcome = ChainRunner.run(action, counting)
                if (outcome.ok) {
                    BackgroundOutcome(true, outcome.result.shown())
                } else {
                    BackgroundOutcome(false, ChainRunner.failure(action, outcome))
                }
            }

            else -> {
                val result = CommandLineRunner.run(app, job.command)
                BackgroundOutcome(result.ran, result.output.orEmpty())
            }
        }
    }

    private const val THEME_TIMEOUT = 120

    private suspend fun askAi(action: CommandAction.Ai, history: List<AiTurn>, report: (Int, Int, String?) -> Unit): BackgroundOutcome {
        val config = AiStore.load(app) ?: return BackgroundOutcome(false, app.getString(CommandR.string.cmd_ai_locked))
        val now = java.time.ZonedDateTime.now()
        val apps = withContext(Dispatchers.IO) { CommandExecutor.loadApps(app) }
        val folders = if (action.mode == AiMode.FOLDERS) runCatching { FolderOrganizer.existing(app) }.getOrDefault(emptyList()) else emptyList()
        val theme = if (action.mode == AiMode.THEME) withContext(Dispatchers.IO) { CommandSources.load(app, CommandActivity.loadHistory(app)).theme } else null
        val context = withContext(Dispatchers.IO) {
            AiContext(
                custom = CustomActionStore.load(app),
                aliases = AliasStore.load(app),
                apps = apps.map { it.label },
                now = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm, EEEE", java.util.Locale.getDefault())) + " (" + now.zone + ")",
                host = HostAiData(folders = FolderOrganizer.describe(folders, apps), forceFolders = action.force, theme = theme),
            )
        }
        val system = AiPrompts.system(action.mode, context)
        val prompt = action.prompt.ifEmpty {
            when (action.mode) {
                AiMode.FOLDERS -> app.getString(R.string.cmd_ai_folders_request)
                AiMode.THEME -> app.getString(R.string.cmd_ai_theme_request)
                else -> ""
            }
        }
        val turns = (if (action.mode == AiMode.CHAT) history else emptyList()) + AiTurn(fromUser = true, text = prompt)
        return try {
            val reply = when (action.mode) {
                AiMode.CHAT -> AiClient.stream(config, system, turns) { report(0, 0, it) }
                AiMode.THEME, AiMode.CREATE -> AiClient.stream(config, system, turns, minTimeoutSeconds = THEME_TIMEOUT) {}
                else -> AiClient.send(config, system, turns)
            }
            when (action.mode) {
                AiMode.RUN -> {
                    val proposal = AiPrompts.command(reply.text)
                    BackgroundOutcome(proposal.command != null, proposal.command ?: proposal.note.ifEmpty { app.getString(R.string.cmd_ai_no_command) }, prefill = proposal.command)
                }

                AiMode.CREATE -> if (LauncherAiPrompts.createdTheme(reply.text) == null && AiPrompts.pack(reply.text) == null) {
                    BackgroundOutcome(false, app.getString(R.string.cmd_ai_create_failed))
                } else {
                    BackgroundOutcome(true, app.getString(CommandR.string.cmd_bg_review), reply = reply.turn)
                }

                AiMode.THEME -> if (LauncherAiPrompts.theme(reply.text) == null) {
                    BackgroundOutcome(false, app.getString(R.string.cmd_ai_theme_failed))
                } else {
                    BackgroundOutcome(true, app.getString(CommandR.string.cmd_bg_review), reply = reply.turn)
                }

                AiMode.FOLDERS -> {
                    val plan = AiFolders.plan(reply.text, apps, folders, action.force)
                    when {
                        plan == null -> BackgroundOutcome(false, app.getString(R.string.cmd_ai_folders_failed))
                        plan.isEmpty -> BackgroundOutcome(false, plan.note.ifEmpty { app.getString(R.string.cmd_ai_folders_none) })
                        else -> BackgroundOutcome(true, app.getString(CommandR.string.cmd_bg_review), reply = reply.turn)
                    }
                }

                else -> BackgroundOutcome(true, reply.text, reply = reply.turn)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            BackgroundOutcome(false, AiClient.describe(app, e))
        }
    }
}
