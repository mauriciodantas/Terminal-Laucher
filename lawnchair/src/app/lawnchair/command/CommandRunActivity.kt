package app.lawnchair.command

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.android.launcher3.R
import kotlinx.coroutines.launch

/**
 * Runs a command line sent by another app, such as Tasker or an automation app: an intent with
 * the action `<package>.RUN_COMMAND` and the line in the `command` extra. Only apps the user
 * granted the "run commands" permission to can send it. It has no screen of its own; being an
 * activity, the command may open apps, which a background receiver could not.
 */
class CommandRunActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val command = intent?.getStringExtra(EXTRA_COMMAND)?.trim().orEmpty()
        if (command.isEmpty()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        lifecycleScope.launch {
            val result = CommandLineRunner.run(this@CommandRunActivity, command)
            if (!result.ran) {
                Toast.makeText(this@CommandRunActivity, getString(R.string.cmd_run_failed, command), Toast.LENGTH_SHORT).show()
            }
            setResult(if (result.ran) Activity.RESULT_OK else Activity.RESULT_CANCELED)
            finish()
        }
    }

    companion object {
        const val EXTRA_COMMAND = "command"
    }
}
