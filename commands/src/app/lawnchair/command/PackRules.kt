package app.lawnchair.command

import app.lawnchair.util.Texts

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
    /** The pack's providers as they would come in, renamed when the user has another of the same id. */
    val providers: List<PackEntry<ApiProvider>> = emptyList(),
    /** What each action, in order, sends out and where; null for one that opens an app instead. */
    val sends: List<PackSends?> = emptyList(),
    /** Pack provider id to the id it comes in as, for the ones renamed. */
    val renames: Map<String, String> = emptyMap(),
    /** Pack provider ids the user already has with the same address and authentication. */
    val reused: Set<String> = emptySet(),
)

/** What an API action sends to [host] each time it runs: what is typed, and the [variables] it fills in. */
data class PackSends(val host: String, val typed: Boolean, val variables: Set<Variables.Var>, val method: String = HttpMethods.GET) {
    /** It tells somebody else where the user is or what they copied: never ticked for them. */
    val isSensitive: Boolean get() = variables.isNotEmpty()

    /** It changes data on the other side (POST, PUT, PATCH, DELETE): never ticked for them either. */
    val writes: Boolean get() = method != HttpMethods.GET

    /** What makes an import leave it unticked. */
    val needsTrust: Boolean get() = isSensitive || writes

    /** "o que você digitar · sua localização". */
    fun label(): String = listOfNotNull(
        Texts.get(R.string.cmd_sends_text).takeIf { typed },
        Texts.get(R.string.cmd_sends_location).takeIf { Variables.Var.LOCATION in variables || Variables.Var.MAPS in variables },
        Texts.get(R.string.cmd_sends_clip).takeIf { Variables.Var.CLIP in variables },
        Texts.get(R.string.cmd_sends_selection).takeIf { Variables.Var.SELECTION in variables },
    ).joinToString(" · ")
}

/**
 * How the providers of a pack land among the user's. A provider is the same one when the id, the
 * host and the authentication all match, and then the user's is used. With the same id but another
 * address or authentication it comes in under a free id, and the pack's actions follow it: a
 * stranger's commands are never bound to the user's credentials without them choosing so.
 */
