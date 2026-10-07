package app.lawnchair.command

/** One entry of a pack as the import dialog shows it, checked against what the user already has. */
data class PackEntry<T>(
    val item: T,
    /** What the entry does when typed: the URI of an action or the command of an alias. */
    val detail: String,
    /** Why it cannot be added as it is (letter taken, reserved word...), or null. */
    val conflict: String?,
    /** A free name that would resolve [conflict], or null when there is none. */
    val suggestion: String?,
    /** Something worth a look before importing, or null. */
    val caution: String?,
)

data class PackPreview(
    val actions: List<PackEntry<CustomAction>>,
    val aliases: List<PackEntry<CommandAlias>>,
)

/** Pure rules of importing a [CommandPack], free of Android types so they can be unit tested. */
object PackRules {

    /** Schemes that can reach files, run script or start an arbitrary component: never imported. */
    private val BLOCKED_SCHEMES = setOf("intent", "file", "content", "javascript", "data", "android-app")

    /** Schemes the catalog and the usual recipes use; any other is flagged as an app-specific link. */
    private val COMMON_SCHEMES = setOf("http", "https", "tel", "sms", "smsto", "mailto", "geo", "tg", "spotify")

    fun schemeOf(template: String): String? {
        val colon = template.indexOf(':')
        if (colon <= 0) return null
        val scheme = template.substring(0, colon).lowercase()
        return scheme.takeIf { s -> s.all { it.isLetterOrDigit() || it in "+-." } }
    }

    fun isBlocked(action: CustomAction): Boolean = action.kind == ActionKind.INTENT && schemeOf(action.template.trim()) in BLOCKED_SCHEMES

    /** A short warning for an action that is allowed but unusual, or null. */
    fun caution(action: CustomAction): String? {
        if (action.kind == ActionKind.SHORTCUT) return null
        val scheme = schemeOf(action.template.trim()) ?: return null
        return if (scheme in COMMON_SCHEMES) null else "link de app específico ($scheme:)"
    }

    private fun detail(action: CustomAction): String = when {
        action.kind == ActionKind.SHORTCUT -> "atalho do launcher · " + action.packages.firstOrNull().orEmpty()
        action.template.isEmpty() -> action.intentAction.substringAfterLast('.') + " " + action.mimeType.orEmpty()
        else -> action.template
    } + if (action.packages.isNotEmpty() && action.kind == ActionKind.INTENT) " · " + action.packages.joinToString() else ""

    /** The first of "name2".."name9" that [free] accepts, or null. */
    private fun renamed(name: String, free: (String) -> Boolean): String? = (2..9).map { name + it }.firstOrNull(free)

    /**
     * What importing [pack] would do, entry by entry and in order, with the same rules as [merge].
     * An entry that fits is taken into account by the ones after it, as it will be when added.
     */
    fun preview(actions: List<CustomAction>, aliases: List<CommandAlias>, pack: CommandPack): PackPreview {
        var mergedActions = actions
        val actionEntries = pack.actions.map { raw ->
            val action = raw.copy(letter = raw.letter.trim().lowercase())
            val conflict = CustomActions.validateLetter(action.letter, mergedActions)
            val suggestion = if (conflict != null && action.letter.isNotEmpty() && action.letter.all { it.isLetterOrDigit() }) {
                renamed(action.letter) { CustomActions.validateLetter(it, mergedActions) == null }
            } else {
                null
            }
            if (conflict == null) mergedActions = mergedActions + action
            PackEntry(action, detail(action), conflict, suggestion, caution(action))
        }
        var mergedAliases = aliases
        val aliasEntries = pack.aliases.map { raw ->
            val alias = CommandAlias(raw.name.trim().lowercase(), raw.expansion.trim())
            val conflict = Aliases.validate(alias.name, alias.expansion, mergedAliases, mergedActions)
            val suggestion = if (conflict != null && alias.name.isNotEmpty() && alias.name.all { it.isLetterOrDigit() }) {
                renamed(alias.name) { Aliases.validate(it, alias.expansion, mergedAliases, mergedActions) == null }
            } else {
                null
            }
            if (conflict == null) mergedAliases = mergedAliases + alias
            PackEntry(alias, alias.expansion, conflict, suggestion, null)
        }
        return PackPreview(actionEntries, aliasEntries)
    }

    /**
     * Adds [pack] to what the user has. Nothing existing is replaced. An entry that does not fit is
     * skipped, or, with [rename], added under its free suggested name when there is one.
     */
    fun merge(
        actions: List<CustomAction>,
        aliases: List<CommandAlias>,
        pack: CommandPack,
        rename: Boolean = false,
    ): PackMerge {
        var mergedActions = actions
        var skipped = 0
        for (raw in pack.actions) {
            val action = raw.copy(letter = raw.letter.trim().lowercase())
            val conflict = CustomActions.validateLetter(action.letter, mergedActions)
            if (conflict == null) {
                mergedActions = mergedActions + action
                continue
            }
            val free = if (rename && action.letter.isNotEmpty() && action.letter.all { it.isLetterOrDigit() }) {
                renamed(action.letter) { CustomActions.validateLetter(it, mergedActions) == null }
            } else {
                null
            }
            if (free != null) mergedActions = mergedActions + action.copy(letter = free) else skipped++
        }
        var mergedAliases = aliases
        for (raw in pack.aliases) {
            val alias = CommandAlias(raw.name.trim().lowercase(), raw.expansion.trim())
            if (Aliases.validate(alias.name, alias.expansion, mergedAliases, mergedActions) == null) {
                mergedAliases = mergedAliases + alias
                continue
            }
            val free = if (rename && alias.name.isNotEmpty() && alias.name.all { it.isLetterOrDigit() }) {
                renamed(alias.name) { Aliases.validate(it, alias.expansion, mergedAliases, mergedActions) == null }
            } else {
                null
            }
            if (free != null) mergedAliases = mergedAliases + alias.copy(name = free) else skipped++
        }
        return PackMerge(
            mergedActions,
            mergedAliases,
            mergedActions.size - actions.size,
            mergedAliases.size - aliases.size,
            skipped,
        )
    }
}
