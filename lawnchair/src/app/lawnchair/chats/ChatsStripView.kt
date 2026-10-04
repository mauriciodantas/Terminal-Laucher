package app.lawnchair.chats

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.smartspace.glance.ChatCandidate
import app.lawnchair.smartspace.glance.ChatShortcuts
import app.lawnchair.smartspace.glance.WhatsAppChatSource
import app.lawnchair.theme.color.tokens.PhosphorColorToken
import app.lawnchair.util.repeatOnAttached
import com.android.launcher3.BaseActivity
import com.android.launcher3.Insettable
import com.android.launcher3.R
import com.android.launcher3.notification.NotificationKeyData
import com.android.launcher3.notification.NotificationListener
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.views.ActivityContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Conversas rápidas": a strip of WhatsApp chat shortcuts sitting right above the dock icons, as
 * in the prototype. It is its own section of the home screen, not part of At a Glance.
 */
@SuppressLint("ViewConstructor")
class ChatsStripView(context: Context) :
    LinearLayout(context),
    Insettable {

    private val prefs2 = PreferenceManager2.getInstance(context)
    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    private val phosphor = PhosphorColorToken(1f).resolveColor(context)
    private val dim = PhosphorColorToken(0.62f).resolveColor(context)

    private val label = TextView(context)
    private val count = TextView(context)
    private val cells = LinearLayout(context)

    private var chats: List<ChatCandidate> = emptyList()
    private var unread: Map<String, Int> = emptyMap()

    private val notificationsListener = object : NotificationListener.NotificationsChangedListener {
        override fun onNotificationPosted(postedPackageUserKey: PackageUserKey?, notificationKey: NotificationKeyData?) =
            reloadSoon()

        override fun onNotificationRemoved(removedPackageUserKey: PackageUserKey?, notificationKey: NotificationKeyData?) =
            reloadSoon()

        override fun onNotificationFullRefresh(activeNotifications: MutableList<StatusBarNotification>?) = reloadSoon()
    }

    private val tick = object : Runnable {
        override fun run() {
            reloadSoon()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    private var attachedScope: kotlinx.coroutines.CoroutineScope? = null

    private fun deviceProfile() = ActivityContext.lookupContext<BaseActivity>(context).deviceProfile

    init {
        orientation = VERTICAL
        visibility = GONE
        clipChildren = false
        val side = (16 * density).toInt()
        setPadding(side, 0, side, (6 * density).toInt())

        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(label, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(count, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        label.apply {
            text = context.getString(R.string.glance_chats_label)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = ResourcesCompat.getFont(context, R.font.ibm_plex_mono_regular)
            letterSpacing = 0.2f
            includeFontPadding = false
            maxLines = 1
            setTextColor(dim)
        }
        count.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = ResourcesCompat.getFont(context, R.font.ibm_plex_mono_regular)
            letterSpacing = 0.14f
            includeFontPadding = false
            maxLines = 1
            setTextColor(dim)
        }
        cells.apply {
            orientation = HORIZONTAL
            clipChildren = false
        }
        addView(header)
        addView(
            cells,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = (6 * density).toInt() },
        )

        repeatOnAttached {
            attachedScope = this
            NotificationListener.addNotificationsChangedListener(notificationsListener)
            handler.postDelayed(tick, REFRESH_MS)
            // Any setting that changes what the strip shows reloads it.
            launch {
                combine(
                    prefs2.glanceChats.get(),
                    prefs2.glanceChatKeys.get(),
                    prefs2.glanceChatBusiness.get(),
                    prefs2.glanceChatBadge.get(),
                ) { _, _, _, _ -> }.collect { loadNow() }
            }
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                attachedScope = null
                NotificationListener.removeNotificationsChangedListener(notificationsListener)
                handler.removeCallbacksAndMessages(null)
            }
        }
    }

    private fun reloadSoon() {
        handler.removeCallbacks(reloadRunnable)
        handler.post(reloadRunnable)
    }

    private val reloadRunnable = Runnable { loadNow() }

    private fun loadNow() {
        if (!prefs2.glanceChats.firstCached()) {
            show(emptyList(), emptyMap())
            return
        }
        val includeBusiness = prefs2.glanceChatBusiness.firstCached()
        val chosen = ChatShortcuts.parseKeys(prefs2.glanceChatKeys.firstCached())
        val showBadge = prefs2.glanceChatBadge.firstCached()
        attachedScope?.launch {
            val shown = withContext(Dispatchers.Default) {
                ChatShortcuts.select(WhatsAppChatSource.load(context, includeBusiness), chosen)
            }
            val counts = if (showBadge && shown.isNotEmpty()) {
                withContext(Dispatchers.Default) { WhatsAppChatSource.unread(shown) }
            } else {
                emptyMap()
            }
            show(shown, counts)
        }
    }

    private fun show(shown: List<ChatCandidate>, counts: Map<String, Int>) {
        chats = shown
        unread = counts
        cells.removeAllViews()
        // The landscape layout moves the dock to the side, where there is no room for the strip.
        if (shown.isEmpty() || deviceProfile().isVerticalBarLayout) {
            visibility = GONE
            return
        }
        visibility = VISIBLE
        count.text = context.getString(R.string.glance_chats_count, shown.size)
        shown.forEach { cells.addView(cell(it, ChatShortcuts.badge(counts[it.key] ?: 0))) }
        // Empty slots keep every cell one fifth wide, as in the prototype grid.
        repeat((ChatShortcuts.MAX - shown.size).coerceAtLeast(0)) {
            cells.addView(View(context), LayoutParams(0, 1, 1f))
        }
    }

    private fun cell(chat: ChatCandidate, badgeText: String?): View {
        val size = (48 * density).toInt()
        val square = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor((phosphor and 0x00FFFFFF) or 0x14000000)
                setStroke(density.toInt().coerceAtLeast(1), (dim and 0x00FFFFFF) or 0x66000000)
            }
            addView(
                TextView(context).apply {
                    text = ChatShortcuts.initial(chat.label)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                    typeface = ResourcesCompat.getFont(context, R.font.vt323_regular)
                    includeFontPadding = false
                    gravity = Gravity.CENTER
                    setTextColor(phosphor)
                    setShadowLayer(6f, 0f, 0f, (phosphor and 0x00FFFFFF) or 0x66000000)
                },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }
        val frame = FrameLayout(context).apply {
            clipChildren = false
            addView(square, FrameLayout.LayoutParams(size, size))
            if (badgeText != null) {
                val out = (-6 * density).toInt()
                val h = (16 * density).toInt()
                addView(
                    TextView(context).apply {
                        text = badgeText
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                        typeface = ResourcesCompat.getFont(context, R.font.ibm_plex_mono_semibold)
                        includeFontPadding = false
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#FF05140C"))
                        setBackgroundColor(phosphor)
                        minWidth = h
                        minHeight = h
                        setPadding((3 * density).toInt(), 0, (3 * density).toInt(), 0)
                    },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
                        .apply { topMargin = out; marginEnd = out },
                )
            }
        }
        return LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            minimumHeight = (44 * density).toInt()
            contentDescription = context.getString(R.string.glance_chat_open, chat.label)
            addView(frame, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(
                TextView(context).apply {
                    text = chat.label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                    typeface = Typeface.MONOSPACE
                    includeFontPadding = false
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    setTextColor(dim)
                },
                LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = (4 * density).toInt() },
            )
            setOnClickListener { WhatsAppChatSource.open(context, chat) }
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
    }

    /** Sits right on top of the hotseat, whose bar already includes the bottom system inset. */
    override fun setInsets(insets: Rect) = placeAboveHotseat()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        placeAboveHotseat()
    }

    private fun placeAboveHotseat() {
        val lp = layoutParams as? FrameLayout.LayoutParams ?: return
        lp.gravity = Gravity.BOTTOM
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        lp.bottomMargin = deviceProfile().hotseatBarSizePx
        layoutParams = lp
    }

    companion object {
        private const val REFRESH_MS = 60_000L
    }
}
