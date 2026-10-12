package app.lawnchair.data.folder

import com.android.launcher3.util.ComponentKey

/**
 * Resolves the component keys saved in a drawer folder against the apps installed now.
 *
 * An app update can rename the launcher activity, which changes its component key and would
 * otherwise drop the app from the folder. A saved key with no exact match falls back to an app of
 * the same package and user. Keys that match nothing (app uninstalled or still updating) are
 * skipped, and an app is returned only once.
 */
fun <T> resolveFolderItems(
    savedKeys: List<String>,
    available: Collection<T>,
    keyOf: (T) -> ComponentKey,
): List<T> {
    if (savedKeys.isEmpty() || available.isEmpty()) return emptyList()
    val byKey = available.associateBy(keyOf)
    val byPackage = available.groupBy { keyOf(it).let { key -> key.componentName.packageName to key.user } }
    val result = LinkedHashSet<T>()
    savedKeys.forEach { saved ->
        val key = ComponentKey.fromString(saved) ?: return@forEach
        val match = byKey[key]
            ?: byPackage[key.componentName.packageName to key.user]
                ?.firstOrNull { it !in result && keyOf(it).toString() !in savedKeys }
        if (match != null) result.add(match)
    }
    return result.toList()
}
