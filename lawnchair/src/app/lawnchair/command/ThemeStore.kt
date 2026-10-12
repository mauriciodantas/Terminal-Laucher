package app.lawnchair.command

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.lawnchair.font.FontCache
import app.lawnchair.font.googlefonts.GoogleFontsListing
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
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
        save(context, ThemeSpecs.upsert(load(context), theme))
    }

    fun remove(context: Context, name: String) {
        save(context, ThemeSpecs.remove(load(context), name))
    }

    /** Keeps the theme on screen now as [name]. */
    fun saveCurrent(context: Context, name: String) = keep(context, current(context).copy(name = name))

    /** The ready-made looks of Appearance, with their names in the app language. */
    fun presets(context: Context): List<ThemeSpec> = (ComplementaryColors.themes + ComplementaryColors.pastels + ComplementaryColors.pairs).map {
        ThemeSpec(
            name = context.getString(it.name),
            accent = ThemeColor.Custom(it.accent),
            background = ThemeColor.Custom(it.background),
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
            showWallpaper = p.showSystemWallpaper.firstCached(p),
            wallpaperDim = p.wallpaperDim.firstCached(p),
            colorAsWallpaper = p.backgroundColorAsWallpaper.firstCached(p),
        )
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
     * picked in Appearance. A pair that could not be read is refused as a whole.
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
        spec.dock?.let { p.hotseatBackgroundColor.update(p, optionOf(it)) }
        spec.folder?.let { p.folderColor.update(p, optionOf(it)) }
        spec.pixelClock?.let { p.terminalPixelClock.update(p, it) }
        spec.iconEffect?.let { p.terminalIconEffect.update(p, it) }
        spec.widgetEffect?.let { p.widgetEffect.update(p, it) }
        spec.widgetIntensity?.let { p.widgetEffectIntensity.update(p, it) }
        spec.crtEffect?.let { p.crtEffect.update(p, it) }
        spec.crtIntensity?.let { p.crtEffectIntensity.update(p, it) }
        spec.showWallpaper?.let { p.showSystemWallpaper.update(p, it) }
        spec.wallpaperDim?.let { p.wallpaperDim.update(p, it) }
        spec.colorAsWallpaper?.let { p.backgroundColorAsWallpaper.update(p, it) }
        spec.font?.let { setFont(context, it) }
        // Last: a new outline restarts the home screen.
        spec.outline?.let { p.strokeColorStyle.update(p, optionOf(it)) }
        return true
    }

    /** Every element of the theme back to how the app comes; the lock screen is left alone. */
    fun reset(context: Context) {
        val p = PreferenceManager2.getInstance(context)
        listOf(p.launcherBackgroundColor, p.accentColor, p.hotseatBackgroundColor, p.folderColor).forEach { it.update(p, it.defaultValue) }
        listOf(p.followSystemColorMode, p.terminalPixelClock, p.terminalIconEffect, p.widgetEffect, p.crtEffect, p.showSystemWallpaper, p.backgroundColorAsWallpaper)
            .forEach { it.update(p, it.defaultValue) }
        listOf(p.widgetEffectIntensity, p.crtEffectIntensity, p.wallpaperDim).forEach { it.update(p, it.defaultValue) }
        val fonts = PreferenceManager.getInstance(context).fontWorkspace
        if (fonts.get() != fonts.defaultValue) fonts.set(fonts.defaultValue)
        p.strokeColorStyle.update(p, p.strokeColorStyle.defaultValue)
    }

    private fun <C, S> PreferenceImpl<C, S>.update(p: PreferenceManager2, value: C) {
        if (firstCached(p) != value) setBlocking(value)
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
        val named = spec.copy(name = spec.name.ifBlank { context.getString(R.string.theme_default_name) })
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
        val extras = CommandExtras(themes = load(context), themePresets = presets(context))
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
