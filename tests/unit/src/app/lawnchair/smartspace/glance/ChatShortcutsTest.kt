package app.lawnchair.smartspace.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatShortcutsTest {

    private fun chat(id: String, label: String = id, pinned: Boolean = false, pkg: String = ChatShortcuts.WHATSAPP, rank: Int = 0) =
        ChatCandidate(pkg, id, label, pinned, rank)

    @Test
    fun packages_addBusinessOnlyWhenAsked() {
        assertEquals(listOf("com.whatsapp"), ChatShortcuts.packages(false))
        assertEquals(listOf("com.whatsapp", "com.whatsapp.w4b"), ChatShortcuts.packages(true))
    }

    @Test
    fun keys_roundTripAndDropBlanksAndDuplicates() {
        val keys = listOf("com.whatsapp/a", "com.whatsapp/b")
        assertEquals(keys, ChatShortcuts.parseKeys(ChatShortcuts.serializeKeys(keys)))
        assertEquals(emptyList<String>(), ChatShortcuts.parseKeys(""))
        assertEquals(listOf("x"), ChatShortcuts.parseKeys("x\n\nx"))
    }

    @Test
    fun select_withoutAPickShowsThePinnedFirstThenTheMostRecent() {
        val available = listOf(chat("a", rank = 3), chat("b", pinned = true, rank = 9), chat("c", rank = 1))
        assertEquals(listOf("b", "c", "a"), ChatShortcuts.select(available, emptyList()).map { it.id })
    }

    @Test
    fun select_withoutAPickAndNothingPinnedStillFillsTheRow() {
        val available = (1..8).map { chat("c$it", rank = it) }
        assertEquals(listOf("c1", "c2", "c3", "c4", "c5"), ChatShortcuts.select(available, emptyList()).map { it.id })
    }

    @Test
    fun select_followsThePickOrderAndSkipsWhatWhatsAppNoLongerPublishes() {
        val available = listOf(chat("a"), chat("b"), chat("c"))
        val picked = listOf(chat("c").key, chat("gone").key, chat("a").key)
        assertEquals(listOf("c", "a"), ChatShortcuts.select(available, picked).map { it.id })
    }

    @Test
    fun select_neverShowsMoreThanTheMaximum() {
        val available = (1..8).map { chat("c$it", pinned = true) }
        assertEquals(ChatShortcuts.MAX, ChatShortcuts.select(available, emptyList()).size)
    }

    @Test
    fun select_keepsTheSameIdOfTwoPackagesApart() {
        val personal = chat("a")
        val business = chat("a", pkg = ChatShortcuts.WHATSAPP_BUSINESS)
        assertEquals(listOf(business), ChatShortcuts.select(listOf(personal, business), listOf(business.key)))
    }

    @Test
    fun toggle_addsRemovesAndRefusesPastTheLimit() {
        assertEquals(listOf("a"), ChatShortcuts.toggle(emptyList(), "a"))
        assertEquals(emptyList<String>(), ChatShortcuts.toggle(listOf("a"), "a"))
        val full = (1..ChatShortcuts.MAX).map { "k$it" }
        assertEquals(full, ChatShortcuts.toggle(full, "new"))
        assertEquals(full - "k2", ChatShortcuts.toggle(full, "k2"))
    }

    @Test
    fun initial_isTheFirstLetterOrDigitInUpperCase() {
        assertEquals("A", ChatShortcuts.initial("ana"))
        assertEquals("M", ChatShortcuts.initial("  mãe"))
        assertEquals("2", ChatShortcuts.initial("2 Amigos"))
        assertEquals("É", ChatShortcuts.initial("équipe"))
        assertEquals("?", ChatShortcuts.initial("..."))
        assertEquals("?", ChatShortcuts.initial(""))
    }

    @Test
    fun badge_hidesZeroAndCapsAt99() {
        assertNull(ChatShortcuts.badge(0))
        assertNull(ChatShortcuts.badge(-3))
        assertEquals("7", ChatShortcuts.badge(7))
        assertEquals("99", ChatShortcuts.badge(99))
        assertEquals("99+", ChatShortcuts.badge(100))
    }
}
