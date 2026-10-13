package app.lawnchair.command

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persists the user's [CustomAction]s next to the command history. */
object CustomActionStore {

    private const val STORE = "nostromo"
    private const val KEY = "custom_actions"

    /** The saved list, or the defaults (the WhatsApp preset) until the user changes anything. */
    fun load(context: Context): List<CustomAction> {
        val raw = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return CustomActions.DEFAULTS
        return runCatching { decode(raw) }.getOrDefault(CustomActions.DEFAULTS)
    }

    fun save(context: Context, actions: List<CustomAction>) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
            .putString(KEY, encode(actions)).apply()
    }

    fun encode(actions: List<CustomAction>): String = toJson(actions).toString()

    fun toJson(actions: List<CustomAction>): JSONArray = JSONArray().also { array ->
        actions.forEach {
            array.put(
                JSONObject()
                    .put("letter", it.letter)
                    .put("label", it.label)
                    .put("kind", it.kind.name)
                    .put("template", it.template)
                    .put("packages", JSONArray(it.packages))
                    .put("arg", it.arg.name)
                    .put("sms", it.smsFallback)
                    .put("action", it.intentAction)
                    .put("mime", it.mimeType ?: JSONObject.NULL)
                    .put("extra", it.textExtra ?: JSONObject.NULL)
                    .put("pattern", it.pattern)
                    .put("api", it.api?.let { api -> apiJson(api) } ?: JSONObject.NULL)
                    .put("pack", it.pack)
                    .put("provider", it.provider),
            )
        }
    }

    private fun apiJson(api: ApiSpec): JSONObject = JSONObject()
        .put("format", api.format)
        .put("list", api.list)
        .put("max", api.max)
        .put("upcoming", api.upcoming)

    fun decode(raw: String): List<CustomAction> = fromJson(JSONArray(raw))

    fun fromJson(array: JSONArray): List<CustomAction> {
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                val packages = o.getJSONArray("packages")
                CustomAction(
                    letter = o.getString("letter"),
                    label = o.getString("label"),
                    kind = ActionKind.valueOf(o.getString("kind")),
                    template = o.getString("template"),
                    packages = (0 until packages.length()).map { packages.getString(it) },
                    arg = ArgKind.valueOf(o.getString("arg")),
                    smsFallback = o.optBoolean("sms", false),
                    intentAction = o.optString("action", ACTION_VIEW),
                    mimeType = if (o.isNull("mime")) null else o.getString("mime"),
                    textExtra = if (o.isNull("extra")) null else o.getString("extra"),
                    pattern = o.optString("pattern", ""),
                    api = o.optJSONObject("api")?.let { api ->
                        ApiSpec(
                            format = api.getString("format"),
                            list = api.optString("list", ""),
                            max = api.optInt("max", 5),
                            upcoming = api.optString("upcoming", ""),
                        )
                    },
                    pack = o.optString("pack", ""),
                    provider = o.optString("provider", ""),
                )
            }.getOrNull()
        }
    }
}