data class ProviderPlan(
    val added: List<ApiProvider>,
    val renames: Map<String, String>,
    val reused: Set<String>,
) {
    fun apply(action: CustomAction): CustomAction = renames[action.provider]?.let { action.copy(provider = it) } ?: action
}

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

    /** An API action only reaches an https address, and needs to say what it shows. */
    fun isBlocked(action: CustomAction): Boolean = when (action.kind) {
        ActionKind.INTENT -> schemeOf(action.template.trim()) in BLOCKED_SCHEMES
        ActionKind.API -> !ApiCall.isUsable(action)
        ActionKind.SHORTCUT -> false
    }

    /** A short warning for an action that is allowed but unusual, or null. */
    fun caution(action: CustomAction): String? {
        if (action.kind == ActionKind.SHORTCUT) return null
        // What is typed goes to somebody else's server: the import says whose.
        if (action.kind == ActionKind.API) {
            return if (action.provider.isNotEmpty()) {
                Texts.get(R.string.cmd_caution_provider, action.provider)
            } else {
                Texts.get(R.string.cmd_caution_api, ApiCall.host(action))
            }
        }
        val scheme = schemeOf(action.template.trim()) ?: return null
        return if (scheme in COMMON_SCHEMES) null else Texts.get(R.string.cmd_caution_app_link, scheme)
    }

    private fun detail(action: CustomAction): String = when {
        action.kind == ActionKind.SHORTCUT -> Texts.get(R.string.cmd_desc_shortcut, action.packages.firstOrNull().orEmpty())

        action.kind == ActionKind.API -> (action.api?.method ?: HttpMethods.GET) + " " +
            (if (action.provider.isEmpty()) "" else action.provider + " · ") + action.template

        action.template.isEmpty() -> action.intentAction.substringAfterLast('.') + " " + action.mimeType.orEmpty()

        else -> action.template
    } + (if (action.packages.isNotEmpty() && action.kind == ActionKind.INTENT) " · " + action.packages.joinToString() else "") +
        // An action that also takes text typed alone says so: it catches lines without its letter.
        (if (action.pattern.isNotEmpty()) "\n" + Texts.get(R.string.cmd_pack_pattern, action.pattern) else "")

    /** The first of "name2".."name9" that [free] accepts, or null. */
    private fun renamed(name: String, free: (String) -> Boolean): String? = (2..9).map { name + it }.firstOrNull(free)

    /**
     * What importing [pack] would do, entry by entry and in order, with the same rules as [merge].
     * An entry that fits is taken into account by the ones after it, as it will be when added.
     */
    fun preview(actions: List<CustomAction>, aliases: List<CommandAlias>, pack: CommandPack, providers: List<ApiProvider> = emptyList()): PackPreview {
        val plan = planProviders(pack.providers, providers)
        val known = Providers.all(providers + plan.added)
        var mergedActions = actions
        val sends = mutableListOf<PackSends?>()
        val actionEntries = pack.actions.map { raw ->
            val action = plan.apply(raw).copy(letter = raw.letter.trim().lowercase(), pattern = CommandPattern.usable(raw.pattern))
            val out = sends(action, known)
            sends += out
            val conflict = CustomActions.validateLetter(action.letter, mergedActions)
            val suggestion = if (conflict != null && action.letter.isNotEmpty() && action.letter.all { it.isLetterOrDigit() }) {
                renamed(action.letter) { CustomActions.validateLetter(it, mergedActions) == null }
            } else {
                null
            }
            if (conflict == null) mergedActions = mergedActions + action
            val caution = when {
                out?.writes == true -> Texts.get(R.string.cmd_caution_writes, out.method, out.host) +
                    if (out.isSensitive) "\n" + Texts.get(R.string.cmd_caution_sends, out.label(), out.host) else ""

                out?.isSensitive == true -> Texts.get(R.string.cmd_caution_sends, out.label(), out.host)

                else -> caution(action)
            }
            PackEntry(action, detail(action), conflict, suggestion, caution)
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
        val providerEntries = pack.providers.mapNotNull { p ->
            val renamed = plan.renames[p.id]
            val item = plan.added.firstOrNull { it.id == (renamed ?: p.id) } ?: p.takeIf { it.id in plan.reused } ?: return@mapNotNull null
            PackEntry(
                item,
                p.baseUrl + " · " + Providers.authLabel(p.auth),
                conflict = null,
                suggestion = null,
                caution = when {
                    p.id in plan.reused -> Texts.get(R.string.cmd_provider_reused, p.id)
                    renamed != null -> Texts.get(R.string.cmd_provider_renamed, p.id, renamed)
                    else -> null
                },
            )
        }
        return PackPreview(actionEntries, aliasEntries, providerEntries, sends, plan.renames, plan.reused)
    }

    /** See [ProviderPlan]. Invalid providers and ones taking a built-in id are left out. */
    fun planProviders(incoming: List<ApiProvider>, existing: List<ApiProvider>): ProviderPlan {
        val added = mutableListOf<ApiProvider>()
        val renames = mutableMapOf<String, String>()
        val reused = mutableSetOf<String>()
        for (p in incoming) {
            if (Providers.validate(p) != null || Providers.isBuiltIn(p.id)) continue
            val mine = existing.firstOrNull { it.id == p.id }
            when {
                mine == null -> added += p

                mine.host == p.host && mine.auth == p.auth -> reused += p.id

                else -> {
                    val free = (2..99).map { p.id.take(36) + "-" + it }.firstOrNull { Providers.validateId(it, existing + added) == null } ?: continue
                    renames[p.id] = free
                    added += p.copy(id = free)
                }
            }
        }
        return ProviderPlan(added, renames, reused)
    }

    /** What [action] sends out each run and where; null for one that does not call an API. */
    fun sends(action: CustomAction, providers: List<ApiProvider>): PackSends? {
        if (action.kind != ActionKind.API) return null
        val api = action.api
        val variables = Variables.used(action.template + " " + api?.body.orEmpty()).intersect(Variables.SENSITIVE)
        return PackSends(ApiCall.host(action, providers), action.arg != ArgKind.NONE, variables, api?.method ?: HttpMethods.GET)
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
        providers: List<ApiProvider> = emptyList(),
    ): PackMerge {
        val plan = planProviders(pack.providers, providers)
        var mergedActions = actions
        var skipped = 0
        for (raw in pack.actions) {
            val action = plan.apply(raw).copy(letter = raw.letter.trim().lowercase(), pattern = CommandPattern.usable(raw.pattern))
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
        val added = plan.added
        return PackMerge(
            mergedActions,
            mergedAliases,
            mergedActions.size - actions.size,
            mergedAliases.size - aliases.size,
            skipped,
            providers + added,
            added.map { it.id },
        )
    }
}
