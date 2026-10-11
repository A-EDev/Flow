package io.github.aedev.flow.sync

import io.github.aedev.flow.sync.transport.SyncConnection
import kotlinx.coroutines.channels.Channel

/** A pair of UNLIMITED channels wired as a full-duplex in-memory [SyncConnection] pair. */
class SyncLoopback {
    private val hostToClient = Channel<ByteArray>(Channel.UNLIMITED)
    private val clientToHost = Channel<ByteArray>(Channel.UNLIMITED)

    /** Every frame the client sent, in order, for tests that inspect what went on the wire. */
    val sentByClient = mutableListOf<ByteArray>()

    private fun end(
        out: Channel<ByteArray>,
        inn: Channel<ByteArray>,
        record: MutableList<ByteArray>?,
    ) = object : SyncConnection {
        override suspend fun send(bytes: ByteArray) {
            record?.add(bytes)
            out.send(bytes)
        }

        override suspend fun receive(): ByteArray? = inn.receiveCatching().getOrNull()

        override fun close() {
            out.close()
        }
    }

    val host: SyncConnection get() = end(hostToClient, clientToHost, null)
    val client: SyncConnection get() = end(clientToHost, hostToClient, sentByClient)
}
