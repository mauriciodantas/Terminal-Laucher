package app.lawnchair.command

/** One extra documented for an intent action in the catalog. */
data class CatalogExtra(
    val key: String,
    val type: ExtraType,
    val description: String,
    val required: Boolean = false,
)

/** An intent action recipe in the catalog, based on Android Common Intents. */
data class CatalogEntry(
    val action: String,
    val name: String,
    val target: String = "activity",
    val data: String? = null,
    val mime: String? = null,
    val extras: List<CatalogExtra> = emptyList(),
)

/**
 * Built-in catalog of common Android intents with their documented extras and types.
 * Pure Kotlin, testable in JVM.
 */
object IntentCatalog {

    val ENTRIES: List<CatalogEntry> = listOf(
        CatalogEntry(
            action = "android.intent.action.SET_ALARM",
            name = "Criar alarme",
            extras = listOf(
                CatalogExtra("android.intent.extra.alarm.HOUR", ExtraType.INT, "Hora (0-23)", required = true),
                CatalogExtra("android.intent.extra.alarm.MINUTES", ExtraType.INT, "Minuto (0-59)", required = true),
                CatalogExtra("android.intent.extra.alarm.MESSAGE", ExtraType.STRING, "Mensagem"),
                CatalogExtra("android.intent.extra.alarm.DAYS", ExtraType.STRING, "Dias repetidos"),
                CatalogExtra("android.intent.extra.alarm.SKIP_UI", ExtraType.BOOL, "Pular tela"),
                CatalogExtra("android.intent.extra.alarm.VIBRATE", ExtraType.BOOL, "Vibrar"),
            ),
        ),
        CatalogEntry(
            action = "android.intent.action.SET_TIMER",
            name = "Criar timer",
            extras = listOf(
                CatalogExtra("android.intent.extra.alarm.LENGTH", ExtraType.INT, "Duração em segundos", required = true),
                CatalogExtra("android.intent.extra.alarm.MESSAGE", ExtraType.STRING, "Mensagem"),
                CatalogExtra("android.intent.extra.alarm.SKIP_UI", ExtraType.BOOL, "Pular tela"),
            ),
        ),
        CatalogEntry(
            action = "android.intent.action.INSERT",
            name = "Novo evento no calendário",
            data = "content://com.android.calendar/events",
            extras = listOf(
                CatalogExtra("title", ExtraType.STRING, "Título do evento", required = true),
                CatalogExtra("beginTime", ExtraType.LONG, "Início em milissegundos"),
                CatalogExtra("endTime", ExtraType.LONG, "Fim em milissegundos"),
                CatalogExtra("description", ExtraType.STRING, "Descrição"),
                CatalogExtra("eventLocation", ExtraType.STRING, "Local"),
                CatalogExtra("allDay", ExtraType.BOOL, "Dia inteiro"),
            ),
        ),
        CatalogEntry(
            action = "android.intent.action.SEND",
            name = "Compartilhar texto",
            mime = "text/plain",
            extras = listOf(
                CatalogExtra("android.intent.extra.TEXT", ExtraType.STRING, "Texto a compartilhar", required = true),
                CatalogExtra("android.intent.extra.SUBJECT", ExtraType.STRING, "Assunto"),
            ),
        ),
        CatalogEntry(
            action = "android.intent.action.SENDTO",
            name = "Enviar mensagem ou email",
            data = "smsto:{phone}",
            extras = listOf(
                CatalogExtra("sms_body", ExtraType.STRING, "Corpo da mensagem"),
                CatalogExtra("android.intent.extra.SUBJECT", ExtraType.STRING, "Assunto do email"),
                CatalogExtra("android.intent.extra.TEXT", ExtraType.STRING, "Texto"),
            ),
        ),
        CatalogEntry(
            action = "android.intent.action.DIAL",
            name = "Discar telefone",
            data = "tel:{phone}",
        ),
        CatalogEntry(
            action = "android.intent.action.VIEW",
            name = "Abrir link ou mapa",
            data = "https://",
        ),
        CatalogEntry(
            action = "android.intent.action.WEB_SEARCH",
            name = "Pesquisa na web",
            extras = listOf(
                CatalogExtra("query", ExtraType.STRING, "Termo de busca", required = true),
            ),
        ),
        CatalogEntry(
            action = "android.media.action.MEDIA_PLAY_FROM_SEARCH",
            name = "Tocar música pesquisada",
            extras = listOf(
                CatalogExtra("query", ExtraType.STRING, "Nome da música ou artista", required = true),
            ),
        ),
        CatalogEntry(
            action = "android.settings.WIFI_SETTINGS",
            name = "Configurações: Wi-Fi",
        ),
        CatalogEntry(
            action = "android.settings.BLUETOOTH_SETTINGS",
            name = "Configurações: Bluetooth",
        ),
        CatalogEntry(
            action = "android.intent.action.POWER_USAGE_SUMMARY",
            name = "Configurações: Bateria",
        ),
    )

    fun find(action: String): CatalogEntry? = ENTRIES.firstOrNull { it.action == action }
}
