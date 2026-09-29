package io.github.aedev.flow.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals short secrets with an AES-GCM key that never leaves the Android Keystore, so a secret copied
 * out of the preferences file (a rooted device, an adb backup) is useless anywhere else.
 */
internal object KeystoreSecretBox {
    private const val TAG = "KeystoreSecretBox"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "flow_preference_secrets"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "ks1:"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    fun seal(plain: String): String {
        if (plain.isEmpty()) return plain
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    /**
     * The secret inside [stored]. A value saved before sealing existed is returned as it is; one that
     * no longer opens (the key was wiped with the app's data) reads as empty rather than as garbage.
     */
    fun open(stored: String?): String {
        if (stored.isNullOrEmpty() || !stored.startsWith(PREFIX)) return stored.orEmpty()
        return runCatching {
            val sealed = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val cipher =
                Cipher.getInstance(TRANSFORMATION).apply {
                    init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
                }
            String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
        }.getOrElse {
            Log.w(TAG, "Stored secret could not be opened", it)
            ""
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(KEY_BITS)
                        .build(),
                )
            }.generateKey()
    }
}
