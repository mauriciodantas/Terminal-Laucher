package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** API actions, the built-in packs and the everyday commands: qr, perto, clima, liberar, desinstalar, atualizar. */
class PackAndApiTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private val apps = listOf(AppEntry("WhatsApp", "com.whatsapp/.Main"), AppEntry("Chrome", "com.android.chrome/.Main"))
    private val today = LocalDate.of(2026, 10, 13)
    private val pt = Locale.forLanguageTag("pt-BR")

    private fun run(text: String, custom: List<CustomAction> = CustomActions.DEFAULTS, extras: CommandExtras = CommandExtras(providers = providers)) =
        CommandEngine.analyze(text, apps, emptyList(), true, custom, emptyList(), extras)

    private val brazil get() = BundledPacks.of("brasil")!!
    private fun installed() = BundledPacks.install(CustomActions.DEFAULTS, emptyList(), emptyList(), brazil).actions
    private val providers get() = Providers.all(brazil.providers)
    private fun action(letter: String) = brazil.actions.single { it.letter == letter }

    // ---- API answers ----

    private val viaCep = """{"cep":"01310-100","logradouro":"Avenida Paulista","complemento":"de 612 a 1510 - lado par",
        "bairro":"Bela Vista","localidade":"São Paulo","uf":"SP","ddd":"11"}"""

    @Test fun cepAnswerIsShownLineByLine() {
        val text = ApiFormat.render(viaCep, action("cep").api!!, today, pt)
        assertEquals("Avenida Paulista · de 612 a 1510 - lado par\nBela Vista · São Paulo/SP\nDDD 11", text)
    }

    @Test fun emptyFieldsLeaveOutTheirPartAndLine() {
        val text = ApiFormat.render("""{"logradouro":"","bairro":"","localidade":"Brasília","uf":"DF","ddd":"61"}""", action("cep").api!!, today, pt)
        assertEquals("Brasília/DF\nDDD 61", text)
    }

    @Test fun anAnswerWithNothingToShowIsNull() {
        assertNull(ApiFormat.render("""{"erro": "true"}""", action("cep").api!!, today, pt))
        assertNull(ApiFormat.render("<html>", action("cep").api!!, today, pt))
    }

    @Test fun holidaysBeforeTodayAreLeftOut() {
        val body = """[{"date":"2026-01-01","name":"Confraternização mundial"},{"date":"2026-10-12","name":"Nossa Senhora Aparecida"},
            {"date":"2026-11-02","name":"Finados"},{"date":"2026-11-15","name":"Proclamação da República"}]"""
        val text = ApiFormat.render(body, action("feriados").api!!, today, pt)!!
        assertEquals(2, text.lines().size)
        assertTrue(text, text.startsWith("02/11/2026 seg · Finados"))
        assertTrue(text.endsWith("Proclamação da República"))
    }

    @Test fun pathsReachIntoObjectsAndArrays() {
        val body = """{"results":[{"address":{"city":"Recife"},"n":2.0}]}"""
        assertEquals("Recife · 2", ApiFormat.render(body, ApiSpec("{address.city} · {n}", list = "results"), today, pt))
        assertEquals("Recife", ApiFormat.renderItem(org.json.JSONObject(body), "{results.0.address.city}"))
    }

    @Test fun addressFillsTextAndYear() {
        assertEquals("https://viacep.com.br/ws/01310-100/json/", ApiCall.url(action("cep"), " 01310-100 ", today, providers))
        assertEquals("https://brasilapi.com.br/api/feriados/v1/2026", ApiCall.url(action("feriados"), "", today, providers))
        assertEquals("viacep.com.br", ApiCall.host(action("cep"), providers))
        // An action saved before providers existed still runs on its own address.
        val legacy = action("cep").copy(provider = "", template = "https://viacep.com.br/ws/{text}/json/")
        assertEquals("https://viacep.com.br/ws/1/json/", ApiCall.url(legacy, "1", today))
    }

    @Test fun onlyHttpsApiActionsAreImported() {
        val plain = action("cep").copy(provider = "", template = "http://example.com/{text}")
        assertTrue(PackRules.isBlocked(plain))
        assertTrue(PackRules.isBlocked(action("cep").copy(api = null)))
        assertFalse(PackRules.isBlocked(action("cep")))
        assertNotNull(PackRules.caution(action("cep")))
    }

    @Test fun apiActionsSurviveTheStore() {
        val back = CustomActionStore.decode(CustomActionStore.encode(brazil.actions))
        assertEquals(brazil.actions, back)
    }

    // ---- packs ----

    @Test fun packAloneListsThePacks() {
        val a = run("pacote")
        assertNull(a.action)
        assertEquals("pacote brasil", a.suggestions.single().completion)
    }

    @Test fun packInstallsAndRemoves() {
        val a = run("pacote brasil")
        assertEquals(CommandAction.InstallPack("brasil"), a.action)
        val custom = installed()
        assertEquals(listOf("w", "rastrear", "cep", "feriados"), custom.map { it.letter })
        assertNull(run("pacote brasil", custom).action)
        assertEquals(CommandAction.RemovePack("brasil"), run("pacote brasil remover", custom).action)
        assertEquals(CustomActions.DEFAULTS, BundledPacks.remove(custom, "brasil"))
    }

    @Test fun installingKeepsTheUsersOwnLetter() {
        val mine = CustomActions.DEFAULTS + CustomAction("cep", "Meu CEP", ActionKind.INTENT, "https://x.com/{text}", emptyList(), ArgKind.TEXT)
        assertEquals(listOf("cep"), BundledPacks.conflicts(mine, emptyList(), brazil))
        val result = BundledPacks.install(mine, emptyList(), emptyList(), brazil)
        assertEquals(1, result.skipped)
        assertEquals("Meu CEP", result.actions.single { it.letter == "cep" }.label)
    }

    @Test fun installingTwiceDoesNotDuplicate() {
        val once = BundledPacks.install(CustomActions.DEFAULTS, emptyList(), emptyList(), brazil)
        val twice = BundledPacks.install(once.actions, emptyList(), once.providers, brazil)
        assertEquals(once, twice)
        assertEquals(listOf("viacep", "brasilapi"), once.providers.map { it.id })
        val removed = BundledPacks.remove(once.actions, once.providers, "brasil")
        assertEquals(CustomActions.DEFAULTS, removed.actions)
        assertTrue(removed.providers.isEmpty())
    }

    @Test fun packCommandsAnswerTextTypedAlone() {
        val custom = installed()
        // It is also a subtraction: the options list the CEP first, and Enter picks it.
        val options = run("01310-100", custom)
        assertTrue(options.picksOnEnter)
        assertEquals(listOf("cep 01310-100", "calc 01310-100"), options.suggestions.map { it.completion })
        val cep = run("cep 01310-100", custom).action as CommandAction.Custom
        assertEquals("01310-100", cep.text)
        val track = run("QB123456789BR", custom).action as CommandAction.Custom
        assertEquals("rastrear", track.action.letter)
        assertTrue(run("feriados", custom).action is CommandAction.Custom)
        // A CEP is not a phone number.
        assertNull(PhoneNumber.of("01310-100"))
    }

    // ---- everyday commands ----

    @Test fun qrDrawsTextAndWifi() {
        assertEquals(CommandAction.ShowQr("https://x.com"), run("qr https://x.com").action)
        assertEquals("WIFI:T:WPA;S:casa;P:senha123;;" to "casa", QrCommand.wifi("wifi casa senha123"))
        assertEquals("WIFI:T:WPA;S:Casa da Ana;P:s;;" to "Casa da Ana", QrCommand.wifi("wifi \"Casa da Ana\" s"))
        assertEquals("WIFI:T:nopass;S:visitas;;", QrCommand.wifi("wifi visitas")!!.first)
        assertEquals("WIFI:T:WPA;S:a\\;b;P:p\\:q;;", QrCommand.wifi("wifi a;b p:q")!!.first)
        assertNull(QrCommand.wifi("wifiano x"))
        assertNull(run("qr").action)
        val code = QrCode.encode("https://x.com")!!
        assertTrue(code.size >= 21)
    }

    @Test fun nearbyOffersPlacesAndOpensMaps() {
        val empty = run("perto")
        assertNull(empty.action)
        assertTrue(empty.suggestions.any { it.completion == "perto farmácia" })
        assertEquals(CommandAction.Nearby("farmacia"), run("perto farmacia").action)
    }

    @Test fun weatherUsesTheCityOrWhereYouAre() {
        assertEquals(CommandAction.Weather("recife", null), run("clima recife").action)
        assertEquals(CommandAction.Weather("recife", null), run("tempo recife").action)
        val here = CommandExtras(location = "-8.05,-34.88", locationGranted = true)
        assertEquals(CommandAction.Weather("", "-8.05,-34.88"), run("clima", extras = here).action)
        assertEquals(Need.LOCATION, run("clima").need)
    }

    @Test fun weatherAnswerReads() {
        val body = """{"current":{"temperature_2m":27.8,"apparent_temperature":29.9,"relative_humidity_2m":63,"weather_code":3,"wind_speed_10m":12.3},
            "daily":{"time":["2026-10-13","2026-10-14","2026-10-15"],"weather_code":[51,0,95],"temperature_2m_max":[29.4,29.3,29.8],
            "temperature_2m_min":[25.0,25.3,25.8],"precipitation_probability_max":[47,8,4]}}"""
        val text = Weather.text(body, "Recife, Pernambuco", pt)!!.lines()
        assertEquals("RECIFE, PERNAMBUCO", text[0])
        assertEquals("agora 28° · sensação 30° · nublado", text[1])
        assertEquals("umidade 63% · vento 12 km/h", text[2])
        assertEquals("hoje 25°–29° · garoa · chuva 47%", text[3])
        assertEquals("amanhã 25°–29° · céu limpo · chuva 8%", text[4])
        assertTrue(text[5], text[5].startsWith("qui 26°–30° · trovoada"))
        assertEquals("Recife, Pernambuco", Weather.place("""{"results":[{"name":"Recife","admin1":"Pernambuco","latitude":-8.05,"longitude":-34.88}]}""")?.name)
        assertNull(Weather.place("""{"generationtime_ms":0.1}"""))
    }

    @Test fun freeSpaceOpensTheSystemCleanup() {
        assertEquals(CommandAction.OpenSettings(SettingsPage.FREE_SPACE), run("liberar espaço").action)
        assertEquals(CommandAction.OpenSettings(SettingsPage.FREE_SPACE), run("liberar").action)
    }

    @Test fun uninstallAndUpdatePickTheApp() {
        assertEquals(CommandAction.Uninstall(apps[0]), run("desinstalar whats").action)
        assertNull(run("desinstalar").action)
        assertEquals(CommandAction.UpdateApp(apps[1]), run("atualizar chrome").action)
        assertEquals(CommandAction.UpdateApp(null), run("atualizar").action)
        assertNull(run("atualizar xyz").action)
    }
}
