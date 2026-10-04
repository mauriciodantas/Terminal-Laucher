package app.lawnchair.smartspace.glance

import android.app.Notification
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Process
import android.service.notification.StatusBarNotification
import com.android.launcher3.notification.NotificationListener

/**
 * Reads the chat shortcuts WhatsApp publishes and opens them. Needs no WhatsApp API: as the
 * default launcher, Lawnchair may list and start the shortcuts of other apps.
 */
object WhatsAppChatSource {

    private fun launcherApps(context: Context) = context.getSystemService(LauncherApps::class.java)

    /** The shortcuts of every WhatsApp package, or nothing when Lawnchair is not the default launcher. */
    fun load(context: Context, includeBusiness: Boolean): List<ChatCandidate> {
        val apps = launcherApps(context) ?: return emptyList()
        if (!apps.hasShortcutHostPermission()) return emptyList()
        var flags = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) flags = flags or LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED
        return ChatShortcuts.packages(includeBusiness).flatMap { pkg ->
            val query = LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(flags)
            runCatching { apps.getShortcuts(query, Process.myUserHandle()) }.getOrNull().orEmpty()
                // Only conversations: they are the long-lived shortcuts; "new chat" and the like are not.
                .filter { it.isEnabled && it.isChat() }
                .map { it.toCandidate() }
        }
    }

    private fun ShortcutInfo.isChat() = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || isLongLived

    private fun ShortcutInfo.toCandidate() = ChatCandidate(
        packageName = `package`,
        id = id,
        label = (longLabel ?: shortLabel)?.toString().orEmpty(),
        pinned = isPinned,
        rank = rank,
    )

    /** Opens the chat directly, as a tap on the shortcut icon would. */
    fun open(context: Context, chat: ChatCandidate) {
        val apps = launcherApps(context) ?: return
        runCatching {
            apps.startShortcut(chat.packageName, chat.id, null, null, Process.myUserHandle())
        }
    }

    /**
     * Unread messages per chat, keyed by [ChatCandidate.key]. WhatsApp tags each message
     * notification with the id of the chat shortcut, so the counts match the shortcuts.
     */
    fun unread(chats: List<ChatCandidate>): Map<String, Int> {
        val listener = NotificationListener.getInstanceIfConnected() ?: return emptyMap()
        val packages = chats.map { it.packageName }.toSet()
        val active = runCatching { listener.activeNotifications }.getOrNull().orEmpty()
        return active
            .filter { it.packageName in packages && it.notification.shortcutId != null }
            .groupBy { ChatShortcuts.key(it.packageName, it.notification.shortcutId!!) }
            .mapValues { (_, notifications) -> notifications.sumOf { it.messageCount() } }
    }

    private fun StatusBarNotification.messageCount(): Int {
        val messages = notification.extras?.getParcelableArray(Notification.EXTRA_MESSAGES)?.size ?: 0
        return maxOf(messages, notification.number, 1)
    }
}
