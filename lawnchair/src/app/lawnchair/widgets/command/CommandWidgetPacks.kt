package app.lawnchair.widgets.command

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.lawnchair.command.AliasStore
import app.lawnchair.command.CommandPack
import app.lawnchair.command.CommandPacks
import app.lawnchair.command.CommandWidgetSchedule
import app.lawnchair.command.CustomActionStore
import app.lawnchair.widgets.lottie.LottieWidgetPacks
import app.lawnchair.widgets.lottie.LottieWidgetPacks.readLimited
import com.android.launcher3.R
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject

/**
 * A command widget as one file that can be sent to somebody else: the command line, its schedule
 * and the user's own letters and aliases that the command needs, so it works the same on the other
 * phone. It is the same kind of file as the Lottie widget pack (a zip with `widget.json`), with the
 * kind "command" and no animation.
 */
data class CommandWidgetPack(
    val command: String,
    val schedule: CommandWidgetSchedule,
    /** The letters and aliases [command] uses; empty when it uses only built-in commands. */
    val commands: CommandPack,
)

object CommandWidgetPacks {

    const val KIND = "command"

    /** A command line is short; this keeps a hostile file from carrying a script's worth of text into the widget. */
    private const val MAX_COMMAND_LENGTH = 2000

    fun write(pack: CommandWidgetPack, out: OutputStream) {
        val manifest = JSONObject()
            .put("format", LottieWidgetPacks.FORMAT)
            .put("version", LottieWidgetPacks.VERSION)
            .put("kind", KIND)
            .put("name", pack.command)
            .put("command", pack.command)
            .put("schedule", pack.schedule.encode())
            .put("commands", JSONObject(CommandPacks.encode(pack.commands.actions, pack.commands.aliases)))
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(LottieWidgetPacks.MANIFEST))
            zip.write(manifest.toString(2).toByteArray())
            zip.closeEntry()
        }
    }

    /** Reads a pack. Throws when [input] is not one. */
    fun read(input: InputStream): CommandWidgetPack {
        var manifest: JSONObject? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == LottieWidgetPacks.MANIFEST) {
                    manifest = JSONObject(zip.readLimited(LottieWidgetPacks.MAX_MANIFEST_BYTES).decodeToString())
                }
            }
        }
        val o = requireNotNull(manifest) { "no widget.json" }
        require(o.optString("format") == LottieWidgetPacks.FORMAT) { "not a widget pack" }
        require(o.optInt("version", LottieWidgetPacks.VERSION) <= LottieWidgetPacks.VERSION) { "newer pack version" }
        require(o.optString("kind") == KIND) { "not a command widget" }
        val command = o.optString("command").trim()
        require(command.isNotEmpty() && command.length <= MAX_COMMAND_LENGTH) { "bad command" }
        val commands = o.optJSONObject("commands")?.let { runCatching { CommandPacks.decode(it.toString()) }.getOrNull() }
        return CommandWidgetPack(
            command = command,
            // The schedule is read the same way the store reads it, so the minimum interval holds for imports too.
            schedule = CommandWidgetSchedule.decode(o.optString("schedule")),
            commands = commands ?: CommandPack(emptyList(), emptyList()),
        )
    }

    /** The pack in [uri], or null when it cannot be read or is not a command widget pack. */
    fun read(context: Context, uri: Uri): CommandWidgetPack? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { read(it) }
    }.onFailure { android.util.Log.w("CommandWidgetPacks", "could not read $uri", it) }.getOrNull()

    /** Builds the pack for what the configuration screen shows and returns a share sheet for it. */
    fun shareIntent(context: Context, command: String, schedule: CommandWidgetSchedule): Intent? = runCatching {
        val line = command.trim()
        require(line.isNotEmpty()) { "no command" }
        val pack = CommandWidgetPack(
            command = line,
            schedule = schedule,
            commands = LottieWidgetPacks.dependencies(line, CustomActionStore.load(context), AliasStore.load(context)),
        )
        val dir = File(context.cacheDir, "widgets").apply { mkdirs() }
        val fileName = line.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").trim('_').take(40).ifBlank { "widget" } + "." + LottieWidgetPacks.EXTENSION
        val file = File(dir, fileName)
        file.outputStream().use { write(pack, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(LottieWidgetPacks.MIME)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.command_widget_share_subject))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(fileName, uri)
        Intent.createChooser(send, context.getString(R.string.command_widget_share)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.onFailure { android.util.Log.w("CommandWidgetPacks", "could not share", it) }.getOrNull()
}
