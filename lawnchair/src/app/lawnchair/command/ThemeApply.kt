package app.lawnchair.command

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.wallpaper.WallpaperController
import com.android.launcher3.R

/**
 * Applies a whole theme of the user's. What the theme says nothing about stays as an earlier theme left it, so
 * when some of it is not the default the user is asked: put those back to the defaults, or keep them as they are.
 * Where no screen can ask (a scheduled chain, the watch) the theme is applied as it always was, keeping them.
 */
object ThemeApply {

    enum class Choice { DEFAULTS, KEEP }

    /**
     * Asks what to do with [left], in the look of the app; [onChoice] gets null when the user backs out. Needs the
     * screen of a [ComponentActivity] in [context]; false when there is none, and then nothing was asked.
     */
    fun ask(context: Context, left: List<ThemeElement>, onChoice: (Choice?) -> Unit): Boolean {
        val activity = (activityOf(context) as? ComponentActivity)?.takeUnless { it.isFinishing || it.isDestroyed } ?: return false
        var answered = false
        val dialog = ComponentDialog(activity)
        fun answer(choice: Choice?) {
            if (answered) return
            answered = true
            dialog.dismiss()
            onChoice(choice)
        }
        dialog.setContentView(
            ComposeView(activity).apply {
                setContent { LawnchairTheme { LeftoverPrompt(left.joinToString("\n") { "• " + it.desc.lowercase() }, ::answer) } }
            },
        )
        // After the content: the window takes the width of the screen and the card draws its own edges.
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            decorView.setPadding(0, 0, 0, 0)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.setOnCancelListener { answer(null) }
        dialog.show()
        return true
    }

    /** Applies [spec] with [choice] for the elements in [left]. */
    fun apply(context: Context, spec: ThemeSpec, left: List<ThemeElement>, choice: Choice): Boolean {
        if (choice == Choice.DEFAULTS) return ThemeStore.applyWithDefaults(context, spec, left)
        // Kept: the wallpaper on the screens is the user's from now on, so no later theme asks about it or takes it off.
        WallpaperController.keep(context, system = ThemeElement.WALLPAPER in left, lock = ThemeElement.LOCK in left)
        return ThemeStore.apply(context, spec)
    }

    /**
     * Applies [spec], asking first when it leaves out something that is not the default; [onDone] gets whether it was
     * applied (null: the user backed out). [onBefore] runs once the theme is going to be applied, after the answer.
     */
    fun run(context: Context, spec: ThemeSpec, onBefore: () -> Unit = {}, onDone: (Boolean?) -> Unit) {
        val left = ThemeStore.leftovers(context, spec)
        val asked = left.isNotEmpty() && ask(context, left) { choice ->
            if (choice == null) {
                onDone(null)
            } else {
                onBefore()
                onDone(apply(context, spec, left, choice))
            }
        }
        if (!asked) {
            onBefore()
            onDone(ThemeStore.apply(context, spec))
        }
    }

    private fun activityOf(context: Context): Activity? {
        var c = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}

/** The question, as a card in the colors and the font of the app; the message is just the list of what would be replaced. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeftoverPrompt(elements: String, onAnswer: (ThemeApply.Choice?) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        shape = AlertDialogDefaults.shape,
        color = AlertDialogDefaults.containerColor,
        tonalElevation = AlertDialogDefaults.TonalElevation,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.theme_leftover_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.theme_leftover_message, elements),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
            ) {
                TextButton(onClick = { onAnswer(null) }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = { onAnswer(ThemeApply.Choice.KEEP) }) { Text(stringResource(R.string.theme_leftover_keep)) }
                Button(onClick = { onAnswer(ThemeApply.Choice.DEFAULTS) }) { Text(stringResource(R.string.theme_leftover_defaults)) }
            }
        }
    }
}
