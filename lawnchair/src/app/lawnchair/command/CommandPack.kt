package app.lawnchair.command

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** The user's actions and aliases as one file that can be sent to somebody else. */
data class CommandPack(
    val actions: List<CustomAction>,
    val aliases: List<CommandAlias>,
    /** Actions left out on reading because they use a scheme that can reach files or components. */
    val blocked: Int = 0,
) {
    val isEmpty: Boolean get() = actions.isEmpty() && aliases.isEmpty() && blocked == 0
}

/** What an import did: the lists to save and how many entries each side added or skipped. */
data class PackMerge(
    val actions: List<CustomAction>,
    val aliases: List<CommandAlias>,
    val addedActions: Int,
    val addedAliases: Int,
    val skipped: Int,
)

object CommandPacks {

    const val MIME = "application/x-terminal-commands"
    const val EXTENSION = "tcmd"
    const val FILE_NAME = "comandos.$EXTENSION"

    private const val FORMAT = "terminal-commands"
    private const val VERSION = 1
    private const val MAX_BYTES = 256 * 1024
    private const val MAX_ITEMS = 200

    fun encode(actions: List<CustomAction>, aliases: List<CommandAlias>): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("actions", CustomActionStore.toJson(actions))
        .put("aliases", AliasStore.toJson(aliases))
        .toString(2)

    /** Reads a pack. Throws when [raw] is not one, so a stray JSON file is never taken for a pack. */
    fun decode(raw: String): CommandPack {
        val text = raw.trim().removePrefix("﻿")
        val o = JSONObject(text)
        require(o.optString("format") == FORMAT) { "not a command pack" }
        require(o.optInt("version", VERSION) <= VERSION) { "newer pack version" }
        val (blocked, allowed) = CustomActionStore.fromJson(o.optJSONArray("actions") ?: JSONArray())
            .take(MAX_ITEMS)
            .partition { PackRules.isBlocked(it) }
        return CommandPack(
            allowed,
            AliasStore.fromJson(o.optJSONArray("aliases") ?: JSONArray()).take(MAX_ITEMS),
            blocked.size,
        )
    }

    /** The pack in [uri], or null when it cannot be read, is too big or is not a pack. */
    fun read(context: Context, uri: Uri): CommandPack? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            // readNBytes needs API 33; minSdk is 26.
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (out.size() <= MAX_BYTES) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
            if (out.size() > MAX_BYTES) return null
            decode(out.toByteArray().decodeToString()).takeUnless { it.isEmpty }
        }
    }.onFailure { android.util.Log.w("CommandPacks", "could not read $uri", it) }.getOrNull()

    /** See [PackRules.merge]. */
    fun merge(actions: List<CustomAction>, aliases: List<CommandAlias>, pack: CommandPack, rename: Boolean = false): PackMerge =
        PackRules.merge(actions, aliases, pack, rename)

    /** Writes the pack to the cache and returns a share sheet for it, or null when that fails. */
    fun shareIntent(context: Context, actions: List<CustomAction>, aliases: List<CommandAlias>): Intent? =
        runCatching {
            val dir = File(context.cacheDir, "commands").apply { mkdirs() }
            val file = File(dir, FILE_NAME).apply { writeText(encode(actions, aliases)) }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val send = Intent(Intent.ACTION_SEND)
                .setType(MIME)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "Comandos da barra de comando")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri(FILE_NAME, uri)
            Intent.createChooser(send, "Compartilhar comandos").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.getOrNull()
}
