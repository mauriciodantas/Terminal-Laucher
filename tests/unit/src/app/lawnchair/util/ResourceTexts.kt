package app.lawnchair.util

import app.lawnchair.command.R as CommandR
import com.android.launcher3.R
import java.io.File

/**
 * Installs a [Texts] lookup that reads the strings straight from the resource files. [install]
 * uses Portuguese, values/strings.xml with values-pt/strings_terminal.xml on top, so the rule
 * objects are tested with the text the app shows on a Portuguese phone. [installLanguage] picks
 * another language folder, or none for the English defaults. The command engine's strings live in
 * the :commands module (commands/res, strings_commands.xml) and are read alongside.
 */
object ResourceTexts {

    private val names: Map<Int, String> by lazy {
        (R.string::class.java.fields + CommandR.string::class.java.fields).associate { it.getInt(null) to it.name }
    }

    private val defaults: Map<String, String> by lazy {
        read(File(resDir(LAUNCHER_RES), "values/strings.xml")) + read(File(resDir(COMMANDS_RES), "values/strings_commands.xml"))
    }

    fun install() = installLanguage("values-pt")

    /** [folder] is a resource folder such as "values-de"; null keeps the English defaults. */
    fun installLanguage(folder: String?) {
        val strings = defaults + (
            folder?.let {
                read(File(resDir(LAUNCHER_RES), "$it/strings_terminal.xml")) + read(File(resDir(COMMANDS_RES), "$it/strings_commands.xml"))
            } ?: emptyMap()
            )
        Texts.install { id, args ->
            val name = names[id] ?: error("No string resource with id $id")
            val value = strings[name] ?: error("No text for string $name")
            if (args.isEmpty()) value else String.format(value, *args)
        }
    }

    private const val LAUNCHER_RES = "lawnchair/res"
    private const val COMMANDS_RES = "commands/res"

    /** A res folder such as lawnchair/res, looked up from the working directory Gradle runs the tests in. */
    private fun resDir(path: String): File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, path) }
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
