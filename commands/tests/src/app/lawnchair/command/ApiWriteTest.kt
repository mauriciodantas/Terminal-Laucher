package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import java.time.LocalDate
import java.util.Locale
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** API actions that change data: POST, PUT, PATCH and DELETE. */
class ApiWriteTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private val today = LocalDate.of(2026, 10, 13)
    private val todo = ApiProvider(id = "todo", name = "Todo", baseUrl = "https://api.todo.com", auth = AuthSpec.Bearer)

    private fun action(method: String, body: String = "", type: BodyType = BodyType.JSON, format: String = "", template: String = "/tasks") = CustomAction(
        "tarefa", "Tarefa", ActionKind.API, template, emptyList(), ArgKind.TEXT,
        api = ApiSpec(format, method = method, body = body, bodyType = type), provider = "todo",
    )

    private fun run(text: String, a: CustomAction, extras: CommandExtras = CommandExtras(providers = Providers.all(listOf(todo)), configuredProviders = setOf("todo"))) =
        CommandEngine.analyze(text, emptyList(), emptyList(), true, listOf(a), emptyList(), extras)

    @Test fun typedTextCannotBreakOutOfTheJson() {
        val a = action(HttpMethods.POST, """{"title": "{text}", "done": false}""")
        val body = ApiCall.body(a, """comprar "pão", depois\fim""", today)!!
        val json = JSONObject(body)
        assertEquals("""comprar "pão", depois\fim""", json.getString("title"))
        assertEquals(false, json.getBoolean("done"))
        // A typed "injection" stays a string.
        val sneaky = JSONObject(ApiCall.body(a, """x", "admin": true, "y": "z""", today)!!)
        assertFalse(sneaky.has("admin"))
    }

    @Test fun formAndTextBodies() {
        assertEquals("q=a+b%26c&y=2026", ApiCall.body(action(HttpMethods.POST, "q={text}&y={year}", BodyType.FORM), "a b&c", today))
        assertEquals("nota: oi \"x\"", ApiCall.body(action(HttpMethods.PUT, "nota: {text}", BodyType.TEXT), "oi \"x\"", today))
        // A GET sends no body, whatever is saved.
        assertNull(ApiCall.body(action(HttpMethods.GET, "{text}"), "x", today))
    }

    @Test fun aWriteWithNothingToShowSaysItWentThrough() {
        val spec = ApiSpec("", method = HttpMethods.DELETE)
        assertEquals("DELETE OK · HTTP 204", ApiCall.outcome(spec, 204, "", today))
        assertEquals("POST OK · HTTP 201", ApiCall.outcome(spec.copy(method = HttpMethods.POST), 201, """{"id": 7}""", today))
        assertEquals("criada #7", ApiCall.outcome(ApiSpec("criada #{id}", method = HttpMethods.POST), 201, """{"id": 7}""", today, Locale.ROOT))
        assertEquals(Texts(R.string.cmd_api_empty), ApiCall.outcome(ApiSpec("{x}"), 200, "{}", today))
        // A GraphQL query goes over POST: an empty result is "nothing found", not "POST OK".
        val graphql = ApiSpec("#{id} {name}", list = "data.pokemon", method = HttpMethods.POST, body = "{}")
        assertEquals(Texts(R.string.cmd_api_empty), ApiCall.outcome(graphql, 200, """{"data":{"pokemon":[]}}""", today))
        assertEquals("#25 pikachu", ApiCall.outcome(graphql, 200, """{"data":{"pokemon":[{"id":25,"name":"pikachu"}]}}""", today))
    }

    private fun Texts(id: Int) = app.lawnchair.util.Texts.get(id)

    @Test fun writesNeedNoFormatButGetsDo() {
        assertTrue(ApiCall.isUsable(action(HttpMethods.POST, "{}")))
        assertFalse(ApiCall.isUsable(action(HttpMethods.GET)))
        assertFalse(ApiCall.isUsable(action("TRACE")))
        assertTrue(PackRules.isBlocked(action("CONNECT")))
    }

    @Test fun theBarWarnsAndDeleteAsksTwice() {
        val post = run("tarefa comprar pão", action(HttpMethods.POST, """{"t":"{text}"}"""))
        assertEquals(Tone.WARN, post.tone)
        assertFalse(post.confirmFirst)
        assertTrue(post.preview.contains("POST"))
        assertEquals("POST https://api.todo.com/tasks · json", post.intent)
        val delete = run("tarefa 42", action(HttpMethods.DELETE, template = "/tasks/{text}"))
        assertTrue(delete.confirmFirst)
        assertEquals("DELETE https://api.todo.com/tasks/42", delete.intent)
        assertFalse(run("tarefa x", action(HttpMethods.GET, format = "{t}")).confirmFirst)
    }

    @Test fun variablesInTheBodyAreEscapedAndCalledOut() {
        val leaky = action(HttpMethods.POST, """{"onde": "{location}", "t": "{text}"}""")
        val x = CommandExtras(providers = Providers.all(listOf(todo)), configuredProviders = setOf("todo"), location = "-8.05,-34.88", locationGranted = true)
        val custom = run("tarefa oi", leaky, x).action as CommandAction.Custom
        assertEquals("-8.05,-34.88", JSONObject(ApiCall.body(custom.action, custom.text, today)!!).getString("onde"))
        val sends = PackRules.sends(leaky, listOf(todo))!!
        assertTrue(sends.isSensitive)
        assertTrue(sends.writes)
    }

    @Test fun importNeverTicksAWrite() {
        val pack = CommandPack(listOf(action(HttpMethods.POST, "{}")), emptyList(), providers = listOf(todo))
        val preview = PackRules.preview(emptyList(), emptyList(), pack)
        assertTrue(preview.sends.single()!!.needsTrust)
        assertTrue(preview.actions.single().caution!!.contains("POST"))
    }

    @Test fun methodAndBodySurviveTheStore() {
        val a = action(HttpMethods.PATCH, """{"t":"{text}"}""", BodyType.FORM, "ok")
        assertEquals(listOf(a), CustomActionStore.decode(CustomActionStore.encode(listOf(a))))
        // Actions saved before methods existed read as GET.
        val old = CustomActionStore.encode(listOf(action(HttpMethods.GET, format = "{x}"))).replace(""","method":"GET"""", "")
        assertEquals(HttpMethods.GET, CustomActionStore.decode(old).single().api!!.method)
    }
}
