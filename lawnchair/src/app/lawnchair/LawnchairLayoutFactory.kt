package app.lawnchair

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import app.lawnchair.font.FontManager
import app.lawnchair.theme.ThemeProvider
import com.android.launcher3.BubbleTextView
import com.android.launcher3.R
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.views.DoubleShadowBubbleTextView

class LawnchairLayoutFactory(context: Context) :
    LayoutInflater.Factory2,
    SafeCloseable {

    private val fontManager by lazy { FontManager.INSTANCE.get(context) }
    private val constructorMap = mapOf<String, (Context, AttributeSet) -> View>(
        "Button" to ::Button,
        "TextView" to ::TextView,
        BubbleTextView::class.java.name to ::BubbleTextView,
        DoubleShadowBubbleTextView::class.java.name to ::DoubleShadowBubbleTextView,
    )

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet,
    ): View? {
        val view = constructorMap[name]?.let { it(context, attrs) }
        if (view is TextView) {
            // Terminal typography everywhere; views with their own customFontType override below.
            runCatching { fontManager.setCustomFont(view, R.id.font_base_icon) }
            runCatching { fontManager.overrideFont(view, attrs) }
            // Workspace labels use a fixed phosphor resource; follow the user's phosphor color instead.
            if (view.currentTextColor == ThemeProvider.NOSTROMO_PHOSPHOR) {
                view.setTextColor(ThemeProvider.INSTANCE.get(context).phosphorColor)
            }
        }
        return view
    }

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
        return onCreateView(null, name, context, attrs)
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}
