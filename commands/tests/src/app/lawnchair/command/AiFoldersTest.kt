package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** "ia pastas": the prompt and the reading of the folders the AI proposes. */
class AiFoldersTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private fun app(label: String, pkg: String = label.lowercase()) = AppEntry(label, "com.$pkg/.Main")

    private val whatsapp = app("WhatsApp")
    private val instagram = app("Instagram")
    private val x = app("X")
    private val nubank = app("Nubank")
    private val itau = app("Itaú", "itau")
    private val maps = app("Maps")
    private val apps = listOf(whatsapp, instagram, x, nubank, itau, maps)

    private fun plan(reply: String, existing: List<ExistingFolder> = emptyList()) = AiFolders.plan(reply, apps, existing)

    private fun run(text: String) = CommandEngine.analyze(
        text,
        apps,
        emptyList(),
        true,
        CustomActions.DEFAULTS,
        emptyList(),
        CommandExtras(ai = "Claude · claude-opus-5-5"),
    )

    @Test fun commandRunsWithoutARequest() {
        assertEquals(CommandAction.Ai(AiMode.FOLDERS, ""), run("ia pastas").action)
        assertEquals(CommandAction.Ai(AiMode.FOLDERS, "só bancos"), run("ai folders só bancos").action)
    }

    @Test fun readsTheFolders() {
        val p = plan(
            """```json
            {"folders": [{"name": "Social", "apps": ["WhatsApp", "instagram", "X"]},
                         {"name": "Bancos", "apps": ["Nubank", "Itau"]}], "note": "por uso"}
            ```""",
        )!!
        assertEquals(listOf("Social", "Bancos"), p.folders.map { it.name })
        assertEquals(listOf(whatsapp, instagram, x), p.folders[0].apps)
        // Case and accents do not matter when matching names.
        assertEquals(listOf(nubank, itau), p.folders[1].apps)
        assertEquals(5, p.appCount)
        assertEquals("por uso", p.note)
    }

    @Test fun dropsUnknownAndRepeatedAppsAndTinyFolders() {
        val p = plan(
            """{"folders": [{"name": "Social", "apps": ["WhatsApp", "Telegram", "WhatsApp", "X"]},
                            {"name": "Mais", "apps": ["X", "Maps"]},
                            {"name": "  ", "apps": ["Nubank", "Itaú"]}]}""",
        )!!
        // Telegram is not installed, WhatsApp is listed twice, X is already in Social: "Mais" keeps one app and is dropped.
        assertEquals(listOf("Social"), p.folders.map { it.name })
        assertEquals(listOf(whatsapp, x), p.folders.single().apps)
    }

    @Test fun addsToAnExistingFolderAndLeavesFiledAppsAlone() {
        val existing = listOf(ExistingFolder(7, "Finanças", listOf("com.nubank")))
        val p = plan(
            """{"folders": [{"name": "financas", "apps": ["Itaú", "Nubank"]}, {"name": "Social", "apps": ["Nubank", "WhatsApp"]}]}""",
            existing,
        )!!
        val finance = p.folders.first()
        assertEquals("Finanças", finance.name)
        assertEquals(7, finance.existingId)
        // One new app is enough for a folder that already exists.
        assertEquals(listOf(itau), finance.apps)
        // Nubank stays in its folder, so Social keeps only one app and is not created.
        assertEquals(1, p.folders.size)
    }

    @Test fun sameNameTwiceMakesOneFolder() {
        val p = plan("""{"folders": [{"name": "Social", "apps": ["WhatsApp"]}, {"name": "social", "apps": ["X"]}]}""")!!
        assertEquals(listOf(whatsapp, x), p.folders.single().apps)
    }

    @Test fun noPlan() {
        assertNull(plan("não consegui"))
        assertNull(plan("""{"note": "nada"}"""))
        assertTrue(plan("""{"folders": []}""")!!.isEmpty)
    }

    @Test fun promptListsOnlyAppsOutsideFolders() {
        val system = AiPrompts.system(
            AiMode.FOLDERS,
            AiContext(apps = listOf("WhatsApp", "Nubank", "Maps"), folders = listOf("Finanças" to listOf("Nubank"))),
        )
        val outside = system.substringAfter("APPS WITHOUT A FOLDER: ").substringBefore("\n")
        assertEquals("WhatsApp, Maps", outside)
        assertTrue(system.contains("- Finanças: Nubank"))
    }

    // ---- --force ----

    @Test fun forceFlag() {
        assertEquals(CommandAction.Ai(AiMode.FOLDERS, "", force = true), run("ia pastas --force").action)
        assertEquals(CommandAction.Ai(AiMode.FOLDERS, "só bancos", force = true), run("ia pastas só -f bancos").action)
        assertEquals(Tone.WARN, run("ia pastas --force").tone)
        assertEquals("" to false, AiFolders.splitForce("  "))
    }

    private val social = ExistingFolder(1, "Social", listOf("com.whatsapp", "com.instagram", "com.x"), listOf("k-wa", "k-ig", "k-x"))
    private val misc = ExistingFolder(2, "Diversos", listOf("com.nubank", "com.maps"), listOf("k-nu", "k-maps"))
    private val banks = ExistingFolder(3, "Bancos", listOf("com.itau"), listOf("k-itau"))

    @Test fun forceMovesAppsAndRewritesNamedFolders() {
        val p = AiFolders.plan(
            """{"folders": [{"name": "Social", "apps": ["WhatsApp", "Instagram"]},
                            {"name": "Dinheiro", "apps": ["Nubank", "Itaú"]}]}""",
            apps,
            listOf(social, misc, banks),
            force = true,
        )!!
        // Social is named: it gets exactly these apps (X leaves it).
        val rewritten = p.folders.first()
        assertEquals(1, rewritten.existingId)
        assertTrue(rewritten.replace)
        assertEquals(listOf(whatsapp, instagram), rewritten.apps)
        // Apps already in folders can now go to a new one.
        assertEquals(listOf(nubank, itau), p.folders[1].apps)
        // Diversos loses Nubank and keeps Maps; Bancos is left empty and goes.
        assertEquals(listOf(ExistingFolder(2, "Diversos", listOf("com.maps"), listOf("k-maps"))), p.trimmed)
        assertEquals(listOf(banks), p.removed)
    }

    @Test fun withoutForceNothingMoves() {
        val p = AiFolders.plan(
            """{"folders": [{"name": "Dinheiro", "apps": ["Nubank", "Itaú"]}]}""",
            apps,
            listOf(misc, banks),
        )!!
        assertTrue(p.isEmpty)
    }

    @Test fun forcePromptListsEveryApp() {
        val system = AiPrompts.system(
            AiMode.FOLDERS,
            AiContext(apps = listOf("WhatsApp", "Nubank"), folders = listOf("Finanças" to listOf("Nubank")), forceFolders = true),
        )
        assertEquals("WhatsApp, Nubank", system.substringAfter("ALL APPS: ").substringBefore("\n"))
        assertTrue(system.contains("you may reorganize them"))
    }
}
