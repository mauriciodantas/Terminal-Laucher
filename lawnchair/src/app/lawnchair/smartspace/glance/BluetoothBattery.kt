package app.lawnchair.smartspace.glance

/** A connected Bluetooth device and its battery, in percent. */
data class BluetoothDeviceBattery(val name: String, val percent: Int)

/** Pure rules of the Bluetooth battery target, free of Android types so they can be unit tested. */
object BluetoothBattery {

    /** Title and subtitle of the target. The subtitle starts with the percent of the title device. */
    data class Summary(val title: String, val subtitle: String)

    /** Valid readings only, lowest battery first, so the device that needs charging leads. */
    fun usable(devices: List<BluetoothDeviceBattery>): List<BluetoothDeviceBattery> =
        devices
            .filter { it.percent in 0..100 && it.name.isNotBlank() }
            .distinctBy { it.name }
            .sortedWith(compareBy({ it.percent }, { it.name }))

    /** The summary, or null when no connected device reports a battery. */
    fun summarize(devices: List<BluetoothDeviceBattery>): Summary? {
        val list = usable(devices)
        val first = list.firstOrNull() ?: return null
        val others = list.drop(1).joinToString(" · ") { "${it.name} ${it.percent}%" }
        return Summary(
            title = first.name,
            subtitle = listOf("${first.percent}%", others).filter { it.isNotEmpty() }.joinToString(" · "),
        )
    }
}

/** The utility shortcuts under the panel. */
enum class GlanceShortcut(val label: String) {
    TORCH("LANTERNA"),
    CALCULATOR("CALC"),
    CAMERA("CÂMERA"),
    CLOCK("RELÓGIO"),
    ;

    companion object {
        /** Label of the flashlight shortcut, which shows whether the torch is on. */
        /** Calculator apps to try when the system has no default calculator category (Samsung, Xiaomi...). */
        val CALCULATOR_PACKAGES = listOf(
            "com.sec.android.app.popupcalculator",
            "com.google.android.calculator",
            "com.android.calculator2",
            "com.miui.calculator",
            "com.oneplus.calculator",
            "com.coloros.calculator",
            "com.huawei.calculator",
        )

        /** True for a launcher label that is a calculator, in English or Portuguese. */
        fun looksLikeCalculator(label: String): Boolean {
            val l = label.lowercase()
            return l.contains("calculator") || l.contains("calculadora") || l == "calc"
        }

        fun torchLabel(on: Boolean): String = if (on) "[LANTERNA]" else TORCH.label
    }
}
