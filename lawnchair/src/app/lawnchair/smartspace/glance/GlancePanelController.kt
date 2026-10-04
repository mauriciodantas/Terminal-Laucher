package app.lawnchair.smartspace.glance

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.smartspace.model.SmartspaceAction
import app.lawnchair.smartspace.model.SmartspaceTarget
import app.lawnchair.smartspace.provider.SmartspaceProvider
import app.lawnchair.theme.color.tokens.PhosphorColorToken
import com.android.launcher3.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Feeds the At a Glance panel of the home screen: it reads the smartspace targets, lets
 * [GlanceEngine] pick and order the tabs, and draws the selected one.
 */
class GlancePanelController(
    private val context: Context,
    private val root: View,
    private val previewMode: Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val prefs2 = PreferenceManager2.getInstance(context)
    private val handler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null

    private var latest: List<SmartspaceTarget> = emptyList()
    private var actions: Map<String, SmartspaceAction?> = emptyMap()
    private var panel = GlancePanel(emptyList(), null)
    private var selectedId: String? = null

    private val phosphor = PhosphorColorToken(1f).resolveColor(context)
    private val dim = PhosphorColorToken(0.62f).resolveColor(context)
    private val urgentColor = Color.parseColor("#FFFF5A45")

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    fun start() {
        refresh()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = newScope
        newScope.launch {
            // previewTargets leaves out the "tap to set up" notice: a source that still needs
            // permission must not take over the panel, the sources that work are shown anyway.
            SmartspaceProvider.INSTANCE.get(context).previewTargets
                .catch { latest = emptyList() }
                .collect {
                    latest = it
                    refresh()
                }
        }
        handler.postDelayed(tick, REFRESH_MS)
        if (!previewMode) {
            runCatching {
                context.getSystemService(CameraManager::class.java)
                    ?.registerTorchCallback(torchCallback, handler)
            }
        }
    }

    fun stop() {
        runCatching { context.getSystemService(CameraManager::class.java)?.unregisterTorchCallback(torchCallback) }
        scope?.cancel()
        scope = null
        handler.removeCallbacksAndMessages(null)
    }

    private fun settings() = GlanceSettings(
        maxTargets = prefs2.smartspacerMaxCount.firstCached(),
        enabledKinds = buildSet {
            add(GlanceKind.AVISO)
            if (prefs2.glanceAgenda.firstCached()) add(GlanceKind.AGENDA)
            if (prefs2.glanceWeather.firstCached()) add(GlanceKind.CLIMA)
            if (prefs2.smartspaceNowPlaying.firstCached()) add(GlanceKind.MIDIA)
            if (prefs2.glanceAlarm.firstCached()) add(GlanceKind.ALARME)
            if (prefs2.glanceReminders.firstCached()) add(GlanceKind.LEMBRETE)
            if (prefs2.glanceBluetooth.firstCached()) add(GlanceKind.BATERIA)
        },
        autoPriority = prefs2.glanceAutoPriority.firstCached(),
        urgentWindowMinutes = GlanceEngine.normalizeLeadMinutes(prefs2.glanceLeadMinutes.firstCached()),
    )

    /** Updates the line under the date: sound profile, storage and memory, or the fixed text. */
    private fun renderStatusLine() {
        val view = root.findViewById<TextView>(R.id.nostromo_status) ?: return
        if (!prefs2.glanceStatusLine.firstCached()) {
            view.text = StatusLine.FALLBACK
            return
        }
        val audio = context.getSystemService(android.media.AudioManager::class.java)
        val notifications = context.getSystemService(android.app.NotificationManager::class.java)
        val dnd = notifications?.currentInterruptionFilter.let {
            it != null && it != android.app.NotificationManager.INTERRUPTION_FILTER_ALL &&
                it != android.app.NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }
        val profile = audio?.let { StatusLine.soundProfile(it.ringerMode, dnd) }
        val storage = runCatching {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            StatusLine.percentUsed(stat.totalBytes - stat.availableBytes, stat.totalBytes)
        }.getOrNull()
        val ram = runCatching {
            val info = android.app.ActivityManager.MemoryInfo()
            context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(info)
            StatusLine.percentUsed(info.totalMem - info.availMem, info.totalMem)
        }.getOrNull()
        view.text = StatusLine.format(profile, storage, ram)
    }

    private var torchOn = false
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            torchOn = enabled
            renderShortcuts()
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            torchOn = false
            renderShortcuts()
        }
    }

    /** The row of utility shortcuts under the panel, or nothing when the user turned it off. */
    private fun renderShortcuts() {
        val row = root.findViewById<LinearLayout>(R.id.nostromo_shortcuts) ?: return
        row.removeAllViews()
        if (!prefs2.glanceShortcuts.firstCached()) {
            row.visibility = View.GONE
            root.findViewById<View>(R.id.nostromo_quick_label)?.visibility = View.VISIBLE
            return
        }
        row.visibility = View.VISIBLE
        // The panel only has two cell rows: the shortcuts take the place of the "quick access" label.
        root.findViewById<View>(R.id.nostromo_quick_label)?.visibility = View.GONE
        val density = context.resources.displayMetrics.density
        GlanceShortcut.values().forEachIndexed { index, shortcut ->
            val active = shortcut == GlanceShortcut.TORCH && torchOn
            row.addView(
                TextView(context).apply {
                    text = if (shortcut == GlanceShortcut.TORCH) GlanceShortcut.torchLabel(torchOn) else shortcut.label
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
                    typeface = Typeface.MONOSPACE
                    letterSpacing = 0.05f
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setTextColor(if (active) phosphor else dim)
                    background = GradientDrawable().apply {
                        setColor(if (active) (phosphor and 0x00FFFFFF) or 0x29000000 else Color.TRANSPARENT)
                        setStroke(density.toInt().coerceAtLeast(1), (dim and 0x00FFFFFF) or 0x66000000)
                    }
                    minHeight = (30 * density).toInt()
                    contentDescription = shortcut.label
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (index > 0) marginStart = (6 * density).toInt()
                    }
                    if (!previewMode) setOnClickListener { run(shortcut) }
                },
            )
        }
    }

    private fun run(shortcut: GlanceShortcut) {
        runCatching {
            when (shortcut) {
                GlanceShortcut.TORCH -> toggleTorch()
                GlanceShortcut.CALCULATOR -> openCalculator()
                GlanceShortcut.CAMERA -> startApp(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                GlanceShortcut.CLOCK -> startApp(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            }
        }
    }

    /**
     * The system's default calculator when there is one; many phones (Samsung among them) do not
     * declare it, so the known calculator packages and any app labelled "calculator" come next.
     */
    private fun openCalculator() {
        val pm = context.packageManager
        val byCategory = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
        val intent = byCategory.takeIf { pm.resolveActivity(it, 0) != null }
            ?: GlanceShortcut.CALCULATOR_PACKAGES.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
            ?: context.getSystemService(android.content.pm.LauncherApps::class.java)
                ?.getActivityList(null, android.os.Process.myUserHandle())
                ?.firstOrNull { GlanceShortcut.looksLikeCalculator(it.label.toString()) }
                ?.let { Intent.makeMainActivity(it.componentName) }
        if (intent == null) {
            android.widget.Toast.makeText(context, R.string.glance_no_calculator, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        startApp(intent)
    }

    private fun startApp(intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun toggleTorch() {
        val manager = context.getSystemService(CameraManager::class.java) ?: return
        val id = manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return
        manager.setTorchMode(id, !torchOn)
    }

    private fun refresh() {
        renderStatusLine()
        renderShortcuts()
        val glanceTargets = latest.map { it.toGlanceTarget() }
        actions = latest.associate { it.id to (it.headerAction ?: it.baseAction) }
        panel = GlanceEngine.build(glanceTargets, settings(), clock())
        if (selectedId == null || panel.tabs.none { it.id == selectedId }) {
            selectedId = panel.tabs.firstOrNull()?.id
        }
        // An event that just became urgent takes the front, unless the user is reading another tab.
        if (panel.urgentId != null && selectedId != panel.urgentId && !userPicked) {
            selectedId = panel.urgentId
        }
        render()
    }

    private var userPicked = false

    private fun SmartspaceTarget.toGlanceTarget(): GlanceTarget {
        val action = headerAction ?: baseAction
        return GlanceTarget(
            id = id,
            kind = GlanceKind.from(featureType, id),
            title = action?.title?.toString().orEmpty(),
            subtitle = action?.subtitle?.toString().orEmpty(),
            startsAtMillis = action?.extras
                ?.takeIf { it.containsKey(EXTRA_STARTS_AT) }
                ?.getLong(EXTRA_STARTS_AT),
            score = score,
        )
    }

    private fun render() {
        val title = root.findViewById<TextView>(R.id.nostromo_panel_name)
        val tag = root.findViewById<TextView>(R.id.nostromo_panel_state)
        val bar = root.findViewById<View>(R.id.nostromo_panel_title)
        val primary = root.findViewById<TextView>(R.id.nostromo_target_primary)
        val secondary = root.findViewById<TextView>(R.id.nostromo_target_secondary)
        val content = root.findViewById<View>(R.id.nostromo_target_content)
        val tabs = root.findViewById<LinearLayout>(R.id.nostromo_tabs) ?: return

        val selected = panel.tabs.firstOrNull { it.id == selectedId }
        val urgent = selected != null && panel.isUrgent(selected)

        if (selected == null) {
            title?.text = context.getString(R.string.nostromo_panel_title)
            tag?.text = context.getString(R.string.nostromo_panel_state)
            bar?.setBackgroundColor(phosphor)
            primary?.text = context.getString(R.string.glance_empty_primary)
            secondary?.text = context.getString(R.string.glance_empty_secondary)
            content?.setOnClickListener(null)
            content?.isClickable = false
            tabs.removeAllViews()
            bar?.setOnClickListener(null)
            return
        }

        title?.text = selected.kind.panelTitle
        tag?.text = if (urgent) {
            context.getString(R.string.glance_tag_priority)
        } else {
            "[%02d/%02d]".format(panel.tabs.indexOf(selected) + 1, panel.tabs.size)
        }
        bar?.setBackgroundColor(if (urgent) urgentColor else phosphor)

        // The time or temperature goes big on the left, like the prototype; the rest is the text.
        val leadView = root.findViewById<TextView>(R.id.nostromo_target_lead)
        val lead = GlanceEngine.lead(selected)
        leadView?.visibility = if (lead.value == null) View.GONE else View.VISIBLE
        leadView?.text = lead.value
        leadView?.setTextColor(if (urgent) urgentColor else phosphor)
        leadView?.setShadowLayer(8f, 0f, 0f, (if (urgent) urgentColor else phosphor) and 0x00FFFFFF or 0x66000000)

        val countdown = GlanceEngine.minutesUntil(selected.startsAtMillis, clock())
            ?.let { GlanceEngine.countdownLabel(it) }
        val parts = lead.text.split(" · ").filter { it.isNotBlank() }
        // The title bar already says the kind, so a bare value gets a short unit label instead.
        val main = parts.firstOrNull() ?: when (selected.kind) {
            GlanceKind.CLIMA -> "TEMPERATURA"
            GlanceKind.AGENDA -> "EVENTO"
            GlanceKind.ALARME -> "ALARME"
            GlanceKind.BATERIA -> "BATERIA"
            GlanceKind.LEMBRETE -> "LEMBRETE"
            else -> selected.kind.label
        }
        primary?.text = main
        secondary?.text = (parts.drop(1) + listOfNotNull(countdown)).joinToString(" · ")
        secondary?.setTextColor(if (urgent) urgentColor else dim)

        val action = actions[selected.id]
        if (!previewMode && action != null) {
            content?.setOnClickListener { launch(action) }
        } else {
            content?.setOnClickListener(null)
            content?.isClickable = false
        }

        tabs.removeAllViews()
        if (prefs2.glanceShortcuts.firstCached()) {
            // The shortcut row takes the place of the tabs; a tap on the title bar shows the next target.
            tabs.visibility = View.GONE
            bar?.setOnClickListener {
                val next = panel.tabs.indexOfFirst { it.id == selectedId } + 1
                userPicked = true
                selectedId = panel.tabs[next % panel.tabs.size].id
                render()
            }
        } else {
            tabs.visibility = View.VISIBLE
            bar?.setOnClickListener(null)
            bar?.isClickable = false
            panel.tabs.forEachIndexed { index, target ->
                tabs.addView(tabView(index, target, target.id == selectedId))
            }
        }
    }

    private fun tabView(index: Int, target: GlanceTarget, selected: Boolean): TextView {
        val density = context.resources.displayMetrics.density
        val color = if (panel.isUrgent(target)) urgentColor else phosphor
        return TextView(context).apply {
            text = GlanceEngine.tabLabel(index, target.kind)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.05f
            gravity = Gravity.CENTER
            maxLines = 1
            setTextColor(if (selected) color else dim)
            setBackgroundColor(if (selected) (color and 0x00FFFFFF) or 0x29000000 else Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            minHeight = (36 * density).toInt()
            contentDescription = target.kind.label
            setOnClickListener {
                userPicked = true
                selectedId = target.id
                render()
            }
        }
    }

    private fun launch(action: SmartspaceAction) {
        runCatching {
            when {
                action.onClick != null -> action.onClick.run()
                action.pendingIntent != null -> action.pendingIntent.send()
                action.intent != null -> context.startActivity(action.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    companion object {
        /** Optional extra a provider may set on an action: the event start, in epoch millis. */
        const val EXTRA_STARTS_AT = "glance_starts_at_millis"
        private const val REFRESH_MS = 60_000L
    }
}
