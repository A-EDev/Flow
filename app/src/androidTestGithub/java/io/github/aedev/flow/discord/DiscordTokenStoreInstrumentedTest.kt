package io.github.aedev.flow.discord

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiscordTokenStoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = DiscordTokenStore(context)

    @Before
    fun setUp() {
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun tokenRoundTripsThroughKeystoreEncryptedStorage() {
        val tokens = DiscordAuthTokens(
            accessToken = "account-token",
            refreshToken = "",
            expiresAtEpochSeconds = Long.MAX_VALUE,
        )

        store.save(tokens)

        assertEquals(tokens, store.load())
        val bytes = File(context.noBackupFilesDir, "discord_tokens.enc").readBytes()
        assertFalse(bytes.toString(Charsets.UTF_8).contains(tokens.accessToken))
        assertTrue(androidKeyStore().containsAlias("flow_discord_tokens_v1"))
    }

    @Test
    fun clearRemovesCiphertextAndKeystoreKey() {
        store.save(DiscordAuthTokens("account-token", "", Long.MAX_VALUE))

        store.clear()

        assertNull(store.load())
        assertFalse(File(context.noBackupFilesDir, "discord_tokens.enc").exists())
        assertFalse(androidKeyStore().containsAlias("flow_discord_tokens_v1"))
    }

    private fun androidKeyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }
}
