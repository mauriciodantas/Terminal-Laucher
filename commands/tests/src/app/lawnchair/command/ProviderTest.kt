package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** API providers: how their requests are built, where a secret may go, and how they travel in packs. */
class ProviderTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private val weather = ApiProvider(
        id = "owm",
        name = "OpenWeather",
        baseUrl = "https://api.openweathermap.org",
        auth = AuthSpec.ApiKey("appid", KeyPlacement.QUERY),
    )

    private fun ready(p: Prepared): HttpRequest = (p as Prepared.Ready).request

    // ---- requests ----

    @Test fun keyInTheQueryIsAddedAndHidden() {
        val r = ready(ProviderRequests.prepare(weather, "/data/2.5/weather?q=recife", "s3cr3t"))
        assertEquals("https://api.openweathermap.org/data/2.5/weather?q=recife&appid=s3cr3t", r.url)
        assertEquals("https://api.openweathermap.org/data/2.5/weather?q=recife&appid=•••", r.shown)
        assertFalse(r.shown.contains("s3cr3t"))
    }

    @Test fun keyInAHeaderWithPrefix() {
        val p = weather.copy(auth = AuthSpec.ApiKey("Authorization", KeyPlacement.HEADER, "Token "))
        val r = ready(ProviderRequests.prepare(p, "v1/me", "abc"))
        assertEquals("https://api.openweathermap.org/v1/me", r.url)
        assertEquals("Token abc", r.headers["Authorization"])
        assertFalse(r.shown.contains("abc"))
    }

    @Test fun bearerAndBasic() {
        assertEquals("Bearer t0k", ready(ProviderRequests.prepare(weather.copy(auth = AuthSpec.Bearer), "", "t0k")).headers["Authorization"])
        val basic = ready(ProviderRequests.prepare(weather.copy(auth = AuthSpec.Basic("ana")), "/x", "pw"))
        assertEquals("Basic " + Base64.getEncoder().encodeToString("ana:pw".toByteArray()), basic.headers["Authorization"])
    }

    @Test fun noSecretYetMeansAsking() {
        assertTrue(ProviderRequests.prepare(weather, "/x", null) is Prepared.NeedsSecret)
        assertTrue(ProviderRequests.prepare(weather, "/x", "") is Prepared.NeedsSecret)
        assertTrue(ProviderRequests.prepare(weather.copy(auth = AuthSpec.None), "/x", null) is Prepared.Ready)
    }

    @Test fun theSecretNeverLeavesTheProvidersHosts() {
        // A command cannot point the provider somewhere else: a full address must be one of its hosts.
        assertTrue(ProviderRequests.prepare(weather, "https://evil.example/steal", "s") is Prepared.Blocked)
        assertTrue(ProviderRequests.prepare(weather, "http://api.openweathermap.org/x", "s") is Prepared.Blocked)
        assertTrue(ProviderRequests.prepare(weather, "https://api.openweathermap.org.evil.example/x", "s") is Prepared.Blocked)
        val withMirror = weather.copy(hosts = listOf("pro.openweathermap.org"))
        assertTrue(ProviderRequests.prepare(withMirror, "https://pro.openweathermap.org/x", "s") is Prepared.Ready)
        // Redirects too: with a secret only to its hosts, without one to any https address.
        assertFalse(ProviderRequests.mayFollow(weather, "https://evil.example/"))
        assertTrue(ProviderRequests.mayFollow(weather, "https://api.openweathermap.org/v2"))
        assertTrue(ProviderRequests.mayFollow(Providers.OPEN_METEO, "https://cdn.example/"))
        assertFalse(ProviderRequests.mayFollow(Providers.OPEN_METEO, "http://cdn.example/"))
    }

    @Test fun builtInOpenMeteoAnswersFromItsTwoHosts() {
        assertTrue(ProviderRequests.prepare(Providers.OPEN_METEO, Weather.geocodeUrl("recife", "pt"), null) is Prepared.Ready)
        assertTrue(ProviderRequests.prepare(Providers.OPEN_METEO, Weather.forecastUrl(-8.05, -34.88), null) is Prepared.Ready)
    }

    // ---- validation ----

    @Test fun invalidProvidersAreRefused() {
        assertNull(Providers.validate(weather))
        assertNotNull(Providers.validate(weather.copy(id = "Bad Id")))
        assertNotNull(Providers.validate(weather.copy(baseUrl = "http://api.x.com")))
        assertNotNull(Providers.validate(weather.copy(auth = AuthSpec.ApiKey(" "))))
        assertNotNull(Providers.validate(weather.copy(headers = mapOf("authorization" to "Bearer x"))))
        assertNotNull(Providers.validateId("open-meteo", emptyList()))
        assertNotNull(Providers.validateId("owm", listOf(weather)))
        assertNull(Providers.validateId("owm", listOf(weather), ignore = "owm"))
    }

    // ---- packs ----

    private val action = CustomAction(
        "clima2", "Clima", ActionKind.API, "/data/2.5/weather?q={text}", emptyList(), ArgKind.TEXT,
        api = ApiSpec("{name} · {main.temp}°"), provider = "owm",
    )

    @Test fun providersTravelInPacksWithoutSecrets() {
        val raw = CommandPacks.encode(listOf(action), emptyList(), listOf(weather))
        assertFalse(raw.contains("secret"))
        val pack = CommandPacks.decode(raw)
        assertEquals(listOf(weather), pack.providers)
        assertEquals(listOf(action), pack.actions)
        // A field named like a secret in a file is ignored, never read.
        val tampered = JSONObject(raw).apply {
            getJSONArray("providers").getJSONObject(0).put("secret", "x")
        }.toString()
        assertEquals(listOf(weather), CommandPacks.decode(tampered).providers)
    }

    @Test fun badProvidersInAPackAreDropped() {
        val bad = listOf(weather.copy(id = "evil", baseUrl = "http://x.com"), weather.copy(id = "open-meteo"))
        assertTrue(CommandPacks.decode(CommandPacks.encode(listOf(action), emptyList(), bad)).providers.isEmpty())
    }

    @Test fun importKeepsTheUsersProvider() {
        val mine = weather.copy(name = "Minha conta")
        val pack = CommandPack(listOf(action), emptyList(), providers = listOf(weather))
        val merge = PackRules.merge(emptyList(), emptyList(), pack, providers = listOf(mine))
        assertEquals(listOf(mine), merge.providers)
        assertTrue(merge.addedProviders.isEmpty())
        assertNotNull(PackRules.preview(emptyList(), emptyList(), pack, listOf(mine)).providers.single().caution)
        val fresh = PackRules.merge(emptyList(), emptyList(), pack)
        assertEquals(listOf("owm"), fresh.addedProviders)
    }

    @Test fun anotherServiceUnderTheSameIdComesInRenamed() {
        // The user's "owm" has a key; a pack's "owm" pointing elsewhere must not get to use it.
        val mine = weather.copy(baseUrl = "https://minha.api.com")
        val pack = CommandPack(listOf(action), emptyList(), providers = listOf(weather))
        val merge = PackRules.merge(emptyList(), emptyList(), pack, providers = listOf(mine))
        assertEquals(listOf("owm-2"), merge.addedProviders)
        assertEquals("owm-2", merge.actions.single().provider)
        assertEquals("https://api.openweathermap.org", merge.providers.single { it.id == "owm-2" }.baseUrl)
        assertEquals(mine, merge.providers.single { it.id == "owm" })
        val preview = PackRules.preview(emptyList(), emptyList(), pack, listOf(mine))
        assertEquals(mapOf("owm" to "owm-2"), preview.renames)
        assertEquals("owm-2", preview.actions.single().item.provider)
        assertEquals("owm-2", preview.providers.single().item.id)
        // Another authentication on the same host is another service too.
        val keyInHeader = weather.copy(auth = AuthSpec.ApiKey("appid", KeyPlacement.HEADER))
        assertEquals(listOf("owm-2"), PackRules.merge(emptyList(), emptyList(), pack, providers = listOf(keyInHeader)).addedProviders)
    }

    @Test fun sendingTheLocationOrClipboardIsCalledOut() {
        val leaky = action.copy(letter = "x", template = "/track?l={location}&c={clip}&q={text}")
        val pack = CommandPack(listOf(action, leaky), emptyList(), providers = listOf(weather))
        val preview = PackRules.preview(emptyList(), emptyList(), pack)
        assertEquals(false, preview.sends[0]!!.isSensitive)
        val sends = preview.sends[1]!!
        assertTrue(sends.isSensitive)
        assertEquals("api.openweathermap.org", sends.host)
        assertEquals("o que você digitar · sua localização · o que você copiou", sends.label())
        assertTrue(preview.actions[1].caution!!.contains("sua localização"))
        // An action that opens an app sends nothing by itself.
        assertNull(PackRules.sends(CustomActions.WHATSAPP, emptyList()))
    }

        @Test fun onlyTheProvidersInUseAreShared() {
        val other = weather.copy(id = "other")
        assertEquals(listOf(weather), CommandPacks.providersFor(listOf(action), listOf(weather, other, Providers.OPEN_METEO)))
    }

    @Test fun actionsKeepTheirProvider() {
        assertEquals(listOf(action), CustomActionStore.decode(CustomActionStore.encode(listOf(action))))
        assertEquals(listOf(weather), ProviderCodec.fromJson(ProviderCodec.toJson(listOf(weather))))
    }

    // ---- the bar ----

    private fun run(text: String, configured: Set<String>, providers: List<ApiProvider> = Providers.all(listOf(weather))) =
        CommandEngine.analyze(text, emptyList(), emptyList(), true, listOf(action), emptyList(), CommandExtras(providers = providers, configuredProviders = configured))

    @Test fun aMissingKeyOffersToSetItUp() {
        val a = run("clima2 recife", emptySet())
        assertNull(a.action)
        assertEquals(Need.SECRET, a.need)
        assertEquals("owm", a.needProvider)
        val ok = run("clima2 recife", setOf("owm"))
        assertTrue(ok.action is CommandAction.Custom)
        // The line under the preview never shows the key.
        assertEquals("GET https://api.openweathermap.org/data/2.5/weather?q=recife&appid=•••", ok.intent)
    }

    @Test fun aMissingProviderSaysSo() {
        val a = run("clima2 recife", emptySet(), Providers.BUILT_IN)
        assertNull(a.action)
        assertEquals(Tone.ERROR, a.tone)
    }
}
