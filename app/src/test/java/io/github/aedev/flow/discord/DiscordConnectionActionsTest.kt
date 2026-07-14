package io.github.aedev.flow.discord

import android.app.Activity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DiscordConnectionActionsTest {
    @Test
    fun `retry reconnects with stored credentials`() = runTest {
        val transport = RecordingTransport()
        val tokens = DiscordAuthTokens("saved", "", Long.MAX_VALUE)

        retryDiscordConnection(transport) { tokens }

        assertThat(transport.connectedTokens).isEqualTo(tokens)
        assertThat(transport.linkCalls).isEqualTo(0)
    }

    @Test
    fun `retry starts account linking when credentials are unavailable`() = runTest {
        val transport = RecordingTransport()

        retryDiscordConnection(transport) { null }

        assertThat(transport.connectedTokens).isNull()
        assertThat(transport.linkCalls).isEqualTo(1)
    }

    @Test
    fun `unlink disables sharing clears transport and removes account label`() = runTest {
        val transport = RecordingTransport()
        val events = mutableListOf<String>()

        val result = unlinkDiscordConnection(
            transport = transport,
            disablePreference = { events += "disabled" },
            clearAccountLabel = { events += "label-cleared" },
        )

        assertThat(result).isTrue()
        assertThat(transport.unlinkCalls).isEqualTo(1)
        assertThat(events).containsExactly("disabled", "label-cleared").inOrder()
    }

    private class RecordingTransport : DiscordPresenceTransport {
        override val isAvailable = true
        override val connectionState = MutableStateFlow(DiscordConnectionState.DISCONNECTED)
        override val linkedAccountName = MutableStateFlow<String?>(null)
        override val lastError = MutableStateFlow<String?>(null)
        var connectedTokens: DiscordAuthTokens? = null
        var linkCalls = 0
        var unlinkCalls = 0

        override fun attachActivity(activity: Activity?) = Unit

        override suspend fun link(): DiscordLinkResult {
            linkCalls += 1
            return DiscordLinkResult.Success
        }

        override suspend fun connect(tokens: DiscordAuthTokens): DiscordLinkResult {
            connectedTokens = tokens
            return DiscordLinkResult.Success
        }

        override suspend fun update(payload: DiscordPresencePayload) = true
        override suspend fun clear() = true

        override suspend fun unlink(): Boolean {
            unlinkCalls += 1
            return true
        }

        override fun close() = Unit
    }
}
