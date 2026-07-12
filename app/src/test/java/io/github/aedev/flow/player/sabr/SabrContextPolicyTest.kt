package io.github.aedev.flow.player.sabr

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.player.sabr.core.SabrSessionState
import io.github.aedev.flow.player.sabr.proto.ProtobufWriter
import io.github.aedev.flow.player.sabr.proto.SabrContextSendingPolicy
import io.github.aedev.flow.player.sabr.proto.SabrContextUpdate
import org.junit.Test

class SabrContextPolicyTest {
    @Test
    fun `packed context policy activates stops and discards types`() {
        val encoded = ProtobufWriter.encode {
            writeBytes(1, byteArrayOf(2, 3))
            writeInt32(2, 2)
            writeBytes(3, byteArrayOf(4))
        }
        val state = SabrSessionState().apply {
            updateFromContextUpdate(SabrContextUpdate(type = 2, value = byteArrayOf(2)))
            updateFromContextUpdate(SabrContextUpdate(type = 3, value = byteArrayOf(3)))
            updateFromContextUpdate(SabrContextUpdate(type = 4, value = byteArrayOf(4)))
        }

        state.updateFromContextSendingPolicy(SabrContextSendingPolicy.decode(encoded))

        assertThat(state.activeSabrContexts().map { it.type }).containsExactly(3)
        assertThat(state.unsentSabrContextTypes()).containsExactly(2)
    }
}
