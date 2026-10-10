package app.lawnchair.smartspace.glance

import app.lawnchair.util.ResourceTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class BluetoothBatteryTest {

    @Before
    fun installTexts() = ResourceTexts.install()

    private fun dev(name: String, percent: Int) = BluetoothDeviceBattery(name, percent)

    @Test
    fun summarize_isNullWithoutDevices() {
        assertNull(BluetoothBattery.summarize(emptyList()))
    }

    @Test
    fun summarize_oneDeviceShowsItsNameAndPercent() {
        val summary = BluetoothBattery.summarize(listOf(dev("Buds Pro", 80)))!!
        assertEquals("Buds Pro", summary.title)
        assertEquals("80%", summary.subtitle)
    }

    @Test
    fun summarize_theLowestBatteryLeadsAndOthersFollow() {
        val summary = BluetoothBattery.summarize(listOf(dev("Watch", 55), dev("Buds", 20), dev("Fone", 90)))!!
        assertEquals("Buds", summary.title)
        assertEquals("20% · Watch 55% · Fone 90%", summary.subtitle)
    }

    @Test
    fun usable_dropsInvalidReadingsBlankNamesAndDuplicates() {
        val list = BluetoothBattery.usable(
            listOf(dev("A", -1), dev("B", 101), dev(" ", 50), dev("C", 40), dev("C", 30)),
        )
        assertEquals(listOf("C"), list.map { it.name })
    }

    @Test
    fun usable_keepsZeroAndHundred() {
        assertEquals(listOf(0, 100), BluetoothBattery.usable(listOf(dev("A", 100), dev("B", 0))).map { it.percent })
    }

    @Test
    fun torchLabel_marksWhenTheTorchIsOn() {
        assertEquals("LANTERNA", GlanceShortcut.torchLabel(false))
        assertEquals("[LANTERNA]", GlanceShortcut.torchLabel(true))
    }

    @Test
    fun shortcuts_haveUniqueLabelsThatFitTheRow() {
        val labels = GlanceShortcut.values().map { it.label }
        assertEquals(labels.size, labels.toSet().size)
        assertEquals(true, labels.all { it.length <= 8 })
    }

    @Test
    fun looksLikeCalculator_matchesEnglishAndPortugueseLabels() {
        listOf("Calculator", "Calculadora", "Samsung Calculator", "calc").forEach {
            assertEquals(it, true, GlanceShortcut.looksLikeCalculator(it))
        }
        listOf("Calendar", "Camera", "Contacts").forEach {
            assertEquals(it, false, GlanceShortcut.looksLikeCalculator(it))
        }
    }

    @Test
    fun calculatorPackages_includeSamsungAndGoogle() {
        assertEquals(true, "com.sec.android.app.popupcalculator" in GlanceShortcut.CALCULATOR_PACKAGES)
        assertEquals(true, "com.google.android.calculator" in GlanceShortcut.CALCULATOR_PACKAGES)
    }
}
