package app.lawnchair.command

import app.lawnchair.util.Texts

/**
 * "pix 123.456.789-09": the Pix of that key with no amount, handed to the bank app the way the
 * camera hands it a Pix QR code it reads; the bank shows who gets the money and asks the amount.
 * A valid CPF or CNPJ, or a random key, typed alone offers it too.
 */
object PixCommand : BuiltIn(BuiltInCommand.PIX, R.string.cmd_usage_pix, R.string.cmd_help_pix) {

    override val requiresArgument = true

    /** Email and phone typed alone are left to the commands that write and call. */
    private val TYPED_ALONE = setOf(PixKind.CPF, PixKind.CNPJ, PixKind.RANDOM)

    override fun isApplicable(text: String, ctx: CommandContext): Boolean = PixKey.of(text)?.kind in TYPED_ALONE

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val title = "PIX"
        val text = arg.trim()
        if (text.isEmpty()) return commandResult(title, Texts.get(R.string.cmd_pix_empty), Tone.IDLE, null)
        val key = PixKey.of(text) ?: return commandResult(title, Texts.get(R.string.cmd_pix_invalid), Tone.ERROR, null)
        return commandResult(
            title,
            key.kind.label + " " + key.shown + " · " + Texts.get(R.string.cmd_pix_ready),
            Tone.OK,
            CommandAction.Pix(PixCode.of(key.value)),
        )
    }
}

enum class PixKind(val label: String) { CPF("CPF"), CNPJ("CNPJ"), PHONE("TEL"), EMAIL("EMAIL"), RANDOM("EVP") }

/** A Pix key written the way the Central Bank directory stores it, and the way it is shown. */
data class PixKey(val kind: PixKind, val value: String, val shown: String) {

    companion object {
        private val EMAIL = Regex("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)*\\.[A-Za-z]{2,}")
        private val RANDOM = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        private val NUMBER = Regex("\\+?[\\d .()/-]+")

        /**
         * The key in [text], or null. Eleven digits are a CPF when its check digits add up and a
         * mobile number otherwise; a phone without the country code gets +55.
         */
        fun of(text: String): PixKey? {
            val t = text.trim()
            if (RANDOM.matches(t)) return PixKey(PixKind.RANDOM, t.lowercase(), t.lowercase())
            if (EMAIL.matches(t)) return PixKey(PixKind.EMAIL, t.lowercase(), t.lowercase())
            if (!NUMBER.matches(t)) return null
            val digits = t.filter { it.isDigit() }
            val plus = t.startsWith('+')
            if (!plus && digits.length == 11 && validCpf(digits)) {
                return PixKey(PixKind.CPF, digits, "${digits.substring(0, 3)}.${digits.substring(3, 6)}.${digits.substring(6, 9)}-${digits.substring(9)}")
            }
            if (!plus && digits.length == 14 && validCnpj(digits)) {
                return PixKey(
                    PixKind.CNPJ,
                    digits,
                    "${digits.substring(0, 2)}.${digits.substring(2, 5)}.${digits.substring(5, 8)}/${digits.substring(8, 12)}-${digits.substring(12)}",
                )
            }
            val phone = when {
                (plus || digits.length > 11) && digits.startsWith("55") && digits.length in 12..13 -> digits
                !plus && digits.length in 10..11 -> "55$digits"
                else -> return null
            }
            return PixKey(PixKind.PHONE, "+$phone", "+$phone")
        }

        fun validCpf(d: String): Boolean {
            if (d.length != 11 || d.all { it == d[0] }) return false
            fun check(n: Int) = (0 until n).sumOf { (d[it] - '0') * (n + 1 - it) }.let { (it * 10 % 11) % 10 }
            return check(9) == d[9] - '0' && check(10) == d[10] - '0'
        }

        fun validCnpj(d: String): Boolean {
            if (d.length != 14 || d.all { it == d[0] }) return false
            fun check(n: Int): Int {
                val weights = if (n == 12) listOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2) else listOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
                val r = (0 until n).sumOf { (d[it] - '0') * weights[it] } % 11
                return if (r < 2) 0 else 11 - r
            }
            return check(12) == d[12] - '0' && check(13) == d[13] - '0'
        }
    }
}

/** The text of a static Pix QR code (BR Code, EMV format) with no amount. */
object PixCode {

    /**
     * The receiver's name and city are required fields, but the bank shows the name it finds for
     * the key, so one letter each is enough, as the generators the bank apps read write them.
     */
    private const val NAME = "N"
    private const val CITY = "C"

    fun of(key: String): String {
        val account = field("00", "BR.GOV.BCB.PIX") + field("01", key)
        val body = field("00", "01") +
            field("26", account) +
            field("52", "0000") +
            field("53", "986") +
            field("58", "BR") +
            field("59", NAME) +
            field("60", CITY) +
            field("62", field("05", "***")) +
            "6304"
        return body + crc16(body)
    }

    private fun field(id: String, value: String) = id + value.length.toString().padStart(2, '0') + value

    /** CRC-16/CCITT-FALSE over the UTF-8 bytes, as four upper-case hex digits. */
    fun crc16(text: String): String {
        var crc = 0xFFFF
        for (b in text.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
            crc = crc and 0xFFFF
        }
        return crc.toString(16).uppercase().padStart(4, '0')
    }
}
