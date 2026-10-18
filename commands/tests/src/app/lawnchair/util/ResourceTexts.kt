package app.lawnchair.util

import app.lawnchair.command.R
import java.io.File

/**
 * Installs a [Texts] lookup that reads the command strings straight from commands/res. [install]
 * uses Portuguese, values/strings_commands.xml with values-pt/strings_commands.xml on top, so the
 * rule objects are tested with the text the app shows on a Portuguese phone. [installLanguage]
 * picks another language folder, or none for the English defaults.
 */
object ResourceTexts {

    private val names: Map<Int, String> by lazy {
        R.string::class.java.fields.associate { it.getInt(null) to it.name }
    }

    private val defaults: Map<String, String> by lazy { readBoth("values") }

    fun install() = installLanguage("values-pt")

    /** [folder] is a resource folder such as "values-de"; null keeps the English defaults. */
    fun installLanguage(folder: String?) {
        val strings = defaults + (folder?.let { readBoth(it) } ?: emptyMap())
        Texts.install { id, args ->
            val name = names[id] ?: error("No string resource with id $id")
            val value = strings[name] ?: error("No text for string $name")
            if (args.isEmpty()) value else String.format(value, *args)
        }
    }

    private const val FILE = "strings_commands.xml"

    /** The strings of the app around the engine (see CommandHost) have their own file in command-host/res. */
    private const val HOST_FILE = "strings_host.xml"

    private fun readBoth(folder: String): Map<String, String> = read(File(resDir(), "$folder/$FILE")) +
        (hostResDir()?.let { read(File(it, "$folder/$HOST_FILE")) } ?: emptyMap())

    private fun hostResDir(): File? = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "command-host/res") }
        .firstOrNull { it.isDirectory }

    /** The commands/res folder, looked up from the working directory Gradle runs the tests in. */
    private fun resDir(): File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "commands/res") }
        .first { it.isDirectory }

    private val stringTag = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    private fun read(file: File): Map<String, String> = if (!file.exists()) {
        emptyMap()
    } else {
        stringTag.findAll(file.readText())
            .associate { it.groupValues[1] to unescape(it.groupValues[2]) }
    }

    private fun unescape(value: String): String = value
        .replace("\\\"", "\"")
        .replace("\\'", "'")
        .replace("\\n", "\n")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}
