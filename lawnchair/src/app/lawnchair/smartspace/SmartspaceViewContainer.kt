package app.lawnchair.smartspace

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import app.lawnchair.LawnchairLauncher
import app.lawnchair.font.UiFont
import app.lawnchair.launcher
import app.lawnchair.smartspace.glance.GlancePanelController
import app.lawnchair.theme.LauncherGround
import app.lawnchair.theme.color.tokens.PhosphorColorToken
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.Smartspace
import com.android.launcher3.CheckLongPressHelper
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.R
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.views.OptionsPopupView
import kotlin.random.Random

class SmartspaceViewContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    private val previewMode: Boolean = false,
) : FrameLayout(context, attrs) {

    private val longPressHelper = CheckLongPressHelper(this) { performLongClick() }
    private val smartspaceView: View
    private val glance: GlancePanelController

    init {
        val inflater = LayoutInflater.from(context)
        // Nostromo terminal readout (clock, date, motion tracker) replaces the stock smartspace cards.
        smartspaceView = inflater.inflate(R.layout.smartspace_widget, this, false)
        applyPhosphor(smartspaceView)
        glance = GlancePanelController(context, smartspaceView, previewMode)
        val dp = InvariantDeviceProfile.INSTANCE.get(context).getDeviceProfile(context)
        val leftPadding = dp.widgetPadding.left
        val rightPadding = dp.widgetPadding.right
        smartspaceView.setPadding(leftPadding, top, rightPadding, bottom)
        setOnLongClickListener {
            openOptions()
            true
        }
        addView(smartspaceView)
    }

    /** Tints the terminal readout with the user's phosphor color. */
    private fun applyPhosphor(root: View) {
        val phosphor = PhosphorColorToken(1f).resolveColor(context)
        val dim = PhosphorColorToken(0.62f).resolveColor(context)
        root.findViewById<TextView>(R.id.nostromo_clock)?.apply {
            setTextColor(phosphor)
            setShadowLayer(8f, 0f, 0f, ColorUtils.setAlphaComponent(phosphor, 0x66))
        }
        root.findViewById<TextView>(R.id.nostromo_date)?.setTextColor(phosphor)
        root.findViewById<TextView>(R.id.nostromo_status)?.setTextColor(dim)
        root.findViewById<TextView>(R.id.nostromo_header)?.apply {
            setTextColor(phosphor)
            typeIn(context.getString(R.string.nostromo_header, batteryReadout()))
        }
        root.findViewById<TextView>(R.id.nostromo_quick_label)?.setTextColor(dim)

        // Motion tracker panel: thin dim border, solid phosphor title bar with dark text.
        val density = resources.displayMetrics.density
        root.findViewById<View>(R.id.nostromo_panel)?.background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(density.toInt().coerceAtLeast(1), PhosphorColorToken(0.32f).resolveColor(context))
        }
        root.findViewById<View>(R.id.nostromo_panel_title)?.setBackgroundColor(phosphor)
        root.findViewById<TextView>(R.id.nostromo_target_secondary)?.setTextColor(dim)
        applyFont(root)
        root.findViewById<TextView>(R.id.nostromo_target_primary)?.setTextColor(LauncherGround.ink(context))
    }

    /** Puts the font the user picked under Appearance on the readout; the clock follows the pixel switch. */
    private fun applyFont(root: View) {
        listOf(
            R.id.nostromo_header,
            R.id.nostromo_date,
            R.id.nostromo_status,
            R.id.nostromo_quick_label,
            R.id.nostromo_panel_name,
            R.id.nostromo_panel_state,
            R.id.nostromo_target_primary,
            R.id.nostromo_target_secondary,
        ).forEach { id -> root.findViewById<TextView>(id)?.let { UiFont.apply(it) } }
        listOf(R.id.nostromo_clock, R.id.nostromo_target_lead).forEach { id ->
            root.findViewById<TextView>(id)?.let { UiFont.applyToClock(it) }
        }
    }

    /** Battery percentage and a five-segment bar, e.g. "87% ▮▮▮▮▯". */
    private fun batteryReadout(): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (level < 0 || scale <= 0) return "--"
        val percent = level * 100 / scale
        val filled = Math.round(percent / 20f).coerceIn(0, 5)
        return "$percent% " + "\u25AE".repeat(filled) + "\u25AF".repeat(5 - filled)
    }

    private val glitchHandler = Handler(Looper.getMainLooper())
    private val glitchRunnable = object : Runnable {
        override fun run() {
            glitchClock()
            glitchHandler.postDelayed(this, Random.nextLong(18_000, 45_000))
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        glance.start()
        if (animationsEnabled(context)) {
            glitchHandler.postDelayed(glitchRunnable, Random.nextLong(8_000, 20_000))
        }
    }

    override fun onDetachedFromWindow() {
        glitchHandler.removeCallbacksAndMessages(null)
        glance.stop()
        super.onDetachedFromWindow()
    }

    /** A brief horizontal jitter and flicker of the clock, like a failing signal. */
    private fun glitchClock() {
        val clock = smartspaceView.findViewById<View>(R.id.nostromo_clock) ?: return
        val shift = 4 * resources.displayMetrics.density
        val steps = floatArrayOf(shift, -shift * 0.6f, shift * 0.3f, 0f)
        val alphas = floatArrayOf(0.4f, 1f, 0.6f, 1f)
        steps.forEachIndexed { i, dx ->
            clock.postDelayed({
                clock.translationX = dx
                clock.alpha = alphas[i]
            }, i * 45L)
        }
    }

    private fun openOptions() {
        if (previewMode) return

        val launcher = context.launcher
        val pos = Rect()
        launcher.dragLayer.getDescendantRectRelativeToSelf(smartspaceView, pos)
        OptionsPopupView.show<LawnchairLauncher>(launcher, RectF(pos), listOf(getCustomizeOption()), true)
    }

    private fun getCustomizeOption() = OptionsPopupView.OptionItem(
        context,
        R.string.action_customize,
        R.drawable.ic_setting,
        StatsLogManager.LauncherEvent.IGNORE,
    ) {
        context.startActivity(PreferenceActivity.createIntent(context, Smartspace))
        true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        longPressHelper.onTouchEvent(ev)
        return longPressHelper.hasPerformedLongPress()
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        longPressHelper.onTouchEvent(ev)
        return true
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        longPressHelper.cancelLongPress()
    }
}
