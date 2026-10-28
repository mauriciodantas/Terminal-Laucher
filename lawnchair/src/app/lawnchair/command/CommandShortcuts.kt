package app.lawnchair.command

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.lawnchair.theme.color.tokens.PhosphorColorToken
import com.android.launcher3.R

/**
 * A command pinned to the home screen: tapping it opens the bar and runs the command right away,
 * with the confirmations the bar always asks for actions that change something.
 */
object CommandShortcuts {

    /** The action of the shortcut's intent; a shortcut needs one, and it is the app's own. */
    private const val ACTION = "app.lawnchair.command.RUN_SHORTCUT"
    private const val MAX_LABEL = 24

    fun supported(context: Context): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /** Asks the system to pin [command]; the system shows its own confirmation. Says so with a toast when it cannot. */
    fun pin(context: Context, command: String): Boolean {
        val line = command.trim()
        if (line.isEmpty() || !supported(context)) {
            Toast.makeText(context, R.string.command_pin_unsupported, Toast.LENGTH_SHORT).show()
            return false
        }
        val label = if (line.length > MAX_LABEL) line.take(MAX_LABEL - 1) + "…" else line
        val info = ShortcutInfoCompat.Builder(context, "command_" + line.hashCode().toUInt().toString(16))
            .setShortLabel(label)
            .setLongLabel(line)
            .setIcon(IconCompat.createWithAdaptiveBitmap(icon(context)))
            .setIntent(CommandActivity.intent(context, text = line, run = true).setAction(ACTION))
            .build()
        val asked = ShortcutManagerCompat.requestPinShortcut(context, info, null)
        if (!asked) Toast.makeText(context, R.string.command_pin_unsupported, Toast.LENGTH_SHORT).show()
        return asked
    }

    /** A dark tile with the prompt in the phosphor color, in the safe area of an adaptive icon. */
    private fun icon(context: Context): Bitmap {
        val size = 216
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#050B14"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PhosphorColorToken(1f).resolveColor(context)
            typeface = Typeface.MONOSPACE
            isFakeBoldText = true
            textSize = size * 0.34f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(">_", size / 2f, size / 2f + paint.textSize * 0.32f, paint)
        return bitmap
    }
}
