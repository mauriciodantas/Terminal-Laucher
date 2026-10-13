package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommandRegistryTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private val ctx = CommandContext(emptyList(), emptyList(), false, CustomActions.DEFAULTS, emptyList(), CommandExtras())
    private val registry = CommandRegistry.of(CustomActions.DEFAULTS)

    @Test fun aWordNamesItsCommand() {
        assertSame(CalcCommand, registry.named("calc"))
        assertSame(CalcCommand, registry.named("c"))
        assertSame(CallCommand, registry.named("ligar"))
        assertSame(CallCommand, registry.named("call"))
        assertSame(TaskCommand, registry.named("t"))
        assertEquals("w", registry.named("w")?.word)
        assertTrue(registry.named("lanterna") is LegacyCommand)
        assertNull(registry.named("nada"))
        // A command with no word is only reached by the text it recognizes.
        assertNull(registry.named(""))
    }

    @Test fun commandsThatNeedAnArgumentSaySo() {
        assertTrue(CalcCommand.requiresArgument)
        assertTrue(registry.named("abrir")!!.requiresArgument)
        assertTrue(registry.named("w")!!.requiresArgument)
        assertFalse(registry.named("lanterna")!!.requiresArgument)
        assertFalse(registry.named("tema")!!.requiresArgument)
    }

    @Test fun textTypedAloneFindsTheCommandsThatTakeIt() {
        assertEquals(listOf(UrlCommand), registry.applicable("github.com", ctx))
        assertEquals(listOf(CalcCommand), registry.applicable("12*3", ctx))
        assertEquals(listOf("ligar", "w"), registry.applicable("99999-9999", ctx).map { it.word })
        // The sum comes after the ways to reach the number.
        assertEquals(listOf("ligar", "w", "calc"), registry.applicable("2000-1500", ctx).map { it.word })
        assertEquals(emptyList<Command>(), registry.applicable("abrir camera", ctx))
    }

    @Test fun eachOptionTypesTheWholeCommand() {
        assertEquals("ligar 99999-9999", CallCommand.option(" 99999-9999 ", ctx).completion)
        assertEquals("= 500", CalcCommand.option("2000-1500", ctx).label)
        assertEquals("github.com", UrlCommand.option("github.com", ctx).completion)
    }
}
