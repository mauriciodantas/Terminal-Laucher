package app.lawnchair.util

import java.text.Normalizer

private val combiningMarks = Regex("\\p{M}+")

/** Lower case and no accents, so "Itaú" and "itau" compare equal. */
fun String.foldAccents(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(combiningMarks, "")
    .lowercase()
