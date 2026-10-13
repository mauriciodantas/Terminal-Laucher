package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Timers, reminders, switches, conversions, chains, variables and the other newer commands. */
class MoreCommandsTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private val apps = listOf("Chrome", "Kindle", "Maps").map { AppEntry(it, "pkg/$it") }
    private val contacts = listOf(ContactEntry("Ana Souza", "111", "ana@x.com"), ContactEntry("Bruno Lima", "222"))
    private val now = LocalDateTime.of(2026, 10, 11, 10, 0)
    private val extras = CommandExtras(now = now, zone = ZoneOffset.UTC)
    private fun millis(t: LocalDateTime) = t.toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun run(text: String, x: CommandExtras = extras, aliases: List<CommandAlias> = emptyList()) =
        CommandEngine.analyze(text, apps, contacts, true, CustomActions.DEFAULTS, aliases, x)

    // ---- time parsing ----

    @Test fun durations() {
        assertEquals(600, TimeParse.duration("10m"))
        assertEquals(600, TimeParse.duration("10"))
        assertEquals(5400, TimeParse.duration("1h30"))
        assertEquals(5400, TimeParse.duration("1h 30m"))
        assertEquals(90, TimeParse.duration("90s"))
        assertEquals(3600, TimeParse.duration("1 hora"))
        assertEquals(125, TimeParse.duration("2min5s"))
        assertNull(TimeParse.duration("abc"))
        assertNull(TimeParse.duration("0"))
        assertNull(TimeParse.duration("30m1h"))
        assertNull(TimeParse.duration("10", bareMinutes = false))
    }

    @Test fun clockTimes() {
        assertEquals(LocalTime.of(18, 0), TimeParse.clock("18h"))
        assertEquals(LocalTime.of(18, 30), TimeParse.clock("18:30"))
        assertEquals(LocalTime.of(18, 30), TimeParse.clock("18h30"))
        assertEquals(LocalTime.of(6, 30), TimeParse.clock("0630"))
        assertEquals(LocalTime.of(6, 0), TimeParse.clock("6"))
        assertNull(TimeParse.clock("25h"))
        assertNull(TimeParse.clock("30m"))
    }

    @Test fun days() {
        val weekdays = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
        assertEquals(weekdays, TimeParse.days("seg-sex"))
        assertEquals(weekdays, TimeParse.days("seg a sex"))
        assertEquals(weekdays, TimeParse.days("dias úteis"))
        assertEquals(listOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY), TimeParse.days("seg, qua e sex"))
        assertEquals(listOf(Calendar.SATURDAY, Calendar.SUNDAY), TimeParse.days("fds"))
        assertEquals(listOf(Calendar.SATURDAY, Calendar.SUNDAY), TimeParse.days("sat-sun"))
        assertNull(TimeParse.days("comprar"))
    }

    // ---- timer, alarm and reminders ----

    @Test fun timer() {
        assertEquals(CommandAction.SetTimer(600), run("timer 10m").action)
        assertEquals(CommandAction.SetTimer(5400), run("timer 1h30").action)
        assertNull(run("timer").action)
        assertEquals(Tone.ERROR, run("timer xyz").tone)
    }

    @Test fun repeatingAlarm() {
        val weekdays = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
        assertEquals(CommandAction.SetAlarm(7, 30, weekdays), run("alarme 7h30 seg-sex").action)
        assertEquals(CommandAction.SetAlarm(7, 30), run("alarme 7:30").action)
        assertEquals(CommandAction.SetAlarm(6, 30), run("alarme 06 30").action)
    }

    @Test fun reminderAtAClockTime() {
        val a = run("lembrar 18h comprar pão").action as CommandAction.Schedule
        assertEquals(JobKind.REMIND, a.job.kind)
        assertEquals("comprar pão", a.job.text)
        assertEquals(millis(now.withHour(18)), a.job.at)
    }

    @Test fun reminderInAWhile() {
        val a = run("lembrar 30m tirar o bolo").action as CommandAction.Schedule
        assertEquals(millis(now.plusMinutes(30)), a.job.at)
        assertEquals("tirar o bolo", a.job.text)
        val b = run("lembrar em 2h ligar").action as CommandAction.Schedule
        assertEquals(millis(now.plusHours(2)), b.job.at)
    }

    @Test fun reminderTomorrowAndPastHours() {
        val a = run("lembrar amanhã 9h reunião").action as CommandAction.Schedule
        assertEquals(millis(now.plusDays(1).withHour(9)), a.job.at)
        // 8h already passed today: it is tomorrow's.
        val b = run("lembrar 8h café").action as CommandAction.Schedule
        assertEquals(millis(now.plusDays(1).withHour(8)), b.job.at)
        // Spoken: "18 horas" is a time, "1h 30m" a duration.
        val spoken = run("lembrar às 18 horas comprar pão").action as CommandAction.Schedule
        assertEquals(millis(now.withHour(18)), spoken.job.at)
        assertEquals("comprar pão", spoken.job.text)
        assertEquals(millis(now.plusMinutes(90)), (run("lembrar 1h 30m chá").action as CommandAction.Schedule).job.at)
        assertEquals(millis(now.withHour(13).withMinute(30)), (run("lembrar 13h30 chá").action as CommandAction.Schedule).job.at)
        assertNull(run("lembrar comprar pão").action)
        assertNull(run("lembrar 18h").action)
    }

    @Test fun scheduledCommand() {
        val a = run("em 30m: tema escuro")
        val job = (a.action as CommandAction.Schedule).job
        assertEquals(JobKind.COMMAND, job.kind)
        assertEquals("tema escuro", job.text)
        assertEquals(millis(now.plusMinutes(30)), job.at)
        val b = run("às 22:30: silencio liga").action as CommandAction.Schedule
        assertEquals(millis(now.withHour(22).withMinute(30)), b.job.at)
        assertEquals("silencio liga", b.job.text)
        // The command itself must be valid.
        assertNull(run("em 30m: xyzzy abc").action?.takeIf { it is CommandAction.Schedule })
    }

    @Test fun rules() {
        val a = run("quando carregar: wifi").action as CommandAction.Schedule
        assertEquals(JobKind.RULE, a.job.kind)
        assertEquals(TriggerEvent.CHARGING, a.job.event)
        assertEquals("wifi", a.job.text)
        assertEquals(Tone.ERROR, run("quando chover: wifi").tone)
    }

    @Test fun listAndCancelJobs() {
        val jobs = listOf(
            ScheduledJob(5, JobKind.RULE, "wifi", event = TriggerEvent.CHARGING),
            ScheduledJob(7, JobKind.REMIND, "pão", millis(now.withHour(18))),
        )
        val x = extras.copy(jobs = jobs)
        val list = run("agendados", x)
        assertEquals(listOf("cancelar 1", "cancelar 2"), list.suggestions.map { it.completion })
        assertTrue(list.suggestions[0].label.contains("18:00"))
        assertEquals(CommandAction.CancelJob(jobs[1]), run("cancelar 1", x).action)
        assertEquals(CommandAction.CancelJob(jobs[0]), run("cancelar 2", x).action)
        assertNull(run("cancelar 3", x).action)
    }

    @Test fun jobsSurviveEncoding() {
        val jobs = listOf(ScheduledJob(1, JobKind.RULE, "wifi", event = TriggerEvent.HEADPHONES), ScheduledJob(2, JobKind.COMMAND, "tocar", 99))
        assertEquals(jobs, Jobs.decode(Jobs.encode(jobs)))
        assertEquals(listOf(jobs[1]), Jobs.due(jobs, 100))
    }

    // ---- switches, media and settings ----

    @Test fun switches() {
        assertEquals(CommandAction.Toggle(SystemToggle.TORCH, null), run("lanterna").action)
        assertEquals(CommandAction.Toggle(SystemToggle.TORCH, true), run("lanterna liga").action)
        assertEquals(CommandAction.Toggle(SystemToggle.TORCH, false), run("flashlight off").action)
        assertEquals(CommandAction.Toggle(SystemToggle.DND, true), run("silêncio on").action)
        assertEquals(CommandAction.Toggle(SystemToggle.ROTATION, false), run("rotacao desliga").action)
        assertEquals(CommandAction.Toggle(SystemToggle.WIFI, null), run("wifi").action)
        assertEquals(CommandAction.Toggle(SystemToggle.BLUETOOTH, null), run("bt").action)
        assertEquals(Tone.ERROR, run("lanterna talvez").tone)
    }

    @Test fun brightnessAndVolume() {
        assertEquals(CommandAction.Brightness(40), run("brilho 40").action)
        assertEquals(CommandAction.Brightness(null), run("brilho auto").action)
        assertNull(run("brilho 140").action)
        assertEquals(CommandAction.Volume(30), run("vol 30").action)
        assertEquals(CommandAction.Volume(0), run("volume mudo").action)
    }

    @Test fun media() {
        assertEquals(CommandAction.Media(MediaKey.PLAY), run("tocar").action)
        assertEquals(CommandAction.Media(MediaKey.PAUSE), run("pause").action)
        assertEquals(CommandAction.Media(MediaKey.NEXT), run("próxima").action)
        assertEquals(CommandAction.Media(MediaKey.PREVIOUS), run("anterior").action)
    }

    @Test fun settingsPages() {
        assertEquals(CommandAction.OpenSettings(SettingsPage.BATTERY), run("config bateria").action)
        assertEquals(CommandAction.OpenSettings(SettingsPage.DISPLAY), run("config display").action)
        assertTrue(run("config").suggestions.size > 5)
        assertEquals(listOf("config notificações"), run("config noti").suggestions.map { it.completion })
    }

    // ---- calc ----

    @Test fun unitConversions() {
        assertEquals("3,10686", (run("c 5 km em mi").action as CommandAction.Calc).result)
        assertEquals("86", (run("c 30 c em f").action as CommandAction.Calc).result)
        assertEquals("2,20462", (run("calc 1 kg to lb").action as CommandAction.Calc).result)
        assertEquals("34,5", (run("c 15% de 230").action as CommandAction.Calc).result)
        assertEquals("1024", (run("c 1 gb em mb").action as CommandAction.Calc).result)
        assertNull(run("c 5 km em kg").action)
    }

    @Test fun currencies() {
        val rates = CurrencyRates("USD", mapOf("BRL" to 5.0, "EUR" to 0.9), 0)
        assertEquals("500", (run("c 100 usd em brl", extras.copy(rates = rates)).action as CommandAction.Calc).result)
        assertEquals("18", (run("c r$ 100 em eur", extras.copy(rates = rates)).action as CommandAction.Calc).result)
        val pending = run("c 100 usd em brl")
        assertNull(pending.action)
        assertEquals(Tone.WARN, pending.tone)
    }

    // ---- clipboard, notes, variables ----

    @Test fun copyAndPaste() {
        assertEquals(CommandAction.Copy("olá mundo"), run("copiar olá mundo").action)
        assertEquals(CommandAction.FillLine("rota casa"), run("colar", extras.copy(clipboard = "rota casa")).action)
        assertNull(run("colar").action)
    }

    @Test fun variablesFillTheLine() {
        val x = extras.copy(clipboard = "Av. Paulista 1000", location = "-23.5,-46.6", locationGranted = true)
        assertEquals(CommandAction.Route("Av. Paulista 1000"), run("rota {clip}", x).action)
        assertEquals(CommandAction.Custom(CustomActions.DEFAULTS.first { it.letter == "w" }, contacts[0], "estou em https://maps.google.com/?q=-23.5,-46.6"), run("w ana estou em {maps}", x).action)
        assertEquals(CommandAction.Copy("10:00"), run("copiar {hora}", x).action)
        val needs = run("w ana {location}")
        assertEquals(Need.LOCATION, needs.need)
        assertNull(needs.action)
        assertEquals(CommandAction.Route("texto"), run("rota {sel}", extras.copy(selection = "texto")).action)
    }

    @Test fun notes() {
        assertEquals(CommandAction.AddNote("ideia boa"), run("nota ideia boa").action)
        val notes = listOf(NoteEntry("b", 2), NoteEntry("a", 1))
        val a = run("nota", extras.copy(notes = notes))
        assertEquals(CommandAction.ShareNotes("• b\n• a"), a.action)
        assertEquals(listOf("copiar b", "copiar a"), a.suggestions.map { it.completion })
        assertEquals(listOf("x", "b", "a"), Notes.add(notes, "x", 3).map { it.text })
    }

    // ---- chains, history and repeat ----

    @Test fun chains() {
        val a = run("tema escuro; silencio liga; abrir kindle")
        val chain = a.action as CommandAction.Chain
        assertEquals(3, chain.steps.size)
        assertEquals(CommandAction.OpenApp(apps[1]), chain.steps[2])
        assertEquals(Tone.OK, a.tone)
        // While the last one is typed, its suggestions complete the whole line.
        val typing = run("tema escuro; abrir")
        assertNull(typing.action)
        assertEquals("tema escuro; abrir ", typing.suggestions.first().completion)
        assertNull(run("tema escuro; xyzzy abc").action)
    }

    @Test fun aliasesHoldChains() {
        val aliases = listOf(CommandAlias("noite", "tema escuro; silencio liga"))
        val a = run("noite", aliases = aliases).action as CommandAction.Chain
        assertEquals(2, a.steps.size)
        val nested = run("noite; abrir maps", aliases = aliases).action as CommandAction.Chain
        assertEquals(3, nested.steps.size)
    }

    @Test fun repeatRunsTheLastCommand() {
        val x = extras.copy(history = listOf("abrir chrome", "tocar"))
        assertEquals(CommandAction.OpenApp(apps[0]), run("!!", x).action)
        assertNull(run("!!").action)
    }

    @Test fun historyIsSuggested() {
        val x = extras.copy(history = listOf("abrir chrome", "alarme 0630"))
        assertTrue(run("ab", x).suggestions.any { it.completion == "abrir chrome" })
    }

    @Test fun usualCommandsComeFirst() {
        var stats = emptyMap<String, IntArray>()
        repeat(3) { stats = CommandStats.record(stats, "abrir kindle", 22) }
        repeat(5) { stats = CommandStats.record(stats, "abrir chrome", 8) }
        assertEquals(listOf("abrir chrome"), CommandStats.rank(stats, 8))
        assertEquals(listOf("abrir kindle"), CommandStats.rank(stats, 23))
        val decoded = CommandStats.decode(CommandStats.encode(stats))
        assertEquals(5, decoded["abrir chrome"]!![8])
        val chips = CommandEngine.quickChips(listOf("tocar"), emptyList(), emptyList(), CommandStats.rank(stats, 8))
        assertEquals(listOf("abrir chrome", "tocar"), chips.map { it.fill })
    }

    // ---- calendar, contacts, device ----

    @Test fun agenda() {
        assertEquals(Need.CALENDAR, run("agenda").need)
        val events = listOf(
            CalendarEntry("Dentista", millis(now.withHour(15)), millis(now.withHour(16)), false),
            CalendarEntry("Passado", millis(now.withHour(8)), millis(now.withHour(9)), false),
        )
        val a = run("agenda", extras.copy(events = events, calendarGranted = true))
        assertEquals("15:00  Dentista", a.preview)
        assertNotNull(a.action)
    }

    @Test fun contactInfo() {
        val a = run("ana?")
        assertEquals(CommandAction.Copy("111"), a.action)
        assertTrue(a.preview.contains("ana@x.com"))
    }

    @Test fun deviceInfo() {
        val info = SystemSnapshot(87, true, 31.0, 50L * 1024 * 1024 * 1024, 128L * 1024 * 1024 * 1024, listOf("192.168.0.10"), 90 * 60_000L)
        val x = extras.copy(info = info)
        assertTrue(run("bateria", x).preview.startsWith("87% · CARREGANDO · 31"))
        assertEquals("50 GB LIVRES DE 128 GB", run("armazenamento", x).preview)
        assertEquals("192.168.0.10", run("ip", x).preview)
        assertEquals("1h 30min", run("uptime", x).preview)
        assertEquals(CommandAction.Ping("8.8.8.8"), run("ping 8.8.8.8").action)
        assertNull(run("ping a b").action)
    }

    // ---- other languages ----

    @Test fun newCommandsFollowTheAppLanguage() {
        try {
            ResourceTexts.installLanguage(null)
            assertEquals(CommandAction.SetTimer(600), run("timer 10m").action)
            assertEquals(CommandAction.Toggle(SystemToggle.TORCH, true), run("flashlight on").action)
            assertEquals(CommandAction.OpenSettings(SettingsPage.BATTERY), run("settings battery").action)
            assertEquals(JobKind.COMMAND, (run("in 30m: theme dark").action as CommandAction.Schedule).job.kind)
            ResourceTexts.installLanguage("values-de")
            assertEquals(CommandAction.Toggle(SystemToggle.TORCH, true), run("taschenlampe an").action)
            assertEquals(CommandAction.Volume(30), run("lautstärke 30").action)
            assertEquals("Brot", (run("erinnern 18h Brot").action as CommandAction.Schedule).job.text)
            ResourceTexts.installLanguage("values-es")
            assertEquals(millis(now.withHour(22)), (run("a las 22h: silencio activar").action as CommandAction.Schedule).job.at)
            // Portuguese and English words keep working in any language.
            assertEquals(CommandAction.Toggle(SystemToggle.TORCH, null), run("lanterna").action)
        } finally {
            ResourceTexts.install()
        }
    }

    // ---- rede ----

    private val wifi = NetworkSnapshot(
        kind = NetKind.WIFI,
        internet = true,
        ssid = "Casa",
        signalDbm = -55,
        frequencyMhz = 5180,
        wifiStandard = 6,
        linkMbps = 866,
        addresses = listOf("192.168.0.10", "2804:14c::1"),
        gateway = "192.168.0.1",
        dns = listOf("192.168.0.1"),
        privateDns = "dns.google",
    )

    @Test fun networkShowsTheWifi() {
        val a = run("rede", extras.copy(network = wifi))
        assertEquals(Tone.OK, a.tone)
        assertEquals("REDE · WI-FI", a.previewTitle)
        assertEquals(
            "internet ok\nSSID Casa\nsinal -55 dBm · bom\n5 GHz · Wi-Fi 6 · 866 Mbps\n" +
                "IP 192.168.0.10 · 2804:14c::1\nGATEWAY 192.168.0.1\nDNS privado dns.google · 192.168.0.1",
            a.preview,
        )
        // Enter copies what is shown.
        assertEquals(CommandAction.Copy(a.preview), a.action)
        // The English word and the short one work too.
        assertEquals(a.preview, run("net", extras.copy(network = wifi)).preview)
    }

    @Test fun networkWithoutTheWifiNameOrInternet() {
        val text = run("rede", extras.copy(network = wifi.copy(ssid = null, internet = false, metered = true, vpn = true))).preview
        assertTrue(text.startsWith("sem internet · cobrada por uso · VPN\nSSID oculto"))
        assertEquals(Tone.WARN, run("rede", extras.copy(network = wifi.copy(internet = false))).tone)
    }

    @Test fun networkOnMobileDataAndOffline() {
        val cell = NetworkSnapshot(kind = NetKind.CELLULAR, internet = true, metered = true, carrier = "Vivo", downKbps = 45000, upKbps = 12000)
        val a = run("rede", extras.copy(network = cell))
        assertEquals("REDE · DADOS MÓVEIS", a.previewTitle)
        assertEquals("internet ok · cobrada por uso\noperadora Vivo\nestimativa ↓ 45 ↑ 12 Mbps", a.preview)
        assertEquals("SEM CONEXÃO", run("rede", extras.copy(network = NetworkSnapshot(NetKind.NONE))).preview)
        assertNull(run("rede", extras.copy(network = NetworkSnapshot(NetKind.NONE))).action)
        assertEquals("LENDO…", run("rede", extras.copy(network = null)).preview)
    }

    @Test fun wifiBandsAndSignal() {
        assertEquals("2.4 GHz", MoreCommands.band(2437))
        assertEquals("6 GHz", MoreCommands.band(6115))
        assertEquals("fraco", app.lawnchair.util.Texts.get(MoreCommands.signalQuality(-80)))
    }
}
