package app.lawnchair.allapps.views

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import app.lawnchair.font.FontManager
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.theme.color.tokens.ColorTokens
import app.lawnchair.util.SECONDARY_TEXT_STRENGTH
import app.lawnchair.util.drawerTextColor
import com.android.launcher3.R

class SearchResultEmptyState(context: Context, attrs: AttributeSet?) :
    LinearLayout(context, attrs),
    SearchResultView {

    private lateinit var icon: ImageView
    private lateinit var title: TextView
    private lateinit var subtitle: TextView

    override fun onFinishInflate() {
        super.onFinishInflate()
        title = ViewCompat.requireViewById(this, R.id.empty_state_title)
        subtitle = ViewCompat.requireViewById(this, R.id.empty_state_subtitle)
        icon = ViewCompat.requireViewById(this, R.id.empty_state_icon)

        FontManager.INSTANCE.get(context).setCustomFont(title, R.id.font_heading)
        FontManager.INSTANCE.get(context).setCustomFont(subtitle, R.id.font_body)

        title.setTextColor(drawerTextColor(context))
        subtitle.setTextColor(drawerTextColor(context, SECONDARY_TEXT_STRENGTH))
        icon.setColorFilter(ColorTokens.ColorAccent.resolveColor(context))
    }

    override val isQuickLaunch: Boolean = false
    override fun launch(): Boolean = false

    override fun bind(
        target: SearchTargetCompat,
        shortcuts: List<SearchTargetCompat>,
    ) {
        val extras = target.extras
        val titleRes = extras.getInt("titleRes")
        val subtitleRes = extras.getInt("subtitleRes")

        if (titleRes != 0) title.setText(titleRes)
        if (subtitleRes != 0) subtitle.setText(subtitleRes)
    }
}
