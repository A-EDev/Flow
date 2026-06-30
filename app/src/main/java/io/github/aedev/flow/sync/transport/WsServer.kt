package io.github.aedev.flow.sync.transport

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel

/**
 * Show-QR/host role transport: an embedded Ktor **CIO** WebSocket server bound to `0.0.0.0:0`
 * (OS-assigned ephemeral port, read back for the QR). CIO is pure-Kotlin/coroutine so it runs on
 * Android. v1 handles a single 1↔1 session per server instance.
 */
class WsServer {

    private var engine: ApplicationEngine? = null
    private val connectionDeferred = CompletableDeferred<SyncConnection>()

    /** Bind to an ephemeral port and start listening; returns the actual bound port for the QR. */
    suspend fun start(): Int {
        val e = embeddedServer(CIO, host = "0.0.0.0", port = 0) {
            install(WebSockets)
            routing {
                webSocket(QrPath.PATH) {
                    val conn = KtorServerConnection(this)
                    if (!connectionDeferred.isCompleted) connectionDeferred.complete(conn)
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Binary) conn.deliver(frame.readBytes())
                        }
                    } catch (_: Throwable) {
                        // client dropped / cancelled
                    } finally {
                        conn.onClosed()
                    }
                }
            }
        }
        e.start(wait = false)
        engine = e
        return e.resolvedConnectors().first().port
    }

    /** Suspends until a peer connects and the handshake socket is ready. */
    suspend fun awaitConnection(): SyncConnection = connectionDeferred.await()

    fun stop() {
        runCatching { engine?.stop(0, 0) }
        engine = null
    }

    private object QrPath {
        const val PATH = "/flow-sync"
    }
}

/** Wraps a Ktor server [DefaultWebSocketSession] as a [SyncConnection]. */
private class KtorServerConnection(
    private val session: DefaultWebSocketSession,
) : SyncConnection {
    private val inbound = Channel<ByteArray>(Channel.UNLIMITED)

    suspend fun deliver(bytes: ByteArray) {
        inbound.trySend(bytes)
    }

    fun onClosed() {
        inbound.close()
    }

    override suspend fun send(bytes: ByteArray) {
        session.send(Frame.Binary(true, bytes))
    }

    override suspend fun receive(): ByteArray? = inbound.receiveCatching().getOrNull()

    override fun close() {
        inbound.close()
        runCatching { session.cancel() }
    }
}
