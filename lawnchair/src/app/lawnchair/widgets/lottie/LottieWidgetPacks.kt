package app.lawnchair.widgets.lottie

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.lawnchair.command.AliasStore
import app.lawnchair.command.CommandPack
import app.lawnchair.command.CustomActionStore
import app.lawnchair.command.ProviderStore
import app.lawnchair.command.WidgetPacks
import app.lawnchair.command.WidgetPacks.readLimited
import app.lawnchair.widgets.lottie.LottieWidgetStore.Playback
import com.android.launcher3.R
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject

/**
 * A Lottie widget as one file that can be sent to somebody else: the animation, the command line
 * run on a tap, how it plays, whether it takes the theme colors, and the user's own letters,
 * aliases and API providers that the command needs, so it works the same on the other phone.
 *
 * The file is the widget file of the engine ([WidgetPacks]): a zip with `widget.json`, here of the
 * kind "lottie", and the animation as it was imported (`animation`).
 */
data class LottieWidgetPack(
    val name: String,
    val animation: ByteArray,
    val command: String,
    val playback: Playback,
    val themeColors: Boolean,
    /** The letters, aliases and providers [command] uses; empty when it uses only built-in commands. */
    val commands: CommandPack,
    val showResult: Boolean = false,
    val resultDurationSeconds: Int = LottieWidgetStore.DEFAULT_RESULT_DURATION_SECONDS,
) {
    override fun equals(other: Any?) = other is LottieWidgetPack && name == other.name && command == other.command &&
        playback == other.playback && themeColors == other.themeColors && showResult == other.showResult &&
        resultDurationSeconds == other.resultDurationSeconds &&
        commands == other.commands && animation.contentEquals(other.animation)

    override fun hashCode() = animation.contentHashCode() * 31 + command.hashCode()
}

object LottieWidgetPacks {

    const val MIME = WidgetPacks.MIME
    const val EXTENSION = WidgetPacks.EXTENSION

    private const val FORMAT = WidgetPacks.FORMAT
    private const val VERSION = WidgetPacks.VERSION
    private const val KIND = WidgetPacks.KIND_LOTTIE
    private const val MANIFEST = WidgetPacks.MANIFEST
    private const val ANIMATION = "animation"

    /** Lottie files are rarely more than a few MB; this keeps a hostile zip from filling memory. */
    private const val MAX_ANIMATION_BYTES = 16 * 1024 * 1024

    fun write(pack: LottieWidgetPack, out: java.io.OutputStream) {
        val manifest = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("kind", KIND)
            .put("name", pack.name)
            .put("command", pack.command)
            .put("playback", pack.playback.name)
            .put("themeColors", pack.themeColors)
            .put("showResult", pack.showResult)
            .put("resultDurationSeconds", pack.resultDurationSeconds)
            .put("commands", WidgetPacks.commandsJson(pack.commands))
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.toString(2).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(ANIMATION))
            zip.write(pack.animation)
            zip.closeEntry()
        }
    }

    /** Reads a pack. Throws when [input] is not one. Whether the animation plays is checked on import. */
    fun read(input: InputStream): LottieWidgetPack {
        var manifest: JSONObject? = null
        var animation: ByteArray? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when (entry.name) {
                    MANIFEST -> manifest = JSONObject(zip.readLimited(WidgetPacks.MAX_MANIFEST_BYTES).decodeToString())
                    ANIMATION -> animation = zip.readLimited(MAX_ANIMATION_BYTES)
                }
            }
        }
        val o = requireNotNull(manifest) { "no widget.json" }
        require(o.optString("format") == FORMAT) { "not a widget pack" }
        require(o.optInt("version", VERSION) <= VERSION) { "newer pack version" }
        require(o.optString("kind", KIND) == KIND) { "unknown widget" }
        return LottieWidgetPack(
            name = o.optString("name").ifBlank { "animation.json" },
            animation = requireNotNull(animation) { "no animation" },
            command = o.optString("command").trim(),
            playback = Playback.entries.firstOrNull { it.name == o.optString("playback") } ?: Playback.LOOP,
            themeColors = o.optBoolean("themeColors", false),
            showResult = o.optBoolean("showResult", false),
            resultDurationSeconds = o.optInt("resultDurationSeconds", LottieWidgetStore.DEFAULT_RESULT_DURATION_SECONDS),
            commands = WidgetPacks.commandsOf(o),
        )
    }

    /** The pack in [uri], or null when it cannot be read or is not a widget pack. */
    fun read(context: Context, uri: Uri): LottieWidgetPack? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { read(it) }
    }.onFailure { android.util.Log.w("LottieWidgetPacks", "could not read $uri", it) }.getOrNull()

    /** The kind of widget the pack in [uri] holds ("lottie", "command"), or null when the file is not a widget pack. */
    fun kindOf(context: Context, uri: Uri): String? = WidgetPacks.kindOf(context, uri)

    /** True when [uri] starts like a zip, so a file that is not a command pack may be a widget. */
    fun looksLikePack(context: Context, uri: Uri): Boolean = WidgetPacks.looksLikeZip(context, uri)

    /** Builds the pack for what the configuration screen shows and returns a share sheet for it. */
    fun shareIntent(
        context: Context,
        animation: File?,
        name: String,
        command: String,
        playback: Playback,
        themeColors: Boolean,
        showResult: Boolean = false,
        resultDurationSeconds: Int = LottieWidgetStore.DEFAULT_RESULT_DURATION_SECONDS,
    ): Intent? = runCatching {
        val line = command.trim()
        val pack = LottieWidgetPack(
            name = name,
            animation = animation?.readBytes() ?: DefaultWidgetAnimation.bytes,
            command = line,
            playback = playback,
            themeColors = themeColors,
            showResult = showResult,
            resultDurationSeconds = resultDurationSeconds,
            commands = WidgetPacks.dependencies(line, CustomActionStore.load(context), AliasStore.load(context), ProviderStore.load(context)),
        )
        val dir = File(context.cacheDir, "widgets").apply { mkdirs() }
        val fileName = name.substringBeforeLast('.').ifBlank { "widget" }.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_") + ".$EXTENSION"
        val file = File(dir, fileName)
        file.outputStream().use { write(pack, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.lottie_widget_share_subject))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(fileName, uri)
        Intent.createChooser(send, context.getString(R.string.lottie_widget_share)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.onFailure { android.util.Log.w("LottieWidgetPacks", "could not share", it) }.getOrNull()
}
