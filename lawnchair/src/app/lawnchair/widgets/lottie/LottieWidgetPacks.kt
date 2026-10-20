package app.lawnchair.widgets.lottie

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.lawnchair.command.AliasStore
import app.lawnchair.command.Aliases
import app.lawnchair.command.CommandAlias
import app.lawnchair.command.CommandEngine
import app.lawnchair.command.CommandPack
import app.lawnchair.command.CommandPacks
import app.lawnchair.command.CustomAction
import app.lawnchair.command.CustomActionStore
import app.lawnchair.command.TimeParse
import app.lawnchair.util.foldAccents
import app.lawnchair.widgets.lottie.LottieWidgetStore.Playback
import com.android.launcher3.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject

/**
 * A Lottie widget as one file that can be sent to somebody else: the animation, the command line
 * run on a tap, how it plays, whether it takes the theme colors, and the user's own letters and
 * aliases that the command needs, so it works the same on the other phone.
 *
 * The file is a zip with `widget.json` and the animation as it was imported (`animation`).
 */
data class LottieWidgetPack(
    val name: String,
    val animation: ByteArray,
    val command: String,
    val playback: Playback,
    val themeColors: Boolean,
    /** The letters and aliases [command] uses; empty when it uses only built-in commands. */
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

    const val MIME = "application/x-terminal-widget"
    const val EXTENSION = "twgt"

    internal const val FORMAT = "terminal-widget"
    internal const val VERSION = 1
    private const val KIND = "lottie"
    internal const val MANIFEST = "widget.json"
    private const val ANIMATION = "animation"

    /** Lottie files are rarely more than a few MB; this keeps a hostile zip from filling memory. */
    private const val MAX_ANIMATION_BYTES = 16 * 1024 * 1024
    internal const val MAX_MANIFEST_BYTES = 512 * 1024

    /** How deep aliases that name other aliases are followed when collecting what a command needs. */
    private const val MAX_DEPTH = 4

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
            .put("commands", JSONObject(CommandPacks.encode(pack.commands.actions, pack.commands.aliases)))
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
                    MANIFEST -> manifest = JSONObject(zip.readLimited(MAX_MANIFEST_BYTES).decodeToString())
                    ANIMATION -> animation = zip.readLimited(MAX_ANIMATION_BYTES)
                }
            }
        }
        val o = requireNotNull(manifest) { "no widget.json" }
        require(o.optString("format") == FORMAT) { "not a widget pack" }
        require(o.optInt("version", VERSION) <= VERSION) { "newer pack version" }
        require(o.optString("kind", KIND) == KIND) { "unknown widget" }
        val commands = o.optJSONObject("commands")?.let { runCatching { CommandPacks.decode(it.toString()) }.getOrNull() }
        return LottieWidgetPack(
            name = o.optString("name").ifBlank { "animation.json" },
            animation = requireNotNull(animation) { "no animation" },
            command = o.optString("command").trim(),
            playback = Playback.entries.firstOrNull { it.name == o.optString("playback") } ?: Playback.LOOP,
            themeColors = o.optBoolean("themeColors", false),
            showResult = o.optBoolean("showResult", false),
            resultDurationSeconds = o.optInt("resultDurationSeconds", LottieWidgetStore.DEFAULT_RESULT_DURATION_SECONDS),
            commands = commands ?: CommandPack(emptyList(), emptyList()),
        )
    }

    /** The pack in [uri], or null when it cannot be read or is not a widget pack. */
    fun read(context: Context, uri: Uri): LottieWidgetPack? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { read(it) }
    }.onFailure { android.util.Log.w("LottieWidgetPacks", "could not read $uri", it) }.getOrNull()

    /**
     * The kind of widget the pack in [uri] holds ("lottie", "command"), read from its manifest only,
     * or null when the file is not a widget pack. Packs written before the kind existed are Lottie.
     */
    fun kindOf(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name != MANIFEST) continue
                    val o = JSONObject(zip.readLimited(MAX_MANIFEST_BYTES).decodeToString())
                    if (o.optString("format") != FORMAT) return@use null
                    return@use o.optString("kind", KIND)
                }
                null
            }
        }
    }.getOrNull()

    /** True when [uri] starts like a zip, so a file that is not a command pack may be a widget. */
    fun looksLikePack(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.read() == 'P'.code && it.read() == 'K'.code }
    }.getOrNull() == true

    /**
     * The user's letters and aliases that [command] runs, in each part of a chain and through
     * aliases that stand for other commands, so the pack carries everything the tap needs.
     */
    fun dependencies(command: String, actions: List<CustomAction>, aliases: List<CommandAlias>): CommandPack {
        val usedActions = linkedSetOf<CustomAction>()
        val usedAliases = linkedSetOf<CommandAlias>()
        fun visit(line: String, depth: Int) {
            if (depth > MAX_DEPTH) return
            for (part in CommandEngine.splitChain(line)) {
                // "em 30m: tema escuro" runs what follows the colon.
                val lead = part.trim().takeWhile { !it.isWhitespace() }
                val scheduled = TimeParse.isIn(lead) || TimeParse.isAt(lead) || TimeParse.isTomorrow(lead) || TimeParse.isWhen(lead)
                val text = if (scheduled) part.substringAfter(':', "").trim() else part.trim()
                val first = text.takeWhile { !it.isWhitespace() }
                if (first.isEmpty()) continue
                val alias = Aliases.find(first, aliases)
                if (alias != null) {
                    if (usedAliases.add(alias)) visit(alias.expansion, depth + 1)
                    continue
                }
                actions.firstOrNull { it.letter == first.foldAccents() }?.let { usedActions += it }
            }
        }
        visit(command, 0)
        return CommandPack(usedActions.toList(), usedAliases.toList())
    }

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
            commands = dependencies(line, CustomActionStore.load(context), AliasStore.load(context)),
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

    internal fun InputStream.readLimited(max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            require(out.size() <= max) { "entry too big" }
        }
        return out.toByteArray()
    }
}
