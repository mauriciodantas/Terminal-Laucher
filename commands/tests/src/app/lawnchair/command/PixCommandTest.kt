package app.lawnchair.command

import app.lawnchair.util.ResourceTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PixCommandTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private fun run(text: String) = CommandEngine.analyze(text, emptyList(), emptyList(), false)

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

    @Test fun enterHandsThePixToTheBank() {
        val a = run("pix teste@teste.com")
        assertEquals(Tone.OK, a.tone)
        assertEquals(CommandAction.Pix(PixCode.of("teste@teste.com")), a.action)
        assertEquals(Tone.ERROR, run("pix abc").tone)
    }

    @Test fun documentsTypedAloneOfferPix() {
        val ctx = CommandContext(emptyList(), emptyList(), false, emptyList(), emptyList(), CommandExtras())
        assertTrue(PixCommand.isApplicable("529.982.247-25", ctx))
        assertTrue(PixCommand.isApplicable("123e4567-e89b-12d3-a456-426614174000", ctx))
        assertFalse(PixCommand.isApplicable("ana@x.com", ctx))
        assertFalse(PixCommand.isApplicable("99999-9999", ctx))
    }
}
