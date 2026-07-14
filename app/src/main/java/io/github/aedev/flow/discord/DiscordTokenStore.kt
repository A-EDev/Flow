package io.github.aedev.flow.discord

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class DiscordTokenStore(context: Context) {
    private val storageFile = AtomicFile(
        File(context.applicationContext.noBackupFilesDir, TOKEN_FILE_NAME),
    )

    @Synchronized
    fun save(tokens: DiscordAuthTokens) {
        val plaintext = JSONObject()
            .put("access_token", tokens.accessToken)
            .put("refresh_token", tokens.refreshToken)
            .put("expires_at", tokens.expiresAtEpochSeconds)
            .toString()
            .toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val payload = JSONObject()
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put(
                "ciphertext",
                Base64.encodeToString(cipher.doFinal(plaintext), Base64.NO_WRAP),
            )
            .toString()
            .toByteArray(Charsets.UTF_8)

        val output = storageFile.startWrite()
        try {
            output.use { it.write(payload) }
            storageFile.finishWrite(output)
        } catch (error: Throwable) {
            storageFile.failWrite(output)
            throw error
        }
    }

    @Synchronized
    fun load(): DiscordAuthTokens? {
        if (!storageFile.baseFile.exists()) return null

        return runCatching {
            val payload = JSONObject(
                storageFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() },
            )
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    GCMParameterSpec(
                        GCM_TAG_LENGTH_BITS,
                        Base64.decode(payload.getString("iv"), Base64.NO_WRAP),
                    ),
                )
            }
            val plaintext = cipher.doFinal(
                Base64.decode(payload.getString("ciphertext"), Base64.NO_WRAP),
            )
            val tokens = JSONObject(String(plaintext, Charsets.UTF_8))
            DiscordAuthTokens(
                accessToken = tokens.getString("access_token"),
                refreshToken = tokens.getString("refresh_token"),
                expiresAtEpochSeconds = tokens.getLong("expires_at"),
            )
        }.getOrElse {
            clear()
            null
        }
    }

    @Synchronized
    fun clear() {
        storageFile.delete()
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply {
                load(null)
                deleteEntry(KEY_ALIAS)
            }
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        ).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val TOKEN_FILE_NAME = "discord_tokens.enc"
        const val KEY_ALIAS = "flow_discord_tokens_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
