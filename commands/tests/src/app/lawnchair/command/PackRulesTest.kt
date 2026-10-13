package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PackRulesTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private fun action(letter: String, template: String = "https://x.test/{text}") =
        CustomAction(letter, "Ação $letter", ActionKind.INTENT, template, emptyList(), ArgKind.TEXT)

    private val mine = listOf(CustomActions.WHATSAPP)
    private val myAliases = listOf(CommandAlias("mae", "ligar maria"))

    @Test fun anEntryThatFitsHasNoConflict() {
        val preview = PackRules.preview(mine, myAliases, CommandPack(listOf(action("yt")), listOf(CommandAlias("pai", "ligar pedro"))))
        assertNull(preview.actions.single().conflict)
        assertNull(preview.aliases.single().conflict)
    }

    @Test fun aTakenLetterIsFlaggedWithAFreeSuggestion() {
        val entry = PackRules.preview(mine, myAliases, CommandPack(listOf(action("w")), emptyList())).actions.single()
        assertEquals("\"w\" já está em uso", entry.conflict)
        assertEquals("w2", entry.suggestion)
    }

    @Test fun aReservedWordHasASuggestionToo() {
        val entry = PackRules.preview(mine, myAliases, CommandPack(listOf(action("calc")), emptyList())).actions.single()
        assertNotNull(entry.conflict)
        assertEquals("calc2", entry.suggestion)
    }

    @Test fun aTakenAliasIsFlaggedAndRenamed() {
        val entry = PackRules.preview(mine, myAliases, CommandPack(emptyList(), listOf(CommandAlias("mae", "ligar joana")))).aliases.single()
        assertEquals("\"mae\" já está em uso", entry.conflict)
        assertEquals("mae2", entry.suggestion)
    }

    @Test fun entriesOfThePackClashWithEachOther() {
        val preview = PackRules.preview(mine, myAliases, CommandPack(listOf(action("yt"), action("yt")), emptyList()))
        assertNull(preview.actions[0].conflict)
        assertNotNull(preview.actions[1].conflict)
        assertEquals("yt2", preview.actions[1].suggestion)
    }

    @Test fun mergeWithoutRenameSkipsClashes() {
        val merge = PackRules.merge(mine, myAliases, CommandPack(listOf(action("w"), action("yt")), listOf(CommandAlias("mae", "ligar joana"))))
        assertEquals(listOf("w", "yt"), merge.actions.map { it.letter })
        assertEquals(1, merge.addedActions)
        assertEquals(0, merge.addedAliases)
        assertEquals(2, merge.skipped)
    }

    @Test fun mergeWithRenameKeepsClashesUnderTheirSuggestedName() {
        val merge = PackRules.merge(
            mine,
            myAliases,
            CommandPack(listOf(action("w")), listOf(CommandAlias("mae", "ligar joana"))),
            rename = true,
        )
        assertEquals(listOf("w", "w2"), merge.actions.map { it.letter })
        assertEquals(listOf("mae", "mae2"), merge.aliases.map { it.name })
        assertEquals(0, merge.skipped)
        assertEquals(CustomActions.WHATSAPP, merge.actions.first())
    }

    @Test fun anInvalidNameIsNeverRenamed() {
        val merge = PackRules.merge(mine, myAliases, CommandPack(listOf(action("a b")), emptyList()), rename = true)
        assertEquals(1, merge.skipped)
        assertEquals(mine, merge.actions)
        assertNull(PackRules.preview(mine, myAliases, CommandPack(listOf(action("a b")), emptyList())).actions.single().suggestion)
    }

    @Test fun schemesThatReachFilesOrComponentsAreBlocked() {
        assertTrue(PackRules.isBlocked(action("a", "intent://x#Intent;end")))
        assertTrue(PackRules.isBlocked(action("a", "FILE:///sdcard/x")))
        assertTrue(PackRules.isBlocked(action("a", "content://contacts/people")))
        assertTrue(PackRules.isBlocked(action("a", "javascript:alert(1)")))
        assertFalse(PackRules.isBlocked(action("a", "https://x.test")))
        assertFalse(PackRules.isBlocked(action("a", "tg://resolve?phone={number}")))
        // A text-only action has no URI at all.
        assertFalse(PackRules.isBlocked(action("a", "")))
    }

    @Test fun appSpecificLinksGetACaution() {
        assertNull(PackRules.caution(action("a", "https://x.test")))
        assertNull(PackRules.caution(action("a", "tg://resolve")))
        assertEquals("link de app específico (todoist:)", PackRules.caution(action("a", "todoist://addtask?content={text}")))
        assertNull(PackRules.caution(action("a", "")))
    }

    @Test fun theDetailShowsWhereTheActionGoes() {
        val entry = PackRules.preview(mine, myAliases, CommandPack(listOf(CustomActions.CATALOG[1]), emptyList())).actions.single()
        assertEquals("tg://resolve?phone={number} · org.telegram.messenger, org.telegram.messenger.web", entry.detail)
    }
}
