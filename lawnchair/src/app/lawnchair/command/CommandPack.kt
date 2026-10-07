package app.lawnchair.command

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** The user's actions and aliases as one file that can be sent to somebody else. */
data class CommandPack(val actions: List<CustomAction>, val aliases: List<CommandAlias>) {
    val isEmpty: Boolean get() = actions.isEmpty() && aliases.isEmpty()
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

    /**
     * Reads a pack. Also accepts the older export, a bare array of actions. Throws when [raw] is
     * neither, so a stray JSON file is never taken for a pack.
     */
    fun decode(raw: String): CommandPack {
        val text = raw.trim().removePrefix("﻿")
        if (text.startsWith("[")) {
            return CommandPack(CustomActionStore.fromJson(JSONArray(text)).take(MAX_ITEMS), emptyList())
        }
        val o = JSONObject(text)
        require(o.optString("format") == FORMAT) { "not a command pack" }
        require(o.optInt("version", VERSION) <= VERSION) { "newer pack version" }
        return CommandPack(
            CustomActionStore.fromJson(o.optJSONArray("actions") ?: JSONArray()).take(MAX_ITEMS),
            AliasStore.fromJson(o.optJSONArray("aliases") ?: JSONArray()).take(MAX_ITEMS),
        )
    }

    /** The pack in [uri], or null when it cannot be read, is too big or is not a pack. */
    fun read(context: Context, uri: Uri): CommandPack? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readNBytes(MAX_BYTES + 1)
            if (bytes.size > MAX_BYTES) return null
            decode(bytes.decodeToString()).takeUnless { it.isEmpty }
        }
    }.getOrNull()

    /**
     * Adds [pack] to what the user has. Nothing existing is replaced: an action whose letter is
     * invalid or taken, and an alias that fails [Aliases.validate], is skipped.
     */
    fun merge(actions: List<CustomAction>, aliases: List<CommandAlias>, pack: CommandPack): PackMerge {
        val (mergedActions, skippedActions) = CustomActions.merge(actions, pack.actions)
        var mergedAliases = aliases
        var skippedAliases = 0
        for (alias in pack.aliases) {
            val clean = CommandAlias(alias.name.trim().lowercase(), alias.expansion.trim())
            if (Aliases.validate(clean.name, clean.expansion, mergedAliases, mergedActions) == null) {
                mergedAliases = mergedAliases + clean
            } else {
                skippedAliases++
            }
        }
        return PackMerge(
            mergedActions,
            mergedAliases,
            mergedActions.size - actions.size,
            mergedAliases.size - aliases.size,
            skippedActions + skippedAliases,
        )
    }

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
