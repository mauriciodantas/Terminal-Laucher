package app.lawnchair.command

import org.junit.Assert.assertEquals
import app.lawnchair.util.ResourceTexts
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ThemeShapeTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private fun run(text: String) = CommandEngine.analyze(text, emptyList(), emptyList(), true, emptyList(), emptyList(), CommandExtras())

    @Test fun commandSetsTheShapes() {
        assertEquals(ThemeAction.Apply(ThemeSpec(iconShape = "hexagon")), run("tema shape hexagon").action)
        assertEquals(ThemeAction.Apply(ThemeSpec(folderShape = "squircle")), run("tema foldershape Squircle").action)
        assertNull(run("tema shape blob").action)
    }

    @Test fun shapesAreReadInAnyCaseAndUnknownOnesDropped() {
        val spec = ThemeSpecs.fromJson(org.json.JSONObject("""{"iconShape":"Rounded-Hexagon","folderShape":"blob"}"""))
        assertEquals("roundedhexagon", spec.iconShape)
        assertNull(spec.folderShape)
    }

    @Test fun shapesSurviveTheFileAndOverlay() {
        val spec = ThemeSpec(name = "x", iconShape = "squircle", folderShape = "circle")
        assertEquals(spec, ThemeSpecs.decode(ThemeSpecs.encode(spec)))
        assertEquals("hexagon", ThemeSpec(iconShape = "circle").overlaidWith(ThemeSpec(iconShape = "hexagon")).iconShape)
        assertEquals("circle", ThemeSpec(iconShape = "circle").overlaidWith(ThemeSpec()).iconShape)
        assertTrue(ThemeSpec().copy(iconShape = "circle").isEmpty.not())
    }

    @Test fun promptNamesTheShapes() {
        assertTrue(LauncherAiPrompts.THEME_RULES.contains("iconShape"))
        assertTrue(LauncherAiPrompts.THEME_RULES.contains("squircle"))
    }
}
