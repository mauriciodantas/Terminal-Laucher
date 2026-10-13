package app.lawnchair.command

import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents

/**
 * Commands that come with the app but only show up once their pack is installed: "pacote brasil"
 * adds what only makes sense in one country (Correios tracking, CEP, holidays). Each action keeps
 * the pack id in [CustomAction.pack], so removing the pack takes exactly them away.
 */
data class BundledPack(
    val id: String,
    val name: String,
    val description: String,
    val actions: List<CustomAction>,
)

object BundledPacks {

    const val BRAZIL = "brasil"

    val ALL: List<BundledPack> get() = listOf(brazil())

    fun of(word: String): BundledPack? = word.trim().foldAccents().let { w -> ALL.firstOrNull { it.id == w } }

    fun matching(prefix: String): List<BundledPack> = prefix.trim().foldAccents().let { p -> ALL.filter { it.id.startsWith(p) } }

    fun isInstalled(pack: BundledPack, actions: List<CustomAction>): Boolean = actions.any { it.pack == pack.id }

    /**
     * [actions] with [pack] added. A letter the user already has stays theirs: the pack's action is
     * left out and counted in the second value.
     */
    fun install(actions: List<CustomAction>, aliases: List<CommandAlias>, pack: BundledPack): Pair<List<CustomAction>, Int> {
        val merge = PackRules.merge(remove(actions, pack.id), aliases, CommandPack(pack.actions, emptyList()))
        return merge.actions to merge.skipped
    }

    fun remove(actions: List<CustomAction>, id: String): List<CustomAction> = actions.filter { it.pack != id }

    /** The letters of [pack] another command already uses, which an install leaves out. */
    fun conflicts(actions: List<CustomAction>, aliases: List<CommandAlias>, pack: BundledPack): List<String> = PackRules.preview(remove(actions, pack.id), aliases, CommandPack(pack.actions, emptyList())).actions
        .filter { it.conflict != null }
        .map { it.item.letter }

    private fun brazil() = BundledPack(
        id = BRAZIL,
        name = "Brasil",
        description = Texts.get(R.string.cmd_pack_brazil),
        actions = listOf(
            CustomAction(
                letter = "rastrear",
                label = Texts.get(R.string.cmd_pack_track),
                kind = ActionKind.INTENT,
                template = "https://www.linkcorreios.com.br/?id={text}",
                packages = emptyList(),
                arg = ArgKind.TEXT,
                // "QB123456789BR" typed alone is a Correios tracking code.
                pattern = "[A-Za-z]{2}\\d{9}[A-Za-z]{2}",
                pack = BRAZIL,
            ),
            CustomAction(
                letter = "cep",
                label = "CEP",
                kind = ActionKind.API,
                template = "https://viacep.com.br/ws/{text}/json/",
                packages = emptyList(),
                arg = ArgKind.TEXT,
                // "01310-100" typed alone; without the dash it could be a phone number.
                pattern = "\\d{5}-\\d{3}",
                api = ApiSpec("{logradouro} · {complemento}\n{bairro} · {localidade}/{uf}\nDDD {ddd}"),
                pack = BRAZIL,
            ),
            CustomAction(
                letter = "feriados",
                label = Texts.get(R.string.cmd_pack_holidays),
                kind = ActionKind.API,
                template = "https://brasilapi.com.br/api/feriados/v1/{year}",
                packages = emptyList(),
                arg = ArgKind.NONE,
                api = ApiSpec("{date|date} · {name}", max = 6, upcoming = "date"),
                pack = BRAZIL,
            ),
        ),
    )
}

/** "pacote": lists the packs, "pacote brasil" installs one and "pacote brasil remover" takes it away. */
object PackCommand : BuiltIn(BuiltInCommand.PACK, R.string.cmd_usage_pack, R.string.cmd_help_pack) {

    private fun removeWords(): Set<String> = (Texts.get(R.string.cmd_word_remove).split(',') + listOf("remover", "remove"))
        .map { it.trim().foldAccents() }.filter { it.isNotEmpty() }.toSet()

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val words = arg.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val title = Texts.get(R.string.cmd_pack_title)
        val pack = words.firstOrNull()?.let { BundledPacks.of(it) }
            ?: return list(words.firstOrNull().orEmpty(), ctx, title)
        val installed = BundledPacks.isInstalled(pack, ctx.custom)
        val letters = pack.actions.joinToString(" · ") { it.letter }
        val removing = words.getOrNull(1)?.foldAccents()?.let { it in removeWords() } == true
        val packTitle = title + " · " + pack.name.uppercase()
        return when {
            removing && installed -> commandResult(packTitle, Texts.get(R.string.cmd_pack_remove, letters), Tone.WARN, CommandAction.RemovePack(pack.id))

            removing -> commandResult(packTitle, Texts.get(R.string.cmd_pack_not_installed), Tone.IDLE, null)

            installed -> commandResult(
                packTitle,
                Texts.get(R.string.cmd_pack_installed, letters),
                Tone.IDLE,
                null,
                suggestions = listOf(Suggestion("$word ${pack.id} " + removeWords().first(), Texts.get(R.string.cmd_pack_remove_label), commandKindLabel())),
            )

            else -> {
                val taken = BundledPacks.conflicts(ctx.custom, ctx.aliases, pack)
                val preview = pack.description + "\n" + letters +
                    (if (taken.isEmpty()) "" else "\n" + Texts.get(R.string.cmd_pack_taken, taken.joinToString(", ")))
                commandResult(packTitle, preview, Tone.OK, CommandAction.InstallPack(pack.id))
            }
        }
    }

    private fun list(typed: String, ctx: CommandContext, title: String): Analysis {
        val packs = BundledPacks.matching(typed)
        val suggestions = packs.map {
            val state = if (BundledPacks.isInstalled(it, ctx.custom)) Texts.get(R.string.cmd_pack_state_installed) else Texts.get(R.string.cmd_kind_pack)
            Suggestion("$word ${it.id}", it.name + " · " + it.description, state)
        }
        return commandResult(
            title,
            Texts.get(if (packs.isEmpty()) R.string.cmd_pack_unknown else R.string.cmd_pack_pick),
            if (packs.isEmpty()) Tone.ERROR else Tone.IDLE,
            null,
            suggestions = suggestions,
            listTitle = Texts.get(R.string.cmd_list_packs),
        )
    }

    private fun commandKindLabel(): String = Texts.get(R.string.cmd_kind_command)
}
