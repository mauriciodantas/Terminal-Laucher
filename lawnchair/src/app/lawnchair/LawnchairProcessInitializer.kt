package app.lawnchair

import android.content.Context
import androidx.annotation.Keep
import app.lawnchair.bugreport.LawnchairBugReporter
import app.lawnchair.theme.color.tokens.PhosphorColorToken
import com.android.launcher3.icons.mono.ThemedIconDrawable
import com.android.quickstep.QuickstepProcessInitializer

@Keep
class LawnchairProcessInitializer(context: Context) : QuickstepProcessInitializer(context) {

    override fun init(context: Context) {
        LawnchairBugReporter.INSTANCE.get(context)
        // Nostromo terminal icons: phosphor glyph on a dark panel, tinted with the user-selected color.
        ThemedIconDrawable.COLORS_LOADER = {
            intArrayOf(
                PhosphorColorToken(0.10f).resolveColor(it),
                PhosphorColorToken(1f).resolveColor(it),
            )
        }
        super.init(context)
    }
}
