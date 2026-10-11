package app.lawnchair.command

import androidx.annotation.StringRes
import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents
import com.android.launcher3.R

/**
 * A screen of the system settings that "config" opens: "config bateria", "config wifi". Each is
 * named by the words of the app language ([wordsRes], the first one is shown) and the Portuguese
 * and English words always work. [action] is the settings intent action; [LAUNCHER] opens the
 * launcher's own settings.
 */
enum class SettingsPage(@StringRes private val wordsRes: Int, private val fixed: String, val action: String) {
    WIFI(R.string.cmd_cfg_wifi, "wifi,wi-fi,internet", "android.settings.WIFI_SETTINGS"),
    BLUETOOTH(R.string.cmd_cfg_bluetooth, "bluetooth,bt", "android.settings.BLUETOOTH_SETTINGS"),
    BATTERY(R.string.cmd_cfg_battery, "bateria,battery", "android.intent.action.POWER_USAGE_SUMMARY"),
    DATA(R.string.cmd_cfg_data, "dados,rede,data,network", "android.settings.DATA_USAGE_SETTINGS"),
    AIRPLANE(R.string.cmd_cfg_airplane, "aviao,airplane", "android.settings.AIRPLANE_MODE_SETTINGS"),
    APPS(R.string.cmd_cfg_apps, "apps,aplicativos,applications", "android.settings.APPLICATION_SETTINGS"),
    DEFAULT_APPS(R.string.cmd_cfg_default_apps, "padrao,default", "android.settings.MANAGE_DEFAULT_APPS_SETTINGS"),
    NOTIFICATIONS(R.string.cmd_cfg_notifications, "notificacoes,notifications", "android.settings.NOTIFICATION_SETTINGS"),
    DISPLAY(R.string.cmd_cfg_display, "tela,display,screen", "android.settings.DISPLAY_SETTINGS"),
    SOUND(R.string.cmd_cfg_sound, "som,sound", "android.settings.SOUND_SETTINGS"),
    STORAGE(R.string.cmd_cfg_storage, "armazenamento,storage", "android.settings.INTERNAL_STORAGE_SETTINGS"),
    LOCATION(R.string.cmd_cfg_location, "localizacao,location,gps", "android.settings.LOCATION_SOURCE_SETTINGS"),
    SECURITY(R.string.cmd_cfg_security, "seguranca,security", "android.settings.SECURITY_SETTINGS"),
    ACCESSIBILITY(R.string.cmd_cfg_accessibility, "acessibilidade,accessibility", "android.settings.ACCESSIBILITY_SETTINGS"),
    DATE(R.string.cmd_cfg_date, "data,hora,date,time", "android.settings.DATE_SETTINGS"),
    LANGUAGE(R.string.cmd_cfg_language, "idioma,language", "android.settings.LOCALE_SETTINGS"),
    DEVELOPER(R.string.cmd_cfg_developer, "desenvolvedor,developer,dev", "android.settings.APPLICATION_DEVELOPMENT_SETTINGS"),
    ABOUT(R.string.cmd_cfg_about, "sobre,about", "android.settings.DEVICE_INFO_SETTINGS"),
    LAUNCHER(R.string.cmd_cfg_launcher, "launcher,terminal", ""),
    ;

    /** The word shown and suggested: "bateria" in Portuguese. */
    val word: String get() = Texts.get(wordsRes).split(',').first().trim()

    fun words(): Set<String> = (Texts.get(wordsRes).split(',') + fixed.split(',')).map { it.trim().foldAccents() }.filter { it.isNotEmpty() }.toSet()

    companion object {
        fun of(word: String): SettingsPage? = word.trim().foldAccents().let { w -> entries.firstOrNull { w in it.words() } }

        fun matching(prefix: String): List<SettingsPage> {
            val p = prefix.trim().foldAccents()
            return entries.filter { page -> page.words().any { it.startsWith(p) } }
        }
    }
}
