package app.lawnchair.command

import app.lawnchair.util.Texts
import java.net.URLDecoder
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class LineError(
    val start: Int,
    val end: Int,
    val message: String,
)

sealed interface ParsedCommand {
    val name: String
    val label: String

    data class Action(
        override val name: String,
        override val label: String,
        val action: CustomAction,
    ) : ParsedCommand

    data class Alias(
        override val name: String,
        override val label: String,
        val alias: CommandAlias,
    ) : ParsedCommand
}

sealed interface ParseResult {
    data class Success(val command: ParsedCommand) : ParseResult
    data class Failure(val error: LineError) : ParseResult

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
    fun getOrNull(): ParsedCommand? = (this as? Success)?.command
    fun errorOrNull(): LineError? = (this as? Failure)?.error
}

data class ValidationIssue(
    val message: String,
    val isError: Boolean,
)

data class CommandValidation(
    val issues: List<ValidationIssue>,
) {
    val isValid: Boolean get() = issues.none { it.isError }
    val errors: List<String> get() = issues.filter { it.isError }.map { it.message }
    val warnings: List<String> get() = issues.filterNot { it.isError }.map { it.message }
}

/**
 * Pure JVM parser, serializer and validator for command lines used in the bar and editor.
 */
object CommandLine {

    private data class RawToken(
        val text: String,
        val start: Int,
        val end: Int,
    )

    /**
     * Splits a line into tokens handling single/double quotes and backslash escapes,
     * recording original character offsets.
     */
    private fun tokenize(text: String, baseOffset: Int): List<RawToken> {
        val tokens = mutableListOf<RawToken>()
        var i = 0
        val len = text.length

        while (i < len) {
            while (i < len && text[i].isWhitespace()) i++
            if (i >= len) break

            val start = baseOffset + i
            val sb = StringBuilder()
            while (i < len && !text[i].isWhitespace()) {
                val c = text[i]
                when (c) {
                    '\'' -> {
                        i++ // skip opening quote
                        while (i < len && text[i] != '\'') {
                            sb.append(text[i])
                            i++
                        }
                        if (i < len && text[i] == '\'') i++ // skip closing quote
                    }

                    '"' -> {
                        i++ // skip opening quote
                        while (i < len && text[i] != '"') {
                            if (text[i] == '\\' && i + 1 < len) {
                                i++
                                sb.append(text[i])
                            } else {
                                sb.append(text[i])
                            }
                            i++
                        }
                        if (i < len && text[i] == '"') i++ // skip closing quote
                    }

                    '\\' -> {
                        i++
                        if (i < len) {
                            sb.append(text[i])
                            i++
                        }
                    }

                    else -> {
                        sb.append(c)
                        i++
                    }
                }
            }
            val end = baseOffset + i
            tokens.add(RawToken(sb.toString(), start, end))
        }
        return tokens
    }

    /**
     * Finds the index of ` # ` (space, hash, space) outside quotes.
     */
    private fun findCommentIndex(line: String): Int {
        var inSingle = false
        var inDouble = false
        var i = 0
        val len = line.length
        while (i < len) {
            val c = line[i]
            when {
                c == '\\' && !inSingle -> i += 2

                // skip escaped char
                c == '\'' && !inDouble -> {
                    inSingle = !inSingle
                    i++
                }

                c == '"' && !inSingle -> {
                    inDouble = !inDouble
                    i++
                }

                !inSingle && !inDouble && c == ' ' && i + 2 < len && line[i + 1] == '#' && line[i + 2] == ' ' -> {
                    return i
                }

                else -> i++
            }
        }
        return -1
    }

    fun inferArgKind(destination: String): ArgKind {
        val hasText = "{text}" in destination || "{}" in destination || (1..9).any { "{$it}" in destination }
        val hasContact = "{name}" in destination || "{number}" in destination || "{phone}" in destination
        return when {
            hasContact && hasText -> ArgKind.CONTACT_AND_TEXT
            hasContact -> ArgKind.CONTACT
            hasText -> ArgKind.TEXT
            else -> ArgKind.NONE
        }
    }

