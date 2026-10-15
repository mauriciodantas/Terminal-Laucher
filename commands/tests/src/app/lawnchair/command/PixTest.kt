package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PixTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private val brazil get() = BundledPacks.of("brasil")!!
    private val pix get() = brazil.actions.single { it.letter == "pix" }
    private fun installed() = BundledPacks.install(CustomActions.DEFAULTS, emptyList(), emptyList(), brazil).actions
    private fun run(text: String, custom: List<CustomAction> = installed()) = CommandEngine.analyze(text, emptyList(), emptyList(), false, custom)

    @Test fun keysAreWrittenTheWayTheDirectoryStoresThem() {
        assertEquals(PixKey(PixKind.CPF, "52998224725", "529.982.247-25"), PixKey.of("529.982.247-25"))
        assertEquals(PixKind.CNPJ, PixKey.of("11.222.333/0001-81")?.kind)
        assertEquals("11222333000181", PixKey.of("11.222.333/0001-81")?.value)
        assertEquals("+5511999998888", PixKey.of("+55 (11) 99999-8888")?.value)
        assertEquals("+5511999998888", PixKey.of("11999998888")?.value)
        assertEquals("ana@x.com.br", PixKey.of("Ana@X.com.br")?.value)
        assertEquals(PixKind.RANDOM, PixKey.of("123E4567-E89B-12D3-A456-426614174000")?.kind)
        assertNull(PixKey.of("abrir chrome"))
        assertNull(PixKey.of("123"))
    }

    @Test fun theCodeIsAStaticQrWithoutAmount() {
        assertEquals(
            "00020126370014BR.GOV.BCB.PIX0115teste@teste.com5204000053039865802BR5901N6001C62070503***6304C072",
            PixCode.of("teste@teste.com"),
        )
        val code = PixCode.of("52998224725")
        // No field 54: the payer types the amount.
        assertFalse(code.contains("530398654"))
        assertEquals(PixCode.crc16(code.dropLast(4)), code.takeLast(4))
    }

    @Test fun crcMatchesTheReferenceValues() {
        // CRC-16/CCITT-FALSE of "123456789" is 29B1.
        assertEquals("29B1", PixCode.crc16("123456789"))
        // The example of the Central Bank's BR Code manual.
        val manual = "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-4266554400005204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***6304"
        assertEquals("1D3D", PixCode.crc16(manual))
    }

    @Test fun pixIsInTheBrazilPackNotBuiltIn() {
        assertFalse("pix" in BuiltInCommand.reserved())
        assertNull(CommandRegistry.of(CustomActions.DEFAULTS).named("pix"))
        assertEquals("android.intent.action.Pix", pix.intentAction)
        assertEquals("PIX_DATA", pix.textExtra)
        assertNull(pix.mimeType)
        assertNull(CommandPattern.validate(pix.pattern))
    }

    @Test fun theExtraCarriesTheCodeOfTheKey() {
        assertEquals(PixCode.of("teste@teste.com"), pix.extraFor("teste@teste.com"))
        assertNull(pix.extraFor("abc"))
        // An action without a value of its own sends the text as typed.
        assertEquals("oi", CustomActions.PROBES.first().extraFor("oi"))
    }

    @Test fun enterRunsThePixAction() {
        val a = run("pix teste@teste.com")
        assertEquals(Tone.OK, a.tone)
        assertEquals("teste@teste.com", (a.action as CommandAction.Custom).text)
        assertEquals(Tone.ERROR, run("pix abc").tone)
        assertNull(run("pix abc").action)
    }

    @Test fun keysTypedAloneOfferPix() {
        val ctx = CommandContext(emptyList(), emptyList(), false, installed(), emptyList(), CommandExtras())
        val registry = CommandRegistry.of(installed())
        assertTrue("pix" in registry.applicable("529.982.247-25", ctx).map { it.word })
        assertTrue("pix" in registry.applicable("123e4567-e89b-12d3-a456-426614174000", ctx).map { it.word })
        assertTrue("pix" in registry.applicable("teste@teste.com", ctx).map { it.word })
        // A CPF whose check digits do not add up is not a key.
        assertFalse("pix" in registry.applicable("529.982.247-26", ctx).map { it.word })
    }

    @Test fun anInstalledPackOffersWhatItGained() {
        val old = installed().filter { it.letter != "pix" }
        assertEquals(listOf("pix"), BundledPacks.missing(old, emptyList(), brazil))
        assertEquals(CommandAction.InstallPack("brasil"), run("pacote brasil", old).action)
        assertEquals(emptyList<String>(), BundledPacks.missing(installed(), emptyList(), brazil))
    }

    @Test fun theValueSurvivesTheStore() {
        assertEquals(pix, CustomActionStore.decode(CustomActionStore.encode(listOf(pix))).single())
    }
}
