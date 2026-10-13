package app.lawnchair.command

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray

/** The user's API providers, next to the actions that use them. The built-in ones are not saved. */
object ProviderStore {

    private const val STORE = "nostromo"
    private const val KEY = "api_providers"

    fun load(context: Context): List<ApiProvider> {
        val raw = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching { ProviderCodec.fromJson(JSONArray(raw)) }.getOrDefault(emptyList())
    }

    fun save(context: Context, providers: List<ApiProvider>) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
            .putString(KEY, ProviderCodec.toJson(providers.filter { !Providers.isBuiltIn(it.id) }).toString()).apply()
    }

    /** The built-in providers and the saved ones. */
    fun all(context: Context): List<ApiProvider> = Providers.all(load(context))
}

/**
 * The secrets of the providers (keys, tokens, passwords), each encrypted with an AES key that lives
 * in the Android Keystore and never leaves it. They sit in a file of their own, outside the
 * launcher's backup and the command packs; restored on another phone they could not be read anyway,
 * so a provider simply asks for its secret again.
 */
object SecretStore {

    private const val FILE = "provider_secrets"
    private const val ALIAS = "terminal_provider_secrets"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The secret of provider [id], or null when there is none or it cannot be read any more. */
    fun get(context: Context, id: String): String? {
        val raw = prefs(context).getString(id, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(raw, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).decodeToString()
        }.getOrNull()
    }

    fun put(context: Context, id: String, secret: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(secret.toByteArray())
        prefs(context).edit().putString(id, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }

    fun remove(context: Context, id: String) {
        prefs(context).edit().remove(id).apply()
    }

    /** Ids of [ids] whose secret is saved and readable. */
    fun configured(context: Context, ids: Collection<String>): Set<String> = ids.filter { get(context, it) != null }.toSet()

    /** A lookup by provider id, for [ApiCall.run]. */
    fun lookup(context: Context): (String) -> String? {
        val app = context.applicationContext
        return { id -> get(app, id) }
    }

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
