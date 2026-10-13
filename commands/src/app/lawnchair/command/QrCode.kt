package app.lawnchair.command

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** The squares of a QR code, row by row; true is dark. A one-module quiet border is included. */
class QrMatrix(val size: Int, private val bits: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = bits[y * size + x]
}

object QrCode {

    /** [text] as a QR code, or null when it does not fit in one. */
    fun encode(text: String): QrMatrix? = runCatching {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
        )
        // Size 1 asks for one pixel per module: the bar scales it up itself.
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 1, 1, hints)
        val size = matrix.width
        QrMatrix(size, BooleanArray(size * size) { matrix.get(it % size, it / size) })
    }.getOrNull()
}
