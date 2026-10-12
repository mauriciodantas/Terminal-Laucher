package app.lawnchair.command

import android.content.ComponentName
import android.content.Context
import android.os.Process
import app.lawnchair.data.AppDatabase
import app.lawnchair.data.folder.service.FolderService
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.util.ComponentKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Reads the drawer folders for "ia pastas" and writes the plan the user applied. */
object FolderOrganizer {

    /** The folders the drawer has now. */
    suspend fun existing(context: Context): List<ExistingFolder> = withContext(Dispatchers.IO) {
        AppDatabase.INSTANCE.get(context).folderDao().getAllFoldersWithItems().first().map { f ->
            val keys = f.items.sortedBy { it.rank }.mapNotNull { it.componentKey }
            ExistingFolder(f.folder.id, f.folder.title, keys.map { it.substringBefore('/') }, keys)
        }
    }

    /** The folders by name with the names of their apps, as the AI sees them. */
    fun describe(existing: List<ExistingFolder>, apps: List<AppEntry>): List<Pair<String, List<String>>> {
        val labels = apps.associate { it.id.substringBefore('/') to it.label }
        return existing.map { f -> f.title to f.packages.mapNotNull { labels[it] } }
    }

    /**
     * Creates the new folders and adds apps to the existing ones. Only a plan made with --force moves
     * apps out of a folder: the folders it names get exactly its apps, the others lose the apps that
     * moved, and a folder left empty is deleted. Returns how many folders changed.
     */
    suspend fun apply(context: Context, plan: FolderPlan): Int {
        val service = FolderService.INSTANCE.get(context)
        val current = existing(context).associateBy { it.id }
        var changed = 0
        plan.folders.forEach { folder ->
            val keys = folder.apps.mapNotNull { keyOf(it) }
            if (keys.isEmpty()) return@forEach
            val target = folder.existingId?.let { current[it] }
            if (target != null) {
                val content = if (folder.replace) keys else (target.keys + keys).distinct()
                service.updateFolderWithItems(target.id, target.title, content)
            } else {
                val id = service.saveFolderInfo(folder.name).toInt()
                service.updateFolderWithItems(id, folder.name, keys)
            }
            changed++
        }
        plan.trimmed.forEach { folder ->
            service.updateFolderWithItems(folder.id, folder.title, folder.keys)
            changed++
        }
        plan.removed.forEach { folder ->
            service.deleteFolderInfo(folder.id)
            changed++
        }
        // The drawer reads its folders once; this rebuilds it with the new ones.
        withContext(Dispatchers.Main) { InvariantDeviceProfile.INSTANCE.get(context).onPreferencesChanged(context) }
        return changed
    }

    private fun keyOf(app: AppEntry): String? = ComponentName.unflattenFromString(app.id)?.let { ComponentKey(it, Process.myUserHandle()).toString() }
}