    /**
     * Parses [line] into a [ParsedCommand] ([ParsedCommand.Action] or [ParsedCommand.Alias]).
     */
    fun parse(
        line: String,
        existingActions: List<CustomAction> = emptyList(),
        existingAliases: List<CommandAlias> = emptyList(),
    ): ParseResult {
        val trimmedStart = line.indexOfFirst { !it.isWhitespace() }
        if (trimmedStart < 0) {
            return ParseResult.Failure(LineError(0, line.length, Texts.get(R.string.cmd_err_dest_unknown)))
        }

        // Split comment if present: " # " outside quotes
        val commentIdx = findCommentIndex(line)
        val commandSection = if (commentIdx >= 0) line.substring(0, commentIdx) else line
        val labelSection = if (commentIdx >= 0) line.substring(commentIdx + 3).trim() else ""

        val rawTokens = tokenize(commandSection, 0)
        if (rawTokens.isEmpty()) {
            return ParseResult.Failure(LineError(0, line.length, Texts.get(R.string.cmd_err_dest_unknown)))
        }

        var tokenIndex = 0
        // On the command bar, the line may begin with "alias"
        if (rawTokens[0].text == "alias" && rawTokens.size > 1) {
            tokenIndex = 1
        }

        if (tokenIndex >= rawTokens.size) {
            return ParseResult.Failure(LineError(0, line.length, Texts.get(R.string.cmd_err_dest_unknown)))
        }

        val nameToken = rawTokens[tokenIndex]
        val letter = nameToken.text.lowercase()

        // Validate name format
        if (!letter.all { it.isCommandWordChar() }) {
            return ParseResult.Failure(LineError(nameToken.start, nameToken.end, Texts.get(R.string.cmd_err_letter_chars)))
        }

        val destTokens = rawTokens.subList(tokenIndex + 1, rawTokens.size)
        if (destTokens.isEmpty()) {
            return ParseResult.Failure(
                LineError(nameToken.end, commandSection.length, Texts.get(R.string.cmd_err_dest_unknown) + " · " + Texts.get(R.string.cmd_err_dest_hint)),
            )
        }

        val label = if (labelSection.isNotEmpty()) labelSection else letter

        // Destination type resolution (Section 1.3)
        val firstDest = destTokens[0]

        // 1. am start / am broadcast
        if (firstDest.text == "am" && destTokens.size > 1 && (destTokens[1].text == "start" || destTokens[1].text == "broadcast")) {
            return parseAm(letter, label, destTokens)
        }

        // 2. curl
        if (firstDest.text == "curl") {
            return parseCurl(letter, label, destTokens, commandSection)
        }

        // 3. intent:
        if (firstDest.text.startsWith("intent:")) {
            return parseIntentUri(letter, label, firstDest)
        }

        // 4. Matches URI scheme: ^[a-zA-Z][a-zA-Z0-9+.-]*:
        val scheme = PackRules.schemeOf(firstDest.text)
        if (scheme != null && destTokens.size == 1) {
            val template = firstDest.text.replace("{}", "{text}")
            val arg = inferArgKind(template)
            val action = CustomAction(
                letter = letter,
                label = label,
                kind = ActionKind.INTENT,
                template = template,
                packages = emptyList(),
                arg = arg,
                intentAction = ACTION_VIEW,
            )
            return ParseResult.Success(ParsedCommand.Action(letter, label, action))
        }

        // 5. Apelido (Alias): first token is existing letter, alias, or reserved word
        val reservedWords = BuiltInCommand.reserved()
        val isExistingAction = existingActions.any { it.letter == firstDest.text.lowercase() }
        val isExistingAlias = existingAliases.any { it.name.lowercase() == firstDest.text.lowercase() }
        val isReserved = firstDest.text.lowercase() in reservedWords

        if (isExistingAction || isExistingAlias || isReserved) {
            // Expansion cannot start with own letter
            if (firstDest.text.lowercase() == letter) {
                return ParseResult.Failure(LineError(firstDest.start, firstDest.end, Texts.get(R.string.cmd_err_alias_self)))
            }
            // Destination substring
            val expansion = commandSection.substring(destTokens[0].start).trim()
            val alias = CommandAlias(name = letter, expansion = expansion)
            return ParseResult.Success(ParsedCommand.Alias(letter, label, alias))
        }

        // 6. Unrecognized destination
        return ParseResult.Failure(
            LineError(
                destTokens[0].start,
                destTokens.last().end,
                Texts.get(R.string.cmd_err_dest_unknown) + " · " + Texts.get(R.string.cmd_err_dest_hint),
            ),
        )
    }

