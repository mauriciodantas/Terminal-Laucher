package app.lawnchair.command

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.lawnchair.font.FontCache
import app.lawnchair.font.googlefonts.GoogleFontsListing
import app.lawnchair.icons.shape.IconShape
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.smartspace.glance.GlanceAlign
import app.lawnchair.smartspace.glance.GlanceAnimationFile
import app.lawnchair.smartspace.glance.GlanceAnimationStyle
import app.lawnchair.smartspace.glance.GlanceClockFormat
import app.lawnchair.smartspace.glance.GlanceDateFormat
import app.lawnchair.smartspace.glance.GlanceDensity
import app.lawnchair.smartspace.glance.GlancePreset
import app.lawnchair.smartspace.glance.GlanceReadout
import app.lawnchair.smartspace.glance.GlanceShortcut
import app.lawnchair.smartspace.glance.GlanceTitleBar
import app.lawnchair.smartspace.glance.GlanceUrgentColor
import app.lawnchair.theme.ComplementaryColors
import app.lawnchair.theme.color.ColorOption
import com.android.launcher3.R
import com.patrykmichalik.opto.core.PreferenceImpl
import com.patrykmichalik.opto.core.setBlocking
import java.io.File
import org.json.JSONObject

/**
 * The theme on the phone: reading what Appearance has now, applying a [ThemeSpec] to it, the
 * themes the user saved by name and the theme file sent to somebody else.
 */
object ThemeStore {

    private const val STORE = "nostromo"
    private const val KEY = "command_themes"

