package app.lawnchair.command

import org.json.JSONArray

/** A folder the app drawer already has: [packages] are the apps in it, [keys] how they are stored. */
data class ExistingFolder(val id: Int, val title: String, val packages: List<String>, val keys: List<String> = emptyList())

/**
 * One folder of a plan: a new one, or the folder [existingId]. Without [replace] the apps are added
 * to it; with [replace] (only with --force) they become its whole content.
 */
data class PlannedFolder(val name: String, val apps: List<AppEntry>, val existingId: Int? = null, val replace: Boolean = false)

/**
 * What "ia pastas" proposes; nothing changes until the user applies it. With --force, apps may move
 * out of the folders the AI left out: [trimmed] are those folders with the apps that stay, and
 * [removed] the ones left empty, which are deleted.
 */
data class FolderPlan(
    val folders: List<PlannedFolder>,
    val note: String = "",
    val trimmed: List<ExistingFolder> = emptyList(),
    val removed: List<ExistingFolder> = emptyList(),
) {
    val appCount: Int get() = folders.sumOf { it.apps.size }
    val isEmpty: Boolean get() = folders.isEmpty() && trimmed.isEmpty() && removed.isEmpty()
}

/**
 * Reads the folders the AI proposed and keeps only what the drawer can take: apps that are
 * installed, each in one folder only, and new folders of at least two apps. Without force an app
 * that already sits in a folder stays there; with it, apps may move between folders. Pure, so it can
 * be unit tested.
 */
object AiFolders {

    const val MAX_NAME = 24

    /** The words after "ia pastas" that let the AI move apps that are already in folders. */
    private val FORCE_FLAGS = setOf("--force", "-f")

    /** The request without the force flag, and whether the flag was there. */
    fun splitForce(prompt: String): Pair<String, Boolean> {
        val words = prompt.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val force = words.any { it.lowercase() in FORCE_FLAGS }
        return words.filterNot { it.lowercase() in FORCE_FLAGS }.joinToString(" ") to force
    }

    /** The plan in [reply], or null when the reply holds no folders at all. */
    fun plan(reply: String, apps: List<AppEntry>, existing: List<ExistingFolder>, force: Boolean = false): FolderPlan? {
        val o = AiPrompts.json(reply) ?: return null
        val list = o.optJSONArray("folders") ?: return null
        val byName = apps.groupBy { Aliases.key(it.label) }.mapValues { it.value.first() }
        // The drawer keeps an app in one folder only, by package, as the folder editor does.
        val owner = existing.flatMap { f -> f.packages.map { it to f.id } }.toMap()
        val used = mutableSetOf<String>()
        // Folders by folded name, so the same name twice in the reply makes one folder.
        val planned = linkedMapOf<String, Pair<String, MutableList<AppEntry>>>()
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            val name = item.optString("name").trim().take(MAX_NAME).trim()
            if (name.isEmpty()) continue
            val key = Aliases.key(name)
            val target = existing.firstOrNull { Aliases.key(it.title) == key }
            val (_, chosen) = planned.getOrPut(key) { (target?.title ?: name) to mutableListOf() }
            names(item.optJSONArray("apps")).forEach { label ->
                val app = byName[Aliases.key(label)] ?: return@forEach
                val pkg = app.id.substringBefore('/')
                if (pkg in used) return@forEach
                // Without force an app already in a folder (this one included) stays where the user put it.
                if (!force && owner[pkg] != null) return@forEach
                used += pkg
                chosen += app
            }
        }
        val folders = planned.mapNotNull { (key, value) ->
            val (name, chosen) = value
            val target = existing.firstOrNull { Aliases.key(it.title) == key }
            when {
                target != null && chosen.isNotEmpty() -> PlannedFolder(target.title, chosen, target.id, replace = force)
                target == null && chosen.size >= 2 -> PlannedFolder(name, chosen)
                else -> null
            }
        }
        val note = o.optString("note").trim()
        if (!force) return FolderPlan(folders, note)

        // The folders the plan does not name lose the apps that moved elsewhere; an empty one goes.
        val named = folders.mapNotNull { it.existingId }.toSet()
        val moved = folders.flatMap { f -> f.apps.map { it.id.substringBefore('/') } }.toSet()
        val trimmed = mutableListOf<ExistingFolder>()
        val removed = mutableListOf<ExistingFolder>()
        existing.filter { it.id !in named && it.packages.any { pkg -> pkg in moved } }.forEach { folder ->
            val keep = folder.packages.indices.filter { folder.packages[it] !in moved }
            val left = folder.copy(
                packages = keep.map { folder.packages[it] },
                keys = keep.mapNotNull { folder.keys.getOrNull(it) },
            )
            if (left.packages.isEmpty()) removed += folder else trimmed += left
        }
        return FolderPlan(folders, note, trimmed, removed)
    }

    private fun names(array: JSONArray?): List<String> = if (array == null) emptyList() else (0 until array.length()).map { array.optString(it).trim() }.filter { it.isNotEmpty() }
}
