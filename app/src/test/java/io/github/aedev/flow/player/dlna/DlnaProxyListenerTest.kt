package io.github.aedev.flow.player.dlna

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Test
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DlnaProxyListenerTest {
    @Test
    fun `concurrent starts share one bound listener`() {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val listener = DlnaProxyListener(scope, 2) { }
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val attempts =
                List(16) {
                    executor.submit<Int> {
                        start.await()
                        listener.start()
                    }
                }
            start.countDown()
            val ports = attempts.map { it.get(3, TimeUnit.SECONDS) }
            assertThat(ports.toSet()).hasSize(1)
            assertThat(listener.isRunning).isTrue()
        } finally {
            listener.stop()
            scope.cancel()
            executor.shutdownNow()
        }
    }

    @Test
    fun `connections exceeding the handler limit are closed`() {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val entered = CountDownLatch(2)
        val listener =
            DlnaProxyListener(scope, 2) { socket ->
                entered.countDown()
                socket.getInputStream().read()
            }
        try {
            val port = listener.start()
            Socket("127.0.0.1", port).use { first ->
                Socket("127.0.0.1", port).use { second ->
                    assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue()
                    Socket("127.0.0.1", port).use { rejected ->
                        rejected.soTimeout = 3000
                        assertThat(rejected.getInputStream().read()).isEqualTo(-1)
                    }
                    listener.stop()
                    first.soTimeout = 3000
                    second.soTimeout = 3000
                    assertThat(first.getInputStream().read()).isEqualTo(-1)
                    assertThat(second.getInputStream().read()).isEqualTo(-1)
                }
            }
            assertThat(listener.isRunning).isFalse()
        } finally {
            listener.stop()
            scope.cancel()
        }
    }

    @Test
    fun `an old accept loop cannot stop a restarted listener`() {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val listener = DlnaProxyListener(scope, 2) { socket -> socket.getOutputStream().write(7) }
        try {
            repeat(20) {
                listener.start()
                listener.stop()
                val port = listener.start()
                Socket("127.0.0.1", port).use { client ->
                    client.soTimeout = 3000
                    assertThat(client.getInputStream().read()).isEqualTo(7)
                }
                assertThat(listener.isRunning).isTrue()
                listener.stop()
            }
        } finally {
            listener.stop()
            scope.cancel()
        }
    }

    @Test
    fun `an explicit bind address is honored`() {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val listener = DlnaProxyListener(scope, 2) { socket -> socket.getOutputStream().write(9) }
        try {
            val port = listener.start(InetAddress.getLoopbackAddress())
            Socket("127.0.0.1", port).use { client ->
                client.soTimeout = 3000
                assertThat(client.getInputStream().read()).isEqualTo(9)
            }
            assertThat(listener.isRunning).isTrue()
        } finally {
            listener.stop()
            scope.cancel()
        }
    }

    @Test
    fun `a cancelled scope cannot leave a newly bound listener behind`() {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.cancel()
        val listener = DlnaProxyListener(scope, 2) { }
        try {
            listener.start()
            assertThat(listener.isRunning).isFalse()
        } finally {
            listener.stop()
        }
    }
}
