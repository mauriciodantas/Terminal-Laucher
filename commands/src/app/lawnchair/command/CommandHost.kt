package app.lawnchair.command

import org.json.JSONArray
import org.json.JSONObject

/**
 * What the app around the engine adds: the pieces that differ between the apps that share it (the
 * theme, the launcher's folders...). The engine knows nothing about them, and each app writes them
 * in its own `command-host/` folder, which is compiled into this module (same package, same `R`).
 *
 * The folder has to define, besides the code of its features:
 *  - `object AppHost : CommandHost`, this contract;
 *  - `data class HostData`, the app's own values in [CommandExtras.host] (all with defaults);
 *  - `data class HostAiData`, the app's own values in [AiContext.host] (all with defaults).
 *
 * Its actions extend [CommandAction] and its import targets extend [JsonImportTarget] (both are
 * sealed, and the folder shares their module and package).
 */
interface CommandHost {

    /** How the AI is told what it is part of: "the command bar of Terminal, an Android launcher". */
    val aiIntro: String

    /** Commands only this app has, listed after "dividir". */
    val commands: List<Command>

    /** Modes after "ia" only this app has ([AiMode.FOLDERS], [AiMode.THEME]). */
    val aiModes: List<AiMode>

    /** What the bar shows for one of [aiModes], or null when [mode] is not the app's. */
    fun aiAnalyze(mode: AiMode, prompt: String, heading: String, offers: List<Suggestion>): Analysis?

    /** The system prompt of one of [aiModes], or null when [mode] is not the app's. */
    fun aiSystem(mode: AiMode, context: AiContext): String?

    /** Added to the rules of "ia criar", for what the app can create besides commands; empty for nothing. */
    val aiCreateTail: String

    /** The app's own kinds of JSON a pasted text can be (a theme, a widget); null when [o] is none. */
    fun classifyObject(o: JSONObject, raw: String, format: String): JsonImportTarget?

    fun classifyArray(array: JSONArray, raw: String): JsonImportTarget?

    /** What the bar shows for one of the app's [JsonImportTarget]s. */
    fun importAnalysis(target: JsonImportTarget, arg: String): Analysis?

    /** The line "dry run" shows for one of the app's [CommandAction]s. */
    fun describe(action: CommandAction): String?
}