    private fun parseAm(letter: String, label: String, tokens: List<RawToken>): ParseResult {
        val target = if (tokens[1].text == "broadcast") "broadcast" else "activity"
        var intentAction = ACTION_VIEW
        var template = ""
        var mimeType: String? = null
        val categories = mutableListOf<String>()
        var component: String? = null
        val packages = mutableListOf<String>()
        var shortcutId: String? = null
        var smsFallback = false
        val extras = mutableListOf<IntentExtra>()

        var i = 2
        while (i < tokens.size) {
            val token = tokens[i]
            when (token.text) {
                "-a" -> {
                    if (i + 1 >= tokens.size) return ParseResult.Failure(LineError(token.start, token.end, Texts.get(R.string.cmd_err_flag_value_expected, "-a")))
                    intentAction = tokens[++i].text
                }

                "-d" -> {
                    if (i + 1 >= tokens.size) return ParseResult.Failure(LineError(token.start, token.end, Texts.get(R.string.cmd_err_flag_value_expected, "-d")))
                    template = tokens[++i].text.replace("{}", "{text}")
                }

                "-t" -> {
                    if (i + 1 >= tokens.size) return ParseResult.Failure(LineError(token.start, token.end, Texts.get(R.string.cmd_err_flag_value_expected, "-t")))
                    mimeType = tokens[++i].text
                }

                "-c" -> {
                    if (i + 1 >= tokens.size) return ParseResult.Failure(LineError(token.start, token.end, Texts.get(R.string.cmd_err_flag_value_expected, "-c")))
                    categories.add(tokens[++i].text)
                }

                "-n" -> {
                    if (i + 1 >= tokens.size) return ParseResult.Failure(LineError(token.start, token.end, Texts.get(R.string.cmd_err_flag_value_expected, "-n")))
                    component = tokens[++i].text
                }

                "-p" -> {
                    if (i + 1 >= tokens.size) return ParseResult.Failure(LineError(token.start, token.end, Texts.get(R.string.cmd_err_flag_value_expected, "-p")))
                    packages.add(tokens[++i].text)
                }

                "--shortcut" -> {
                    if (i + 1 >= tokens.size) return ParseResult.Failure(LineError(token.start, token.end, Texts.get(R.string.cmd_err_flag_value_expected, "--shortcut")))
                    shortcutId = tokens[++i].text
                }

                "--sms" -> {
                    smsFallback = true
                }

                "--es", "--ei", "--ez", "--el", "--ef", "--eu" -> {
                    val flag = token.text
                    if (i + 2 >= tokens.size) return ParseResult.Failure(LineError(token.start, token.end, Texts.get(R.string.cmd_err_flag_value_expected, flag)))
                    val key = tokens[++i].text
                    val value = tokens[++i].text.replace("{}", "{text}")
                    val type = when (flag) {
                        "--ei" -> ExtraType.INT
                        "--ez" -> ExtraType.BOOL
                        "--el" -> ExtraType.LONG
                        "--ef" -> ExtraType.FLOAT
                        "--eu" -> ExtraType.URI
                        else -> ExtraType.STRING
                    }
                    extras.add(IntentExtra(key, type, value))
                }

                else -> {
                    return ParseResult.Failure(LineError(token.start, token.end, Texts.get(R.string.cmd_err_flag_unknown, token.text)))
                }
            }
            i++
        }

        if (shortcutId != null) {
            val action = CustomAction(
                letter = letter,
                label = label,
                kind = ActionKind.SHORTCUT,
                template = shortcutId,
                packages = packages,
                arg = ArgKind.NONE,
            )
            return ParseResult.Success(ParsedCommand.Action(letter, label, action))
        }

        val allText = template + " " + extras.joinToString(" ") { it.value }
        val arg = inferArgKind(allText)
        val action = CustomAction(
            letter = letter,
            label = label,
            kind = ActionKind.INTENT,
            template = template,
            packages = packages,
            arg = arg,
            smsFallback = smsFallback,
            intentAction = intentAction,
            mimeType = mimeType,
            target = target,
            categories = categories,
            component = component,
            extras = extras,
        )
        return ParseResult.Success(ParsedCommand.Action(letter, label, action))
    }

