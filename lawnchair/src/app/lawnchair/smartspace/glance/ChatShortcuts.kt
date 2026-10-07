package app.lawnchair.smartspace.glance

/** A chat shortcut published by WhatsApp, as the launcher sees it. */
data class ChatCandidate(
    val packageName: String,
    val id: String,
    val label: String,
    /** True when the user added it with "Adicionar atalho" inside WhatsApp. */
    val pinned: Boolean,
    /** The order WhatsApp gives its shortcuts: lower is more recent. */
    val rank: Int = 0,
) {
    val key: String get() = ChatShortcuts.key(packageName, id)
}

/** Pure rules of the "Conversas rápidas" row, free of Android types so they can be unit tested. */
object ChatShortcuts {

    const val MAX = 5
    const val WHATSAPP = "com.whatsapp"
    const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    private const val SEPARATOR = '\n'

    fun packages(includeBusiness: Boolean): List<String> = if (includeBusiness) listOf(WHATSAPP, WHATSAPP_BUSINESS) else listOf(WHATSAPP)

    fun key(packageName: String, id: String) = "$packageName/$id"

    fun parseKeys(stored: String): List<String> = stored.split(SEPARATOR).filter { it.isNotBlank() }.distinct()

    fun serializeKeys(keys: List<String>): String = keys.distinct().joinToString(SEPARATOR.toString())

    /**
     * The chats to show. The ones the user picked come first, in the order they were picked, and
     * only if WhatsApp still publishes them. With no pick, the pinned shortcuts come first and
     * the most recent chats fill the rest, so the row is never empty just because nothing is pinned.
     */
    fun select(available: List<ChatCandidate>, chosenKeys: List<String>, max: Int = MAX): List<ChatCandidate> {
        val byKey = available.associateBy { it.key }
        val chosen = chosenKeys.mapNotNull { byKey[it] }
        val shown = if (chosenKeys.isEmpty()) {
            available.sortedWith(compareByDescending<ChatCandidate> { it.pinned }.thenBy { it.rank })
        } else {
            chosen
        }
        return shown.take(max)
    }

    /**
     * Flips one chat in the picked list. Adding is refused once [max] are picked, so the row
     * never needs to drop a chat the user chose.
     */
    fun toggle(chosenKeys: List<String>, key: String, max: Int = MAX): List<String> = when {
        key in chosenKeys -> chosenKeys - key
        chosenKeys.size >= max -> chosenKeys
        else -> chosenKeys + key
    }

    /** The big letter inside the square: the first letter or digit of the name. */
    fun initial(label: String): String = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"

    /** Text of the unread counter, or null when there is nothing to show. */
    fun badge(unread: Int): String? = when {
        unread <= 0 -> null
        unread > 99 -> "99+"
        else -> unread.toString()
    }
}
