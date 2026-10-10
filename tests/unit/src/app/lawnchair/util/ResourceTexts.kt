package app.lawnchair.util

import com.android.launcher3.R
import java.io.File

/**
 * Installs a [Texts] lookup that reads the strings straight from the resource files. [install]
 * uses Portuguese, values/strings.xml with values-pt/strings_terminal.xml on top, so the rule
 * objects are tested with the text the app shows on a Portuguese phone. [installLanguage] picks
 * another language folder, or none for the English defaults.
 */
object ResourceTexts {

    private val names: Map<Int, String> by lazy {
        R.string::class.java.fields.associate { it.getInt(null) to it.name }
    }

    private val defaults: Map<String, String> by lazy { read(File(resDir(), "values/strings.xml")) }

    fun install() = installLanguage("values-pt")

    /** [folder] is a resource folder such as "values-de"; null keeps the English defaults. */
    fun installLanguage(folder: String?) {
        val strings = defaults + (folder?.let { read(File(resDir(), "$it/strings_terminal.xml")) } ?: emptyMap())
        Texts.install { id, args ->
            val name = names[id] ?: error("No string resource with id $id")
            val value = strings[name] ?: error("No text for string $name")
            if (args.isEmpty()) value else String.format(value, *args)
        }
    }

    /** The lawnchair/res folder, looked up from the working directory Gradle runs the tests in. */
    private fun resDir(): File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "lawnchair/res") }
        .first { it.isDirectory }

    private val stringTag = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    private fun read(file: File): Map<String, String> = stringTag.findAll(file.readText())
        .associate { it.groupValues[1] to unescape(it.groupValues[2]) }

    private fun unescape(value: String): String = value
        .replace("\\\"", "\"")
        .replace("\\'", "'")
        .replace("\\n", "\n")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}