    private fun parseCurl(letter: String, label: String, tokens: List<RawToken>, fullCommandSection: String): ParseResult {
        // Look for pipe separator: " | " outside quotes
        var pipeIndex = -1
        var inSingle = false
        var inDouble = false
        var k = 0
        while (k < fullCommandSection.length) {
            val c = fullCommandSection[k]
            when {
                c == '\\' && !inSingle -> k += 2

                c == '\'' && !inDouble -> {
                    inSingle = !inSingle
                    k++
                }

                c == '"' && !inSingle -> {
                    inDouble = !inDouble
                    k++
                }

                !inSingle && !inDouble && c == ' ' && k + 2 < fullCommandSection.length && fullCommandSection[k + 1] == '|' && fullCommandSection[k + 2] == ' ' -> {
                    pipeIndex = k
                    break
                }

                else -> k++
            }
        }

        val curlTokens: List<RawToken>
        val formatStr: String
        if (pipeIndex >= 0) {
            val beforePipe = fullCommandSection.substring(0, pipeIndex)
            val afterPipe = fullCommandSection.substring(pipeIndex + 3).trim()
            curlTokens = tokenize(beforePipe, 0).filter { it.start >= tokens[0].start }
            formatStr = afterPipe.replace("\\n", "\n")
        } else {
            curlTokens = tokens
            formatStr = ""
        }

        var method = HttpMethods.GET
        val headers = mutableMapOf<String, String>()
        var body = ""
        var bodyType = BodyType.JSON
        var list = ""
        var max = 5
        var upcoming = ""
        var pattern = ""
        var provider = ""
        var template = ""
        var targetSet = false

        var i = 1
        while (i < curlTokens.size) {
            val token = curlTokens[i]
            when (token.text) {
                "-X" -> {
                    if (i + 1 >= curlTokens.size) return ParseResult.Failure(LineError(token.start, token.end, Texts.get(R.string.cmd_err_flag_value_expected, "-X")))
                    method = curlTokens[++i].text.uppercase()
                }

                "-H" -> {
                    if (i + 1 >= curlTokens.size) return ParseResult.Failure(LineError(token.start, token.end, Texts.get(R.string.cmd_err_flag_value_expected, "-H")))
                    val hVal = curlTokens[++i].text
                    val colon = hVal.indexOf(':')
                    if (colon <= 0) {
                        return ParseResult.Failure(LineError(curlTokens[i].start, curlTokens[i].end, Texts.get(R.string.cmd_err_header_format)))
                    }
                    val hKey = hVal.substring(0, colon).trim()
                    val hValue = hVal.substring(colon + 1).trim()
                    if (hKey.equals("Authorization", ignoreCase = true) || hKey.equals("Cookie", ignoreCase = true)) {
                        return ParseResult.Failure(LineError(curlTokens[i].start, curlTokens[i].end, Texts.get(R.string.cmd_err_header_forbidden)))
                    }
                    headers[hKey] = hValue
                }

                "-d" -> {
                    if (i + 1 >= curlTokens.size) return ParseResult.Failure(LineError(token.start, token.end, Texts.get(R.string.cmd_err_flag_value_expected, "-d")))
                    body = curlTokens[++i].text.replace("{}", "{text}")
                }

                "--type" -> {
                    if (i + 1 >= curlTokens.size) return ParseResult.Failure(LineError(token.start, token.end, Texts.get(R.string.cmd_err_flag_value_expected, "--type")))
                    val typeName = curlTokens[++i].text.uppercase()
                    bodyType = BodyType.entries.firstOrNull { it.name == typeName } ?: BodyType.JSON
                }

                "--list" -> {
                    if (i + 1 >= curlTokens.size) return ParseResult.Failure(LineError(token.start, token.end, Texts.get(R.string.cmd_err_flag_value_expected, "--list")))
                    list = curlTokens[++i].text
                }

                "--max" -> {
                    if (i + 1 >= curlTokens.size) return ParseResult.Failure(LineError(token.start, token.end, Texts.get(R.string.cmd_err_flag_value_expected, "--max")))
                    max = curlTokens[++i].text.toIntOrNull() ?: 5
                }

                "--upcoming" -> {
                    if (i + 1 >= curlTokens.size) return ParseResult.Failure(LineError(token.start, token.end, Texts.get(R.string.cmd_err_flag_value_expected, "--upcoming")))
                    upcoming = curlTokens[++i].text
                }

                "--pattern" -> {
                    if (i + 1 >= curlTokens.size) return ParseResult.Failure(LineError(token.start, token.end, Texts.get(R.string.cmd_err_flag_value_expected, "--pattern")))
                    pattern = curlTokens[++i].text
                }

                else -> {
                    if (token.text.startsWith("-")) {
                        return ParseResult.Failure(LineError(token.start, token.end, Texts.get(R.string.cmd_err_flag_unknown, token.text)))
                    }
                    if (!targetSet) {
                        targetSet = true
                        val alvo = token.text.replace("{}", "{text}")
                        when {
                            alvo.startsWith("@") -> {
                                val rest = alvo.substring(1)
                                val slash = rest.indexOf('/')
                                if (slash >= 0) {
                                    provider = rest.substring(0, slash)
                                    template = rest.substring(slash)
                                } else {
                                    provider = rest
                                    template = ""
                                }
                            }

                            alvo.startsWith("https://") -> {
                                provider = ""
                                template = alvo
                            }

                            else -> {
                                return ParseResult.Failure(LineError(token.start, token.end, Texts.get(R.string.cmd_err_api_target)))
                            }
                        }
                    } else {
                        return ParseResult.Failure(LineError(token.start, token.end, Texts.get(R.string.cmd_err_flag_unknown, token.text)))
                    }
                }
            }
            i++
        }

        if (!targetSet) {
            return ParseResult.Failure(LineError(tokens[0].start, tokens.last().end, Texts.get(R.string.cmd_err_api_target)))
        }

        // Contact placeholders forbidden in API
        if ("{name}" in template || "{number}" in template || "{phone}" in template ||
            "{name}" in body || "{number}" in body || "{phone}" in body
        ) {
            return ParseResult.Failure(LineError(tokens[0].start, tokens.last().end, Texts.get(R.string.cmd_err_api_contact)))
        }

        val hasText = "{text}" in template || "{text}" in body || (1..9).any { "{$it}" in template || "{$it}" in body }
        val arg = if (hasText) ArgKind.TEXT else ArgKind.NONE

        val spec = ApiSpec(
            format = formatStr,
            list = list,
            max = max,
            upcoming = upcoming,
            method = method,
            body = body,
            bodyType = bodyType,
            headers = headers,
        )

        val action = CustomAction(
            letter = letter,
            label = label,
            kind = ActionKind.API,
            template = template,
            packages = emptyList(),
            arg = arg,
            pattern = pattern,
            api = spec,
            provider = provider,
        )
        return ParseResult.Success(ParsedCommand.Action(letter, label, action))
    }

