package app.lawnchair.allapps.views

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import app.lawnchair.font.FontManager
import app.lawnchair.launcher
import app.lawnchair.search.adapter.META_PREFIX
import app.lawnchair.search.adapter.SPACE
import app.lawnchair.search.adapter.SPACE_MINI
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.theme.color.tokens.ColorTokens
import app.lawnchair.theme.color.tokens.PhosphorColorToken
import com.android.launcher3.R
import com.android.systemui.shared.system.BlurUtils

class SearchResultText(context: Context, attrs: AttributeSet?) :
    LinearLayout(context, attrs),
    SearchResultView {

    private val launcher = context.launcher
    private lateinit var title: TextView

    override fun onFinishInflate() {
        super.onFinishInflate()
        onFocusChangeListener = launcher.focusHandler
        title = ViewCompat.requireViewById(this, R.id.title)
        if (BlurUtils.supportsBlursOnWindows()) {
            title.setTextColor(ColorTokens.TextColorPrimary.resolveColor(context))
        } else {
            title.setTextColor(ColorTokens.ColorAccent.resolveColor(context))
        }
        FontManager.INSTANCE.get(context).setCustomFont(title, R.id.font_heading)
    }

    /** Terminal section bars: PROGRAMAS is solid phosphor, other sections use the dim line color. */
    private fun styleTitle(rawTitle: String, isMeta: Boolean) {
        if (rawTitle == SPACE || rawTitle == SPACE_MINI) return
        val density = resources.displayMetrics.density
        val phosphor = PhosphorColorToken(1f).resolveColor(context)
        title.isAllCaps = true
        title.letterSpacing = 0.1f
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        if (isMeta) {
            title.background = null
            title.setPadding((12 * density).toInt(), 0, 0, 0)
            title.setTextColor(PhosphorColorToken(0.62f).resolveColor(context))
            return
        }
        val solid = rawTitle == context.getString(R.string.nostromo_section_programs)
        title.setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
        title.background = ColorDrawable(if (solid) phosphor else PhosphorColorToken(0.32f).resolveColor(context))
        title.setTextColor(if (solid) PhosphorColorToken.GROUND else phosphor)
        (title.layoutParams as? MarginLayoutParams)?.marginStart = (12 * density).toInt()
    }

    override val isQuickLaunch: Boolean get() = false
    override val titleText: CharSequence? get() = title.text
    override fun launch(): Boolean = false

    override fun bind(target: SearchTargetCompat, shortcuts: List<SearchTargetCompat>) {
        val rawTitle = target.searchAction?.title?.toString().orEmpty()
        val isMeta = rawTitle.startsWith(META_PREFIX)
        title.text = rawTitle.removePrefix(META_PREFIX)
        val titleText = title.text
        styleTitle(rawTitle, isMeta)
        val res = when (titleText) {
            SPACE -> resources.getDimensionPixelSize(R.dimen.space_layout_height)
            SPACE_MINI -> resources.getDimensionPixelSize(R.dimen.space_layout_mini_height)
            else -> resources.getDimensionPixelSize(R.dimen.search_result_text_height)
        }
        val params = this.layoutParams
        params.width = LayoutParams.MATCH_PARENT
        if (titleText == SPACE || titleText == SPACE_MINI) {
            params.height = res
            minimumHeight = 0
        } else {
            params.height = LayoutParams.WRAP_CONTENT
            minimumHeight = if (isMeta) 0 else resources.getDimensionPixelSize(R.dimen.nostromo_section_header_height)
        }
        this.layoutParams = params
    }
}