    private fun prefs(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun load(context: Context): List<ThemeSpec> = prefs(context).getString(KEY, null)?.let { ThemeSpecs.decodeList(it) }.orEmpty()

    private fun save(context: Context, themes: List<ThemeSpec>) {
        prefs(context).edit().putString(KEY, ThemeSpecs.encodeList(themes)).apply()
    }

    /** Keeps [theme] under its name, in place of a saved one of the same name. */
    fun keep(context: Context, theme: ThemeSpec) {
        if (theme.name.isBlank()) return
        val themes = ThemeSpecs.upsert(load(context), ThemeImages.store(context, theme))
        save(context, themes)
        ThemeImages.prune(context, themes)
    }

    fun remove(context: Context, name: String) {
        val themes = ThemeSpecs.remove(load(context), name)
        save(context, themes)
        ThemeImages.prune(context, themes)
    }

    /** Keeps the theme on screen now as [name]. */
    fun saveCurrent(context: Context, name: String) = keep(context, current(context).copy(name = name))

    /** The ready-made looks of Appearance, with their names in the app language. */
    fun presets(context: Context): List<ThemeSpec> = ComplementaryColors.all.map {
        ThemeSpec(
            name = context.getString(it.name),
            accent = ThemeColor.Custom(it.accent),
            background = ThemeColor.Custom(it.background),
            // A pair brings no third color: the secondary text goes back to the dimmed accent, as in Appearance.
            secondary = it.secondary?.let { color -> ThemeColor.Custom(color) } ?: ThemeColor.Default,
        )
    }

    /** The Google Fonts families, for "tema fonte" to suggest; empty if the list cannot be read. */
    suspend fun fonts(context: Context): List<String> = runCatching {
        GoogleFontsListing.INSTANCE.get(context).getFonts().map { it.family }
    }.getOrDefault(emptyList())

    // ---- what is on screen ----

    /** Every element of the theme as it is now. */
    fun current(context: Context): ThemeSpec {
        val p = PreferenceManager2.getInstance(context)
        return ThemeSpec(
            accent = colorOf(p.accentColor.firstCached(p)),
            background = colorOf(p.launcherBackgroundColor.firstCached(p)),
            secondary = colorOf(p.secondaryColor.firstCached(p)),
            outline = colorOf(p.strokeColorStyle.firstCached(p)),
            dock = colorOf(p.hotseatBackgroundColor.firstCached(p)),
            folder = colorOf(p.folderColor.firstCached(p)),
            followSystem = p.followSystemColorMode.firstCached(p),
            font = fontOf(PreferenceManager.getInstance(context).fontWorkspace.get()),
            pixelClock = p.terminalPixelClock.firstCached(p),
            iconEffect = p.terminalIconEffect.firstCached(p),
            widgetEffect = p.widgetEffect.firstCached(p),
            widgetIntensity = p.widgetEffectIntensity.firstCached(p),
            crtEffect = p.crtEffect.firstCached(p),
            crtIntensity = p.crtEffectIntensity.firstCached(p),
            animation = p.screenAnimation.firstCached(p).takeIf { it in ThemeSpecs.ANIMATIONS },
            customAnimation = p.screenAnimationCustom.firstCached(p).takeIf { p.screenAnimation.firstCached(p) == "custom" }
                ?.let { runCatching { ThemeSpecs.customAnimation(org.json.JSONObject(it)) }.getOrNull() },
            showWallpaper = p.showSystemWallpaper.firstCached(p),
            wallpaperDim = p.wallpaperDim.firstCached(p),
            colorAsWallpaper = p.backgroundColorAsWallpaper.firstCached(p),
            // A custom corner shape has no name to carry: it is left out.
            iconShape = ThemeSpecs.shapeKey(p.iconShape.firstCached(p).toString()),
            folderShape = ThemeSpecs.shapeKey(p.folderShape.firstCached(p).toString()),
            glance = glanceNow(context, p),
        )
    }

    /** What the At a Glance panel has that is not how the app comes; null when it is all as it comes. */
    private fun glanceNow(context: Context, p: PreferenceManager2): GlanceSpec? {
        val style = p.glanceAnimation.firstCached(p)
        val lottie = style.takeIf { it == GlanceAnimationStyle.CUSTOM }
            ?.let { GlanceAnimationFile.file(context).takeIf { f -> f.isFile } }
            ?.let { runCatching { it.readText() }.getOrNull() }
            ?.takeIf { it.length <= ThemeSpecs.MAX_LOTTIE_CHARS && runCatching { JSONObject(it).optJSONArray("layers") != null }.getOrDefault(false) }
            ?.let { GlanceLottie.Json(it) }
        return GlanceSpec(
            show = false.takeIf { !p.enableSmartspace.firstCached(p) },
            animation = style.id.takeIf { style != GlanceAnimationStyle.OFF && style != GlanceAnimationStyle.CUSTOM },
            lottie = lottie,
            tint = false.takeIf { lottie != null && !p.glanceAnimationTint.firstCached(p) },
            shortcuts = false.takeIf { !p.glanceShortcuts.firstCached(p) },
            chats = false.takeIf { !p.glanceChats.firstCached(p) },
            look = lookNow(p),
        ).takeUnless { it.isEmpty }
    }

    /** The look settings that are not how the app comes; null when they all are. */
    private fun lookNow(p: PreferenceManager2): GlanceLook? {
        fun <C, S> PreferenceImpl<C, S>.changed(): C? = firstCached(p).takeIf { it != defaultValue }
        return GlanceLook(
            header = p.glanceShowHeader.changed(),
            date = p.glanceShowDate.changed(),
            status = p.glanceShowStatus.changed(),
            clockFormat = p.glanceClockFormat.changed()?.id,
            dateFormat = p.glanceDateFormat.changed()?.id,
            seconds = p.glanceClockSeconds.changed(),
            dateCaps = p.glanceDateCaps.changed(),
            clockSize = p.glanceClockScale.changed(),
            textSize = p.glanceTextScale.changed(),
            align = p.glanceAlign.changed()?.id,
            density = p.glanceDensity.changed()?.id,
            border = p.glancePanelBorder.changed(),
            borderWidth = p.glanceBorderWidth.changed(),
            radius = p.glanceCornerRadius.changed(),
            titleBar = p.glanceTitleBar.changed()?.id,
            glow = p.glanceGlow.changed(),
            dim = p.glanceDimLevel.changed(),
            urgent = p.glanceUrgentColor.changed()?.id,
            statusSound = p.glanceStatusSound.changed(),
            statusStorage = p.glanceStatusStorage.changed(),
            statusRam = p.glanceStatusRam.changed(),
            hiddenShortcuts = GlanceReadout.parseHidden(p.glanceShortcutsHidden.firstCached(p))
                .map { it.name.lowercase() }.takeIf { it.isNotEmpty() },
        ).takeUnless { it.isEmpty }
    }

    /**
     * The preset first, then each setting the theme names on its own. The final value of each one is worked out
     * here and written once, without comparing to the cache, which is stale right after a write and made a setting
     * the preset had just written look unchanged.
     */
    private fun applyLook(p: PreferenceManager2, l: GlanceLook) {
        val preset = l.preset?.let { id -> GlancePreset.entries.firstOrNull { it.name.equals(id, ignoreCase = true) } }
        fun <C, S> PreferenceImpl<C, S>.put(value: C?) {
            if (value != null) setBlocking(value)
        }
        p.glancePanelBorder.put(l.border ?: preset?.border)
        p.glanceTitleBar.put(l.titleBar?.let(GlanceTitleBar::fromId) ?: preset?.titleBar)
        p.glanceBorderWidth.put(l.borderWidth ?: preset?.borderWidth)
        p.glanceCornerRadius.put(l.radius ?: preset?.radius)
        p.glanceAlign.put(l.align?.let(GlanceAlign::fromId) ?: preset?.align)
        p.glanceDensity.put(l.density?.let(GlanceDensity::fromId) ?: preset?.density)
        p.glanceGlow.put(l.glow ?: preset?.glow)
        p.glanceShowHeader.put(l.header ?: preset?.header)
        p.glanceShowStatus.put(l.status ?: preset?.status)
        p.glanceClockFormat.put(l.clockFormat?.let(GlanceClockFormat::fromId) ?: preset?.clock)
        p.glanceDateFormat.put(l.dateFormat?.let(GlanceDateFormat::fromId) ?: preset?.date)
        p.glanceShowDate.put(l.date)
        p.glanceClockSeconds.put(l.seconds)
        p.glanceDateCaps.put(l.dateCaps)
        p.glanceClockScale.put(l.clockSize)
        p.glanceTextScale.put(l.textSize)
        p.glanceDimLevel.put(l.dim)
        p.glanceUrgentColor.put(l.urgent?.let(GlanceUrgentColor::fromId))
        p.glanceStatusSound.put(l.statusSound)
        p.glanceStatusStorage.put(l.statusStorage)
        p.glanceStatusRam.put(l.statusRam)
        l.hiddenShortcuts?.let { names ->
            val hidden = GlanceShortcut.entries.filter { it.name.lowercase() in names }.toSet()
            p.glanceShortcutsHidden.put(GlanceReadout.serializeHidden(hidden))
        }
    }

    private fun colorOf(option: ColorOption): ThemeColor = when (option) {
        is ColorOption.CustomColor -> ThemeColor.Custom(option.color or OPAQUE)
        ColorOption.SystemAccent -> ThemeColor.System
        ColorOption.WallpaperPrimary -> ThemeColor.Wallpaper
        else -> ThemeColor.Default
    }

    private fun optionOf(color: ThemeColor): ColorOption = when (color) {
        is ThemeColor.Custom -> ColorOption.CustomColor(color.argb or OPAQUE)
        ThemeColor.System -> ColorOption.SystemAccent
        ThemeColor.Wallpaper -> ColorOption.WallpaperPrimary
        ThemeColor.Default -> ColorOption.Default
    }

    /** The font as a theme can carry it; null for a .ttf of this phone, which does not travel. */
    private fun fontOf(font: FontCache.Font): ThemeFont? {
        val o = runCatching { JSONObject(font.toJsonString()) }.getOrNull() ?: return null
        val family = o.optString("family")
        return when (o.optString("className").substringAfterLast('$')) {
            "GoogleFont" -> ThemeFont.Google(family)
            "SystemFont" -> ThemeFont.System(family)
            "ResourceFont" -> if (family == ThemeFont.Default.label) ThemeFont.Default else null
            else -> null
        }
    }

    // ---- applying ----

    /**
     * Writes what [spec] sets. Only what differs is written, since some settings rebuild the home
     * screen (the outline restarts it). The background goes before the accent, as when a pair is
     * picked in Appearance. A pair that could not be read is refused as a whole. The images are set
     * afterwards, in the background, since a link has to be downloaded.
     */
    fun apply(context: Context, spec: ThemeSpec): Boolean {
        val now = current(context)
        if (ThemeSpecs.unreadable(spec, now)) return false
        val p = PreferenceManager2.getInstance(context)
        // Fixed colors are laid as asked: following the system would swap them back.
        val followSystem = spec.followSystem ?: if (spec.accent != null || spec.background != null) false else null
        followSystem?.let { p.followSystemColorMode.update(p, it) }
        spec.background?.let { p.launcherBackgroundColor.update(p, optionOf(it)) }
        spec.accent?.let { p.accentColor.update(p, optionOf(it)) }
        spec.secondary?.let { p.secondaryColor.update(p, optionOf(it)) }
        spec.dock?.let { p.hotseatBackgroundColor.update(p, optionOf(it)) }
        spec.folder?.let { p.folderColor.update(p, optionOf(it)) }
        spec.pixelClock?.let { p.terminalPixelClock.update(p, it) }
        spec.iconEffect?.let { p.terminalIconEffect.update(p, it) }
        spec.widgetEffect?.let { p.widgetEffect.update(p, it) }
        spec.widgetIntensity?.let { p.widgetEffectIntensity.update(p, it) }
        spec.crtEffect?.let { p.crtEffect.update(p, it) }
        spec.crtIntensity?.let { p.crtEffectIntensity.update(p, it) }
        spec.animation?.let { p.screenAnimation.update(p, it) }
        // What the theme brings wins over a named animation, and stays stored in case the user goes back to it.
        spec.customAnimation?.let {
            p.screenAnimationCustom.update(p, ThemeSpecs.customAnimationJson(it).toString())
            p.screenAnimation.update(p, "custom")
        }
        // An image as wallpaper is shown behind the home screen, and the color no longer painted over it.
        (spec.showWallpaper ?: spec.wallpaper?.let { true })?.let { p.showSystemWallpaper.update(p, it) }
        spec.wallpaperDim?.let { p.wallpaperDim.update(p, it) }
        (if (spec.wallpaper != null) false else spec.colorAsWallpaper)?.let { p.backgroundColorAsWallpaper.update(p, it) }
        if (spec.lockImage != null) p.backgroundColorOnLockScreen.update(p, false)
        spec.iconShape?.let { setShape(context, p.iconShape, it) }
        spec.folderShape?.let { setShape(context, p.folderShape, it) }
        ThemeImages.applyLater(context, spec)
        spec.font?.let { setFont(context, it) }
        spec.glance?.let { applyGlance(context, p, it) }
        // Last: a new outline restarts the home screen.
        spec.outline?.let { p.strokeColorStyle.update(p, optionOf(it)) }
        net.mdantas.terminal.wear.bridge.TerminalWear.publishTheme(context)
        return true
    }

    /**
     * The At a Glance panel: the switches and the named animation are written now, a Lottie (which a link
     * has to download) afterwards, and it wins over a named animation. Hiding the panel goes last, since it restarts the home screen.
     */
    private fun applyGlance(context: Context, p: PreferenceManager2, g: GlanceSpec) {
        if (g.lottie == null) g.animation?.let { p.glanceAnimation.update(p, GlanceAnimationStyle.fromId(it)) }
        g.tint?.let { p.glanceAnimationTint.update(p, it) }
        g.shortcuts?.let { p.glanceShortcuts.update(p, it) }
        g.chats?.let { p.glanceChats.update(p, it) }
        g.agenda?.let { p.glanceAgenda.update(p, it) }
        g.weather?.let { p.glanceWeather.update(p, it) }
        g.alarm?.let { p.glanceAlarm.update(p, it) }
        g.reminders?.let { p.glanceReminders.update(p, it) }
        g.bluetooth?.let { p.glanceBluetooth.update(p, it) }
        g.autoRotate?.let { p.glanceAutoRotate.update(p, it) }
        g.look?.let { applyLook(p, it) }
        g.lottie?.let { ThemeGlance.applyLater(context, it) }
        g.show?.let { p.enableSmartspace.update(p, it) }
    }

    /** Every element of the theme back to how the app comes; the lock screen is left alone. */
    fun reset(context: Context) {
        val p = PreferenceManager2.getInstance(context)
        listOf(p.launcherBackgroundColor, p.accentColor, p.secondaryColor, p.hotseatBackgroundColor, p.folderColor).forEach { it.update(p, it.defaultValue) }
        listOf(p.followSystemColorMode, p.terminalPixelClock, p.terminalIconEffect, p.widgetEffect, p.crtEffect, p.showSystemWallpaper, p.backgroundColorAsWallpaper)
            .forEach { it.update(p, it.defaultValue) }
        listOf(p.widgetEffectIntensity, p.crtEffectIntensity, p.wallpaperDim).forEach { it.update(p, it.defaultValue) }
        p.screenAnimation.update(p, p.screenAnimation.defaultValue)
        p.screenAnimationCustom.update(p, p.screenAnimationCustom.defaultValue)
        // The panel's look; what it shows (agenda, weather…) is a choice of the user, not of a theme.
        listOf(p.glanceAnimationTint, p.glanceShortcuts, p.glanceChats, p.enableSmartspace).forEach { it.update(p, it.defaultValue) }
        p.glanceAnimation.update(p, p.glanceAnimation.defaultValue)
        listOf(
            p.glanceShowHeader, p.glanceShowDate, p.glanceShowStatus, p.glanceClockSeconds, p.glanceDateCaps,
            p.glancePanelBorder, p.glanceGlow, p.glanceStatusSound, p.glanceStatusStorage, p.glanceStatusRam,
        ).forEach { it.update(p, it.defaultValue) }
        listOf(
            p.glanceClockScale,
            p.glanceTextScale,
            p.glanceBorderWidth,
            p.glanceCornerRadius,
            p.glanceDimLevel,
        ).forEach { it.update(p, it.defaultValue) }
        p.glanceClockFormat.update(p, p.glanceClockFormat.defaultValue)
        p.glanceDateFormat.update(p, p.glanceDateFormat.defaultValue)
        p.glanceAlign.update(p, p.glanceAlign.defaultValue)
        p.glanceDensity.update(p, p.glanceDensity.defaultValue)
        p.glanceTitleBar.update(p, p.glanceTitleBar.defaultValue)
        p.glanceUrgentColor.update(p, p.glanceUrgentColor.defaultValue)
        p.glanceShortcutsHidden.update(p, p.glanceShortcutsHidden.defaultValue)
        val fonts = PreferenceManager.getInstance(context).fontWorkspace
        if (fonts.get() != fonts.defaultValue) fonts.set(fonts.defaultValue)
        p.strokeColorStyle.update(p, p.strokeColorStyle.defaultValue)
        setShape(context, p.iconShape, p.iconShape.defaultValue.toString())
        setShape(context, p.folderShape, p.folderShape.defaultValue.toString())
        net.mdantas.terminal.wear.bridge.TerminalWear.publishTheme(context)
    }

    private fun <C, S> PreferenceImpl<C, S>.update(p: PreferenceManager2, value: C) {
        if (firstCached(p) != value) setBlocking(value)
    }

    /** Sets a shape by its key, only when it changes: a new one reloads every icon. */
    private fun setShape(context: Context, pref: PreferenceImpl<IconShape, String>, key: String) {
        val shape = IconShape.fromString(key, context) ?: return
        if (pref.firstCached(PreferenceManager2.getInstance(context)).toString() != shape.toString()) pref.setBlocking(shape)
    }

    private fun setFont(context: Context, font: ThemeFont) {
        val pref = PreferenceManager.getInstance(context).fontWorkspace
        val chosen = when (font) {
            ThemeFont.Default -> pref.defaultValue
            is ThemeFont.Google -> FontCache.GoogleFont(context, font.family)
            is ThemeFont.System -> FontCache.SystemFont(font.family)
        }
        if (pref.get() != chosen) pref.set(chosen)
    }

    // ---- the file ----

    /** The theme in [uri], or null when it cannot be read, is too big or is not a theme. */
    fun read(context: Context, uri: Uri): ThemeSpec? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (out.size() <= ThemeSpecs.MAX_BYTES) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
            if (out.size() > ThemeSpecs.MAX_BYTES) return null
            ThemeSpecs.decode(out.toByteArray().decodeToString()).takeUnless { it.isEmpty }
        }
    }.onFailure { android.util.Log.w("ThemeStore", "could not read $uri", it) }.getOrNull()

    /** Writes [spec] to the cache and returns a uri other apps may read. */
    fun file(context: Context, spec: ThemeSpec, fileName: String = ThemeSpecs.fileName(spec)): Uri {
        val dir = File(context.cacheDir, "themes").apply { mkdirs() }
        val file = File(dir, fileName).apply { writeText(ThemeSpecs.encode(spec)) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** A share sheet for [spec], or null when the file cannot be written. */
    fun shareIntent(context: Context, spec: ThemeSpec): Intent? = runCatching {
        val named = ThemeImages.inline(context, spec).copy(name = spec.name.ifBlank { context.getString(R.string.theme_default_name) })
        val uri = file(context, named)
        val send = Intent(Intent.ACTION_SEND)
            .setType(ThemeSpecs.MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.theme_share_subject, named.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(ThemeSpecs.fileName(named), uri)
        Intent.createChooser(send, context.getString(R.string.theme_share)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.onFailure { android.util.Log.w("ThemeStore", "could not share", it) }.getOrNull()

    /** The theme "tema compartilhar [nome]" sends: a saved one, a ready-made one or, with no name, the one on screen. */
    fun named(context: Context, name: String): ThemeSpec? {
        if (name.isBlank()) return current(context)
        val extras = CommandExtras(host = HostData(themes = load(context), themePresets = presets(context)))
        return ThemeCommand.named(name, extras)?.first
    }

    /** Opens the import screen on [spec], so a theme the AI wrote is shown before anything changes. */
    fun review(context: Context, spec: ThemeSpec): Boolean = runCatching {
        val uri = file(context, spec, "ia.${ThemeSpecs.EXTENSION}")
        context.startActivity(
            Intent(context, ImportThemeActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }.isSuccess

    private const val OPAQUE = 0xFF000000.toInt()
}