    private fun parseIntentUri(letter: String, label: String, token: RawToken): ParseResult {
        val raw = token.text
        var dataUri = ""
        var intentAction = ACTION_VIEW
        var mimeType: String? = null
        val categories = mutableListOf<String>()
        var component: String? = null
        val packages = mutableListOf<String>()
        val extras = mutableListOf<IntentExtra>()

        val split = raw.substring("intent:".length).split("#Intent;")
        var rawData = split.firstOrNull().orEmpty()
        var scheme: String? = null

        if (split.size > 1) {
            val params = split[1].removeSuffix(";end").removeSuffix(";").split(';')
            for (p in params) {
                if (p.isEmpty()) continue
                val eq = p.indexOf('=')
                if (eq <= 0) continue
                val k = p.substring(0, eq)
                val v = p.substring(eq + 1)
                when {
                    k == "action" -> intentAction = v
                    k == "category" -> categories.add(v)
                    k == "component" -> component = v
                    k == "package" -> packages.add(v)
                    k == "type" -> mimeType = v
                    k == "scheme" -> scheme = v
                    k.startsWith("S.") -> extras.add(IntentExtra(k.substring(2), ExtraType.STRING, runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)))
                    k.startsWith("i.") -> extras.add(IntentExtra(k.substring(2), ExtraType.INT, v))
                    k.startsWith("b.") -> extras.add(IntentExtra(k.substring(2), ExtraType.BOOL, v))
                    k.startsWith("l.") -> extras.add(IntentExtra(k.substring(2), ExtraType.LONG, v))
                    k.startsWith("f.") -> extras.add(IntentExtra(k.substring(2), ExtraType.FLOAT, v))
                    k.startsWith("u.") -> extras.add(IntentExtra(k.substring(2), ExtraType.URI, v))
                }
            }
        }

