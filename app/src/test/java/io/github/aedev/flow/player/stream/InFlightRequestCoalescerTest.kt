package io.github.aedev.flow.player.stream

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class InFlightRequestCoalescerTest {
    @Test
    fun `concurrent requests for the same key share one operation`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val coalescer = InFlightRequestCoalescer<String, String>(scope)
            val requestCount = AtomicInteger()
            val release = CompletableDeferred<Unit>()

            val first = async(start = CoroutineStart.UNDISPATCHED) {
                coalescer.run("video") {
                    requestCount.incrementAndGet()
                    release.await()
                    "streams"
                }
            }
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                coalescer.run("video") {
                    requestCount.incrementAndGet()
                    "duplicate"
                }
            }

            release.complete(Unit)

            assertEquals("streams", first.await())
            assertEquals("streams", second.await())
            assertEquals(1, requestCount.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `cancelling one waiter does not cancel the shared operation`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val coalescer = InFlightRequestCoalescer<String, String>(scope)
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()

            val cancelledWaiter = async(start = CoroutineStart.UNDISPATCHED) {
                coalescer.run("video") {
                    started.complete(Unit)
                    release.await()
                    "streams"
                }
            }
            started.await()
            val survivingWaiter = async(start = CoroutineStart.UNDISPATCHED) {
                coalescer.run("video") { "duplicate" }
            }

            cancelledWaiter.cancel()
            release.complete(Unit)

            assertEquals("streams", survivingWaiter.await())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `cancelling the last waiter cancels unobserved work`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val coalescer = InFlightRequestCoalescer<String, String>(scope)
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()

            val onlyWaiter = async(start = CoroutineStart.UNDISPATCHED) {
                coalescer.run("video") {
                    try {
                        started.complete(Unit)
                        CompletableDeferred<Unit>().await()
                        "streams"
                    } finally {
                        cancelled.complete(Unit)
                    }
                }
            }

            started.await()
            onlyWaiter.cancelAndJoin()

            cancelled.await()
        } finally {
            scope.cancel()
        }
    }
}
