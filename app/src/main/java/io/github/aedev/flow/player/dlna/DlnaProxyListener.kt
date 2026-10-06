package io.github.aedev.flow.player.dlna

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

internal class DlnaProxyListener(
    private val scope: CoroutineScope,
    private val maxConnections: Int,
    private val handleClient: suspend (Socket) -> Unit,
) {
    private val slots = Semaphore(maxConnections)
    private val clients = ConcurrentHashMap<Socket, Job>()

    @Volatile
    private var server: ServerSocket? = null
    private var acceptJob: Job? = null

    val isRunning: Boolean get() = server != null
    val port: Int get() = server?.localPort ?: 0

    @Synchronized
    fun start(bindAddress: InetAddress? = null): Int {
        server?.let { return it.localPort }
        if (!scope.isActive) return 0
        val socket = ServerSocket(0, maxConnections, bindAddress ?: InetAddress.getLoopbackAddress())
        server = socket
        acceptJob =
            scope.launch {
                try {
                    while (!socket.isClosed) {
                        val client =
                            try {
                                socket.accept()
                            } catch (error: SocketException) {
                                if (!socket.isClosed) throw error
                                break
                            }
                        dispatch(socket, client)
                    }
                } catch (error: java.io.IOException) {
                    Log.w("StreamProxy", "Accept failed", error)
                } finally {
                    synchronized(this@DlnaProxyListener) {
                        // An old accept loop finishing must not stop a newly started listener.
                        if (server === socket) {
                            server = null
                            acceptJob = null
                        }
                        runCatching { socket.close() }
                    }
                }
            }
        acceptJob?.invokeOnCompletion {
            synchronized(this) {
                if (server === socket) {
                    server = null
                    acceptJob = null
                }
                runCatching { socket.close() }
            }
        }
        return socket.localPort
    }

    @Synchronized
    private fun dispatch(
        listener: ServerSocket,
        client: Socket,
    ) {
        if (server !== listener || !slots.tryAcquire()) {
            runCatching { client.close() }
            return
        }
        val job =
            scope.launch {
                try {
                    handleClient(client)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w("StreamProxy", "Client failed", error)
                }
            }
        clients[client] = job
        job.invokeOnCompletion {
            clients.remove(client)
            runCatching { client.close() }
            slots.release()
        }
    }

    @Synchronized
    fun stop() {
        val socket = server
        server = null
        runCatching { socket?.close() }
        acceptJob?.cancel()
        acceptJob = null
        clients.forEach { (client, job) ->
            runCatching { client.close() }
            job.cancel()
        }
    }
}