        if (scheme != null && !rawData.contains(':')) {
            dataUri = "$scheme:$rawData"
        } else {
            dataUri = rawData
        }

        val allText = dataUri + " " + extras.joinToString(" ") { it.value }
        val arg = inferArgKind(allText)

        val action = CustomAction(
            letter = letter,
            label = label,
            kind = ActionKind.INTENT,
            template = dataUri,
            packages = packages,
            arg = arg,
            intentAction = intentAction,
            mimeType = mimeType,
            categories = categories,
            component = component,
            extras = extras,
        )
        return ParseResult.Success(ParsedCommand.Action(letter, label, action))
    }

    /**
     * Quotes a string for shell syntax if it contains special characters.
     */
    fun shellQuote(str: String): String {
        if (str.isEmpty()) return "''"
        // Safe characters that need no quoting
        val safe = str.all { it.isLetterOrDigit() || it in "_-./:@%+=*?&," }
        if (safe) return str
        // Use single quotes, escaping single quotes with '\''
        return "'" + str.replace("'", "'\\''") + "'"
    }

    /**
     * Produces the canonical command line string from an action.
     */
    fun serialize(action: CustomAction): String {
        val sb = StringBuilder()
        sb.append(action.letter)

        when (action.kind) {
            ActionKind.SHORTCUT -> {
                sb.append(" am start")
                val pkg = action.packages.firstOrNull()
                if (!pkg.isNullOrEmpty()) {
                    sb.append(" -p ").append(shellQuote(pkg))
                }
                sb.append(" --shortcut ").append(shellQuote(action.template))
            }

            ActionKind.INTENT -> {
                val isSimpleLink = action.intentAction == ACTION_VIEW &&
                    action.packages.isEmpty() &&
                    action.categories.isEmpty() &&
                    action.component == null &&
                    action.extras.isEmpty() &&
                    action.target == "activity" &&
                    !action.smsFallback &&
                    PackRules.schemeOf(action.template) != null

                if (isSimpleLink) {
                    sb.append(" ").append(action.template)
                } else {
                    sb.append(" am ").append(if (action.target == "broadcast") "broadcast" else "start")
                    if (action.intentAction != ACTION_VIEW) {
                        sb.append(" -a ").append(shellQuote(action.intentAction))
                    }
                    if (action.template.isNotEmpty()) {
                        sb.append(" -d ").append(shellQuote(action.template))
                    }
                    if (!action.mimeType.isNullOrEmpty()) {
                        sb.append(" -t ").append(shellQuote(action.mimeType))
                    }
                    action.categories.forEach {
                        sb.append(" -c ").append(shellQuote(it))
                    }
                    if (!action.component.isNullOrEmpty()) {
                        sb.append(" -n ").append(shellQuote(action.component))
                    }
                    action.packages.forEach {
                        sb.append(" -p ").append(shellQuote(it))
                    }
                    if (action.smsFallback) {
                        sb.append(" --sms")
                    }
                    action.effectiveExtras().forEach { extra ->
                        val flag = when (extra.type) {
                            ExtraType.INT -> "--ei"
                            ExtraType.BOOL -> "--ez"
                            ExtraType.LONG -> "--el"
                            ExtraType.FLOAT -> "--ef"
                            ExtraType.URI -> "--eu"
                            else -> "--es"
                        }
                        sb.append(" ").append(flag).append(" ").append(shellQuote(extra.key)).append(" ").append(shellQuote(extra.value))
                    }
                }
            }

            ActionKind.API -> {
                sb.append(" curl")
                val spec = action.api ?: ApiSpec("")
                if (spec.method != HttpMethods.GET) {
                    sb.append(" -X ").append(spec.method)
                }
                spec.headers.forEach { (k, v) ->
                    sb.append(" -H ").append(shellQuote("$k: $v"))
                }
                if (spec.writes && spec.body.isNotEmpty()) {
                    sb.append(" -d ").append(shellQuote(spec.body))
                }
                if (spec.writes && spec.bodyType != BodyType.JSON) {
                    sb.append(" --type ").append(spec.bodyType.name.lowercase())
                }
                if (action.provider.isNotEmpty()) {
                    val path = if (action.template.startsWith('/')) action.template else "/${action.template}"
                    sb.append(" @").append(action.provider).append(path)
                } else if (action.template.isNotEmpty()) {
                    sb.append(" ").append(shellQuote(action.template))
                }
                if (spec.list.isNotEmpty()) {
                    sb.append(" --list ").append(shellQuote(spec.list))
                }
                if (spec.max != 5) {
                    sb.append(" --max ").append(spec.max)
                }
                if (spec.upcoming.isNotEmpty()) {
                    sb.append(" --upcoming ").append(shellQuote(spec.upcoming))
                }
                if (action.pattern.isNotEmpty()) {
                    sb.append(" --pattern ").append(shellQuote(action.pattern))
                }
                if (spec.format.isNotEmpty()) {
                    sb.append(" | ").append(spec.format.replace("\n", "\\n"))
                }
            }
        }

        if (action.label.isNotEmpty() && action.label != action.letter) {
            sb.append(" # ").append(action.label)
        }

        return sb.toString()
    }

    /**
     * Produces the canonical command line string from an alias.
     */
    fun serialize(alias: CommandAlias): String {
        return "${alias.name} ${alias.expansion}"
    }

    fun serialize(command: ParsedCommand): String = when (command) {
        is ParsedCommand.Action -> serialize(command.action)
        is ParsedCommand.Alias -> serialize(command.alias)
    }

    /**
     * Validates a parsed command according to Section 1.7.
     */
    fun validate(
        command: ParsedCommand,
        existingActions: List<CustomAction> = emptyList(),
        existingAliases: List<CommandAlias> = emptyList(),
        providers: List<ApiProvider> = Providers.BUILT_IN,
        ignore: Any? = null,
    ): CommandValidation {
        val issues = mutableListOf<ValidationIssue>()

        when (command) {
            is ParsedCommand.Alias -> {
                val err = Aliases.validate(
                    command.name,
                    command.alias.expansion,
                    existingAliases,
                    existingActions,
                    ignore as? CommandAlias,
                )
                if (err != null) issues.add(ValidationIssue(err, isError = true))
            }

            is ParsedCommand.Action -> {
                val action = command.action
                // Letter validation
                val letterErr = CustomActions.validateLetter(
                    action.letter,
                    existingActions,
                    ignore as? CustomAction,
                )
                if (letterErr != null) issues.add(ValidationIssue(letterErr, isError = true))

                if (action.kind == ActionKind.API) {
                    val spec = action.api ?: ApiSpec("")

                    // Provider check
                    if (action.provider.isNotEmpty() && providers.none { it.id == action.provider } && !Providers.isBuiltIn(action.provider)) {
                        issues.add(ValidationIssue(Texts.get(R.string.cmd_err_provider_not_found, action.provider), isError = true))
                    }

                    // GET without format
                    if (!spec.writes && spec.format.isBlank()) {
                        issues.add(ValidationIssue(Texts.get(R.string.cmd_err_api_no_format), isError = true))
                    }

                    // CONTACT in API
                    if (action.arg == ArgKind.CONTACT || action.arg == ArgKind.CONTACT_AND_TEXT) {
                        issues.add(ValidationIssue(Texts.get(R.string.cmd_err_api_contact), isError = true))
                    }

                    // Header check
                    if (spec.headers.keys.any { it.equals("Authorization", ignoreCase = true) || it.equals("Cookie", ignoreCase = true) }) {
                        issues.add(ValidationIssue(Texts.get(R.string.cmd_err_header_forbidden), isError = true))
                    }

                    // JSON body validation
                    if (spec.writes && spec.bodyType == BodyType.JSON && spec.body.isNotBlank()) {
                        val testBody = spec.body.replace("{text}", "teste").replace(Regex("\\{[0-9]\\}"), "teste").replace("{year}", "2026")
                        val validJson = runCatching {
                            val v = JSONTokener(testBody.trim()).nextValue()
                            v is JSONObject || v is JSONArray
                        }.getOrDefault(false)
                        if (!validJson) {
                            issues.add(ValidationIssue(Texts.get(R.string.cmd_err_json_body_invalid), isError = true))
                        }
                    }

                    // Warnings
                    if (spec.writes) {
                        issues.add(ValidationIssue(Texts.get(R.string.cmd_warn_writes), isError = false))
                    }
                } else if (action.kind == ActionKind.INTENT) {
                    // Empty extra warning
                    action.effectiveExtras().forEach { extra ->
                        if (extra.value.isBlank()) {
                            issues.add(ValidationIssue(Texts.get(R.string.cmd_warn_extra_empty, extra.key), isError = false))
                        }
                    }
                }

                // Sensitive variable check (warning)
                val allText = action.template + " " + action.api?.body.orEmpty() + " " + action.effectiveExtras().joinToString(" ") { it.value }
                val sensitiveVars = Variables.used(allText).intersect(Variables.SENSITIVE)
                sensitiveVars.forEach { v ->
                    issues.add(ValidationIssue(Texts.get(R.string.cmd_warn_sensitive_var, v.name.lowercase()), isError = false))
                }
            }
        }

        return CommandValidation(issues)
    }
}
