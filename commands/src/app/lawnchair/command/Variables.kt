package app.lawnchair.command

import app.lawnchair.util.foldAccents
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Words in braces that a command line or a custom action is filled with when it runs: `{clip}`
 * is what was copied, `{sel}` the text selected in another app, `{date}` and `{time}` now,
 * `{location}` the coordinates and `{maps}` a map link to them. "w ana {maps}" sends where you
 * are. The Portuguese names work too. Pure, so it can be unit tested.
 */
object Variables {

    enum class Var { CLIP, SELECTION, DATE, TIME, LOCATION, MAPS }

    private val NAMES = mapOf(
        "clip" to Var.CLIP, "copiado" to Var.CLIP, "area" to Var.CLIP,
        "sel" to Var.SELECTION, "selection" to Var.SELECTION, "selecao" to Var.SELECTION,
        "date" to Var.DATE, "data" to Var.DATE, "hoje" to Var.DATE, "today" to Var.DATE,
        "time" to Var.TIME, "hora" to Var.TIME,
        "location" to Var.LOCATION, "local" to Var.LOCATION, "loc" to Var.LOCATION,
        "maps" to Var.MAPS, "mapa" to Var.MAPS,
    )

    private val pattern = Regex("\\{([^{}\\s]+)\\}")

    /** The text with every variable filled, and the ones that had no value (left as typed). */
    data class Expansion(val text: String, val missing: Set<Var>)

    fun expand(text: String, extras: CommandExtras): Expansion {
        if ('{' !in text) return Expansion(text, emptySet())
        val missing = mutableSetOf<Var>()
        val result = pattern.replace(text) { m ->
            val v = NAMES[m.groupValues[1].foldAccents()] ?: return@replace m.value
            val value = valueOf(v, extras)
            if (value == null) {
                missing += v
                m.value
            } else {
                value
            }
        }
        return Expansion(result, missing)
    }

    /** Every variable [text] uses. */
    fun used(text: String): Set<Var> = pattern.findAll(text).mapNotNull { NAMES[it.groupValues[1].foldAccents()] }.toSet()

    /** What says something about the user: where they are and what they copied or selected. */
    val SENSITIVE: Set<Var> = setOf(Var.CLIP, Var.SELECTION, Var.LOCATION, Var.MAPS)

    /** True when [text] uses [v]. */
    fun uses(text: String, v: Var): Boolean = pattern.findAll(text).any { NAMES[it.groupValues[1].foldAccents()] == v }

    private fun valueOf(v: Var, extras: CommandExtras): String? = when (v) {
        Var.CLIP -> extras.clipboard?.takeIf { it.isNotBlank() }
        Var.SELECTION -> extras.selection?.takeIf { it.isNotBlank() }
        Var.DATE -> extras.clock().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
        Var.TIME -> "%02d:%02d".format(extras.clock().hour, extras.clock().minute)
        Var.LOCATION -> extras.location
        Var.MAPS -> extras.location?.let { "https://maps.google.com/?q=$it" }
    }

    /** The names shown in help: "{clip} {sel} {date} {time} {location} {maps}". */
    const val HELP = "{clip} {sel} {date} {time} {location} {maps}"
}
