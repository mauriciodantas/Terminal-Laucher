package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommandLineTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private val sampleActions = listOf(
        CustomActions.WHATSAPP,
        CustomAction("yt", "YouTube", ActionKind.INTENT, "https://www.youtube.com/results?search_query={text}", listOf("com.google.android.youtube"), ArgKind.TEXT),
        CustomAction("cep", "CEP", ActionKind.API, "/ws/{text}/json/", emptyList(), ArgKind.TEXT, api = ApiSpec("{logradouro}"), provider = "viacep"),
    )

    private val sampleAliases = listOf(
        CommandAlias("mae", "ligar maria"),
        CommandAlias("casa", "rota casa"),
    )

    // ---- 1.1 - 1.4: Link parsing ----

    @Test
    fun parseSimpleLinkWithLabel() {
        val result = CommandLine.parse("mp geo:0,0?q={text} # Padaria")
        assertTrue(result is ParseResult.Success)
        val cmd = (result as ParseResult.Success).command as ParsedCommand.Action
        assertEquals("mp", cmd.name)
        assertEquals("Padaria", cmd.label)
        assertEquals(ActionKind.INTENT, cmd.action.kind)
        assertEquals("geo:0,0?q={text}", cmd.action.template)
        assertEquals(ArgKind.TEXT, cmd.action.arg)
        assertEquals(ACTION_VIEW, cmd.action.intentAction)
        assertEquals("activity", cmd.action.target)
    }

    @Test
    fun parsePrefixedWithAlias() {
        val result = CommandLine.parse("alias mp geo:0,0?q={text}")
        assertTrue(result is ParseResult.Success)
        val cmd = (result as ParseResult.Success).command as ParsedCommand.Action
        assertEquals("mp", cmd.name)
        assertEquals("mp", cmd.label)
        assertEquals("geo:0,0?q={text}", cmd.action.template)
    }

    @Test
    fun parseContactPlaceholders() {
        val phoneResult = CommandLine.parse("lig tel:{phone}") as ParseResult.Success
        assertEquals(ArgKind.CONTACT, (phoneResult.command as ParsedCommand.Action).action.arg)

        val bothResult = CommandLine.parse("msg smsto:{number}?body={text}") as ParseResult.Success
        assertEquals(ArgKind.CONTACT_AND_TEXT, (bothResult.command as ParsedCommand.Action).action.arg)

        val noneResult = CommandLine.parse("site https://google.com") as ParseResult.Success
        assertEquals(ArgKind.NONE, (noneResult.command as ParsedCommand.Action).action.arg)
    }

    @Test
    fun emptyBracesExpandedToText() {
        val result = CommandLine.parse("b https://search.com?q={}") as ParseResult.Success
        assertEquals("https://search.com?q={text}", (result.command as ParsedCommand.Action).action.template)
    }

    // ---- 1.5: Intent (am start / broadcast) ----

    @Test
    fun parseAmStartFull() {
        val line = "despertar am start -a android.intent.action.SET_ALARM -p com.google.android.deskclock --ei android.intent.extra.alarm.HOUR '{1}' --ei android.intent.extra.alarm.MINUTES '{2}' --ez android.intent.extra.alarm.SKIP_UI 'true' # Alarme"
        val result = CommandLine.parse(line)
        assertTrue(result is ParseResult.Success)
        val action = ((result as ParseResult.Success).command as ParsedCommand.Action).action
        assertEquals("despertar", action.letter)
        assertEquals("Alarme", action.label)
        assertEquals("android.intent.action.SET_ALARM", action.intentAction)
        assertEquals(listOf("com.google.android.deskclock"), action.packages)
        assertEquals(ArgKind.TEXT, action.arg) // {1} and {2}
        assertEquals(3, action.extras.size)
        assertEquals(IntentExtra("android.intent.extra.alarm.HOUR", ExtraType.INT, "{1}"), action.extras[0])
        assertEquals(IntentExtra("android.intent.extra.alarm.MINUTES", ExtraType.INT, "{2}"), action.extras[1])
        assertEquals(IntentExtra("android.intent.extra.alarm.SKIP_UI", ExtraType.BOOL, "true"), action.extras[2])
    }

    @Test
    fun parseAmBroadcastWithCategoriesAndComponent() {
        val line = "ev am broadcast -a com.example.NOTIFY -c com.example.CATEGORY -n com.example/.Receiver --es key value"
        val result = CommandLine.parse(line) as ParseResult.Success
        val action = (result.command as ParsedCommand.Action).action
        assertEquals("broadcast", action.target)
        assertEquals("com.example.NOTIFY", action.intentAction)
        assertEquals(listOf("com.example.CATEGORY"), action.categories)
        assertEquals("com.example/.Receiver", action.component)
        assertEquals(listOf(IntentExtra("key", ExtraType.STRING, "value")), action.extras)
    }

    @Test
    fun parseAmShortcut() {
        val line = "sc am start -p com.example.app --shortcut my_shortcut # Atalho"
        val result = CommandLine.parse(line) as ParseResult.Success
        val action = (result.command as ParsedCommand.Action).action
        assertEquals(ActionKind.SHORTCUT, action.kind)
        assertEquals("my_shortcut", action.template)
        assertEquals(listOf("com.example.app"), action.packages)
        assertEquals(ArgKind.NONE, action.arg)
        assertEquals("Atalho", action.label)
    }

    @Test
    fun parseAmUnknownFlagReturnsErrorWithPosition() {
        val line = "cmd am start -a android.intent.action.VIEW --invalid-flag foo"
        val result = CommandLine.parse(line)
        assertTrue(result is ParseResult.Failure)
        val err = (result as ParseResult.Failure).error
        assertTrue(err.start >= line.indexOf("--invalid-flag"))
        assertTrue(err.end <= line.indexOf("--invalid-flag") + "--invalid-flag".length)
    }

    @Test
    fun parseAmMissingFlagValueReturnsError() {
        val line = "cmd am start -a"
        val result = CommandLine.parse(line)
        assertTrue(result is ParseResult.Failure)
    }

    // ---- 1.3: intent: URI ----

    @Test
    fun parseIntentUriScheme() {
        val line = "share intent:#Intent;action=android.intent.action.SEND;type=text/plain;S.android.intent.extra.TEXT={text};end # Compartilhar"
        val result = CommandLine.parse(line) as ParseResult.Success
        val action = (result.command as ParsedCommand.Action).action
        assertEquals("android.intent.action.SEND", action.intentAction)
        assertEquals("text/plain", action.mimeType)
        assertEquals(listOf(IntentExtra("android.intent.extra.TEXT", ExtraType.STRING, "{text}")), action.extras)
        assertEquals(ArgKind.TEXT, action.arg)
        assertEquals("Compartilhar", action.label)
    }

    // ---- 1.6: API (curl) ----

    @Test
    fun parseCurlGetWithFormat() {
        val line = "cep curl @viacep/ws/{text}/json/ | {logradouro} · {bairro} · {localidade}/{uf} # CEP"
        val result = CommandLine.parse(line) as ParseResult.Success
        val action = (result.command as ParsedCommand.Action).action
        assertEquals(ActionKind.API, action.kind)
        assertEquals("viacep", action.provider)
        assertEquals("/ws/{text}/json/", action.template)
        assertEquals("{logradouro} · {bairro} · {localidade}/{uf}", action.api?.format)
        assertEquals(HttpMethods.GET, action.api?.method)
        assertEquals(ArgKind.TEXT, action.arg)
        assertEquals("CEP", action.label)
    }

    @Test
    fun parseCurlPostWithHeadersAndBody() {
        val line = "task curl -X POST -H 'Accept-Language: pt-BR' -d '{\"title\":\"{text}\"}' --type json @todo/v1/tasks | {id} # Tarefa"
        val result = CommandLine.parse(line) as ParseResult.Success
        val action = (result.command as ParsedCommand.Action).action
        assertEquals(HttpMethods.POST, action.api?.method)
        assertEquals(mapOf("Accept-Language" to "pt-BR"), action.api?.headers)
        assertEquals("{\"title\":\"{text}\"}", action.api?.body)
        assertEquals(BodyType.JSON, action.api?.bodyType)
        assertEquals("todo", action.provider)
        assertEquals("/v1/tasks", action.template)
        assertEquals("{id}", action.api?.format)
    }

    @Test
    fun parseCurlForbiddenHeadersReturnsError() {
        val line = "auth curl -H 'Authorization: Bearer 123' @provider/test"
        val result = CommandLine.parse(line)
        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun parseCurlContactPlaceholderReturnsError() {
        val line = "api curl @prov/test?n={number}"
        val result = CommandLine.parse(line)
        assertTrue(result is ParseResult.Failure)
    }

    // ---- 1.3: Apelidos (Aliases) ----

    @Test
    fun parseAliasFromReservedWord() {
        val result = CommandLine.parse("m abrir google maps") as ParseResult.Success
        val alias = (result.command as ParsedCommand.Alias).alias
        assertEquals("m", alias.name)
        assertEquals("abrir google maps", alias.expansion)
    }

    @Test
    fun parseAliasFromExistingAction() {
        val result = CommandLine.parse("zap w ana oi", existingActions = sampleActions) as ParseResult.Success
        val alias = (result.command as ParsedCommand.Alias).alias
        assertEquals("zap", alias.name)
        assertEquals("w ana oi", alias.expansion)
    }

    @Test
    fun parseSelfAliasReturnsError() {
        val result = CommandLine.parse("w w ana", existingActions = sampleActions)
        assertTrue(result is ParseResult.Failure)
    }

    // ---- 1.3: Unrecognized destination ----

    @Test
    fun parseUnrecognizedDestinationReturnsSuggestions() {
        val result = CommandLine.parse("cmd unknown_token foo bar")
        assertTrue(result is ParseResult.Failure)
        val err = (result as ParseResult.Failure).error
        assertTrue("abrir" in err.message)
    }

    // ---- Round-trip property (parse(serialize(x)) == x) ----

    @Test
    fun roundTripPureLink() {
        val action = CustomAction(
            letter = "mp",
            label = "Padaria",
            kind = ActionKind.INTENT,
            template = "geo:0,0?q={text}",
            packages = emptyList(),
            arg = ArgKind.TEXT,
        )
        val serialized = CommandLine.serialize(action)
        assertEquals("mp geo:0,0?q={text} # Padaria", serialized)
        val back = (CommandLine.parse(serialized) as ParseResult.Success).command as ParsedCommand.Action
        assertEquals(action, back.action)
    }

    @Test
    fun roundTripAmStart() {
        val action = CustomAction(
            letter = "despertar",
            label = "Alarme",
            kind = ActionKind.INTENT,
            template = "",
            packages = listOf("com.google.android.deskclock"),
            arg = ArgKind.TEXT,
            intentAction = "android.intent.action.SET_ALARM",
            extras = listOf(
                IntentExtra("android.intent.extra.alarm.HOUR", ExtraType.INT, "{1}"),
                IntentExtra("android.intent.extra.alarm.MINUTES", ExtraType.INT, "{2}"),
                IntentExtra("android.intent.extra.alarm.SKIP_UI", ExtraType.BOOL, "true"),
            ),
        )
        val serialized = CommandLine.serialize(action)
        val back = (CommandLine.parse(serialized) as ParseResult.Success).command as ParsedCommand.Action
        assertEquals(action, back.action)
    }

    @Test
    fun roundTripAmBroadcast() {
        val action = CustomAction(
            letter = "bc",
            label = "bc",
            kind = ActionKind.INTENT,
            template = "custom://event",
            packages = emptyList(),
            arg = ArgKind.NONE,
            target = "broadcast",
            intentAction = "com.example.EVENT",
            categories = listOf("com.example.CAT"),
            extras = listOf(IntentExtra("foo", ExtraType.STRING, "bar")),
        )
        val serialized = CommandLine.serialize(action)
        val back = (CommandLine.parse(serialized) as ParseResult.Success).command as ParsedCommand.Action
        assertEquals(action, back.action)
    }

    @Test
    fun roundTripShortcut() {
        val action = CustomAction(
            letter = "sc",
            label = "Atalho",
            kind = ActionKind.SHORTCUT,
            template = "shortcut_123",
            packages = listOf("com.example.app"),
            arg = ArgKind.NONE,
        )
        val serialized = CommandLine.serialize(action)
        val back = (CommandLine.parse(serialized) as ParseResult.Success).command as ParsedCommand.Action
        assertEquals(action, back.action)
    }

    @Test
    fun roundTripApiGet() {
        val action = CustomAction(
            letter = "cep",
            label = "CEP",
            kind = ActionKind.API,
            template = "/ws/{text}/json/",
            provider = "viacep",
            packages = emptyList(),
            arg = ArgKind.TEXT,
            api = ApiSpec(format = "{logradouro} · {bairro}"),
        )
        val serialized = CommandLine.serialize(action)
        val back = (CommandLine.parse(serialized) as ParseResult.Success).command as ParsedCommand.Action
        assertEquals(action, back.action)
    }

    @Test
    fun roundTripApiPost() {
        val action = CustomAction(
            letter = "task",
            label = "Nova Tarefa",
            kind = ActionKind.API,
            template = "/v1/tasks",
            provider = "todo",
            packages = emptyList(),
            arg = ArgKind.TEXT,
            api = ApiSpec(
                method = "POST",
                headers = mapOf("X-App" to "TB"),
                body = "{\"title\":\"{text}\"}",
                bodyType = BodyType.JSON,
                format = "{id}",
            ),
        )
        val serialized = CommandLine.serialize(action)
        val back = (CommandLine.parse(serialized) as ParseResult.Success).command as ParsedCommand.Action
        assertEquals(action, back.action)
    }

    @Test
    fun roundTripAlias() {
        val alias = CommandAlias("m", "abrir maps")
        val serialized = CommandLine.serialize(alias)
        val back = (CommandLine.parse(serialized) as ParseResult.Success).command as ParsedCommand.Alias
        assertEquals(alias, back.alias)
    }

    @Test
    fun serializationIsIdempotent() {
        val line = "mp geo:0,0?q={text} # Padaria"
        val parsed1 = (CommandLine.parse(line) as ParseResult.Success).command
        val s1 = CommandLine.serialize(parsed1)
        val parsed2 = (CommandLine.parse(s1) as ParseResult.Success).command
        val s2 = CommandLine.serialize(parsed2)
        assertEquals(s1, s2)
    }

    // ---- 1.7: Validação ----

    @Test
    fun validateCatchesUnknownProvider() {
        val action = CustomAction("x", "X", ActionKind.API, "/test", emptyList(), ArgKind.NONE, provider = "inexistente", api = ApiSpec("{val}"))
        val cmd = ParsedCommand.Action("x", "X", action)
        val validation = CommandLine.validate(cmd, providers = Providers.BUILT_IN)
        assertFalse(validation.isValid)
        assertTrue(validation.errors.any { "inexistente" in it })
    }

    @Test
    fun validateCatchesGetWithoutFormat() {
        val action = CustomAction("x", "X", ActionKind.API, "/test", emptyList(), ArgKind.NONE, provider = "open-meteo", api = ApiSpec(""))
        val cmd = ParsedCommand.Action("x", "X", action)
        val validation = CommandLine.validate(cmd, providers = Providers.BUILT_IN)
        assertFalse(validation.isValid)
    }

    @Test
    fun validateCatchesInvalidJsonBody() {
        val action = CustomAction("x", "X", ActionKind.API, "/test", emptyList(), ArgKind.TEXT, provider = "open-meteo", api = ApiSpec(format = "", method = "POST", body = "{not a json}"))
        val cmd = ParsedCommand.Action("x", "X", action)
        val validation = CommandLine.validate(cmd, providers = Providers.BUILT_IN)
        assertFalse(validation.isValid)
    }

    @Test
    fun validateWarnsOnWritingMethodAndSensitiveVariable() {
        val action = CustomAction(
            "x", "X", ActionKind.API, "/test?loc={location}", emptyList(), ArgKind.NONE, provider = "open-meteo",
            api = ApiSpec(method = "POST", format = "{ok}"),
        )
        val cmd = ParsedCommand.Action("x", "X", action)
        val validation = CommandLine.validate(cmd, providers = Providers.BUILT_IN)
        assertTrue(validation.isValid) // Warnings allow saving
        assertTrue(validation.warnings.isNotEmpty())
    }

    // ---- Fase 2: AliasCommand, Contextual Autocomplete e Unalias ----

    private fun testContext(): CommandContext {
        return CommandContext(
            apps = listOf(AppEntry("WhatsApp", "com.whatsapp"), AppEntry("Maps", "com.google.android.apps.maps")),
            contacts = emptyList(),
            contactsGranted = true,
            custom = listOf(
                CustomAction(
                    letter = "w",
                    label = "WhatsApp",
                    kind = ActionKind.INTENT,
                    template = "https://api.whatsapp.com/send?phone={number}&text={text}",
                    packages = listOf("com.whatsapp"),
                    arg = ArgKind.CONTACT_AND_TEXT,
                ),
            ),
            aliases = listOf(CommandAlias("yt", "abrir youtube")),
            extras = CommandExtras(providers = listOf(ApiProvider("viacep", "ViaCEP", "https://viacep.com.br"))),
        )
    }

    @Test
    fun aliasEmptyListsAllCommands() {
        val ctx = testContext()
        val analysis = AliasCommand.analyze("", ctx)
        assertEquals(2, analysis.suggestions.size)
        assertTrue(analysis.picksOnEnter)
        assertTrue(analysis.suggestions.any { "w" in it.completion })
        assertTrue(analysis.suggestions.any { "yt" in it.completion })
    }

    @Test
    fun aliasSingleExistingNameFillsLine() {
        val ctx = testContext()
        val analysis = AliasCommand.analyze("w", ctx)
        assertEquals(1, analysis.suggestions.size)
        assertTrue(analysis.action is CommandAction.FillLine)
        assertTrue("https://api.whatsapp.com" in (analysis.action as CommandAction.FillLine).text)
    }

    @Test
    fun aliasAutocompletePlaceholders() {
        val ctx = testContext()
        val analysis = AliasCommand.analyze("g https://google.com/search?q={", ctx)
        assertTrue(analysis.suggestions.isNotEmpty())
        assertTrue(analysis.suggestions.any { "{text}" in it.completion })
        assertTrue(analysis.suggestions.any { "{number}" in it.completion })
    }

    @Test
    fun aliasAutocompleteEmptyBracesExpandsToText() {
        val ctx = testContext()
        val analysis = AliasCommand.analyze("g https://google.com/search?q={}", ctx)
        assertEquals(1, analysis.suggestions.size)
        assertTrue(analysis.suggestions.first().completion.endsWith("{text}"))
    }

    @Test
    fun aliasAutocompleteActionsFromCatalog() {
        val ctx = testContext()
        val analysis = AliasCommand.analyze("al am start -a ", ctx)
        assertTrue(analysis.suggestions.isNotEmpty())
        assertTrue(analysis.suggestions.any { "SET_ALARM" in it.completion })
    }

    @Test
    fun aliasAutocompleteExtrasFromCatalog() {
        val ctx = testContext()
        val analysis = AliasCommand.analyze("al am start -a android.intent.action.SET_ALARM --e", ctx)
        assertTrue(analysis.suggestions.isNotEmpty())
        assertTrue(analysis.suggestions.any { "EXTRA_HOUR" in it.label || "alarm.HOUR" in it.completion })
    }

    @Test
    fun aliasAutocompletePackages() {
        val ctx = testContext()
        val analysis = AliasCommand.analyze("app am start -p ", ctx)
        assertTrue(analysis.suggestions.isNotEmpty())
        assertTrue(analysis.suggestions.any { "com.whatsapp" in it.completion })
    }

    @Test
    fun aliasAutocompleteProviders() {
        val ctx = testContext()
        val analysis = AliasCommand.analyze("cep curl @", ctx)
        assertTrue(analysis.suggestions.isNotEmpty())
        assertTrue(analysis.suggestions.any { "@viacep/" in it.completion })
    }

    @Test
    fun aliasAutocompleteHttpMethods() {
        val ctx = testContext()
        val analysis = AliasCommand.analyze("call curl -X ", ctx)
        assertTrue(analysis.suggestions.isNotEmpty())
        assertTrue(analysis.suggestions.any { "-X POST" in it.completion })
    }

    @Test
    fun aliasSaveNewCommand() {
        val ctx = testContext()
        val analysis = AliasCommand.analyze("g https://google.com/search?q={text} # Google", ctx)
        assertEquals(Tone.OK, analysis.tone)
        assertTrue(analysis.action is CommandAction.SaveCommand)
        val save = analysis.action as CommandAction.SaveCommand
        assertEquals("g", save.command.name)
        assertFalse(save.isReplacement)
    }

    @Test
    fun aliasSaveExistingCommandWarnsConflict() {
        val ctx = testContext()
        val analysis = AliasCommand.analyze("w https://web.whatsapp.com", ctx)
        assertEquals(Tone.WARN, analysis.tone)
        assertTrue(analysis.action is CommandAction.SaveCommand)
        val save = analysis.action as CommandAction.SaveCommand
        assertTrue(save.isReplacement)
    }

    @Test
    fun unaliasCommandExistingAndNotFound() {
        val ctx = testContext()
        val found = UnaliasCommand.analyze("w", ctx)
        assertEquals(Tone.OK, found.tone)
        assertTrue(found.action is CommandAction.RemoveCommand)
        assertEquals("w", (found.action as CommandAction.RemoveCommand).name)

        val notFound = UnaliasCommand.analyze("inexistente", ctx)
        assertEquals(Tone.ERROR, notFound.tone)
        assertNull(notFound.action)
    }

    @Test
    fun realCommandsRoundTrip() {
        val w = CustomActions.WHATSAPP
        val yt = CustomAction(
            letter = "yt",
            label = "YouTube",
            kind = ActionKind.INTENT,
            template = "https://www.youtube.com/results?search_query={text}",
            packages = listOf("com.google.android.youtube"),
            arg = ArgKind.TEXT,
        )
        val cep = CustomAction(
            letter = "cep",
            label = "CEP",
            kind = ActionKind.API,
            template = "/ws/{text}/json/",
            packages = emptyList(),
            arg = ArgKind.TEXT,
            api = ApiSpec("{logradouro}"),
            provider = "viacep",
        )
        val f = CustomAction(
            letter = "f",
            label = "Lanterna",
            kind = ActionKind.SHORTCUT,
            template = "torch",
            packages = listOf("com.android.systemui"),
            arg = ArgKind.NONE,
        )
        val casa = CommandAlias("casa", "rota casa")
        val mae = CommandAlias("mae", "ligar maria")

        val realActions = listOf(w, yt, cep, f)
        val realAliases = listOf(casa, mae)

        for (action in realActions) {
            val line = CommandLine.serialize(action)
            assertTrue("Line for ${action.letter} should not be empty", line.isNotBlank())
            val parsed = CommandLine.parse(line, realActions, realAliases)
            assertTrue("Failed to parse serialized line for ${action.letter}: $line", parsed is ParseResult.Success)
            val parsedCmd = (parsed as ParseResult.Success).command
            assertTrue(parsedCmd is ParsedCommand.Action)
            val roundTripAction = (parsedCmd as ParsedCommand.Action).action
            assertEquals(action.letter, roundTripAction.letter)
            assertEquals(action.label, roundTripAction.label)
            assertEquals(action.kind, roundTripAction.kind)
            assertEquals(action.template, roundTripAction.template)
            assertEquals(action.packages, roundTripAction.packages)
            assertEquals(action.arg, roundTripAction.arg)
            if (action.kind == ActionKind.API) {
                assertEquals(action.provider, roundTripAction.provider)
                assertEquals(action.api?.format, roundTripAction.api?.format)
            }
            val reserialized = CommandLine.serialize(roundTripAction)
            assertEquals("Reserialized line must match for ${action.letter}", line, reserialized)
        }

        for (alias in realAliases) {
            val line = CommandLine.serialize(alias)
            assertTrue("Line for alias ${alias.name} should not be empty", line.isNotBlank())
            val parsed = CommandLine.parse(line, realActions, realAliases)
            assertTrue("Failed to parse serialized line for alias ${alias.name}: $line", parsed is ParseResult.Success)
            val parsedCmd = (parsed as ParseResult.Success).command
            assertTrue(parsedCmd is ParsedCommand.Alias)
            val roundTripAlias = (parsedCmd as ParsedCommand.Alias).alias
            assertEquals(alias.name, roundTripAlias.name)
            assertEquals(alias.expansion, roundTripAlias.expansion)
            val reserialized = CommandLine.serialize(roundTripAlias)
            assertEquals("Reserialized line must match for alias ${alias.name}", line, reserialized)
        }
    }
}

