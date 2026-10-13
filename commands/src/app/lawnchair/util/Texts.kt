package app.lawnchair.util

import android.content.Context
import androidx.annotation.StringRes

/**
 * Translated text for the pure rule objects (command bar, status panel), which take no Context so
 * they can be unit tested. The app installs the resource lookup when it starts; unit tests install
 * one that reads the Portuguese strings file.
 */
object Texts {

    fun interface Lookup {
        fun get(@StringRes id: Int, args: Array<out Any>): String
    }

    @Volatile
    private var lookup: Lookup? = null

    fun install(context: Context) {
        val app = context.applicationContext
        lookup = Lookup { id, args -> if (args.isEmpty()) app.getString(id) else app.getString(id, *args) }
    }

    fun install(lookup: Lookup) {
        this.lookup = lookup
    }

    fun get(@StringRes id: Int, vararg args: Any): String = checkNotNull(lookup) { "Texts.install was not called" }.get(id, args)
}
