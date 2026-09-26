package io.github.aedev.flow.data.video.downloader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadSlotsTest {
    @Test
    fun `never runs more downloads than the limit`() =
        runTest {
            val slots = DownloadSlots(initialLimit = 2)
            val gate = CompletableDeferred<Unit>()
            val inFlight = AtomicInteger(0)
            var peak = 0
            val jobs =
                List(6) {
                    launch {
                        slots.withSlot {
                            peak = maxOf(peak, inFlight.incrementAndGet())
                            gate.await()
                            inFlight.decrementAndGet()
                        }
                    }
                }
            runCurrent()
            assertEquals(2, slots.running)
            gate.complete(Unit)
            jobs.forEach { it.join() }
            assertEquals(2, peak)
            assertEquals(0, slots.running)
        }

    @Test
    fun `raising the limit starts queued downloads`() =
        runTest {
            val slots = DownloadSlots(initialLimit = 1)
            val gate = CompletableDeferred<Unit>()
            val started = AtomicInteger(0)
            val jobs = List(3) { launch { slots.withSlot { started.incrementAndGet().also { gate.await() } } } }
            runCurrent()
            assertEquals(1, started.get())

            slots.setLimit(3)
            runCurrent()
            assertEquals(3, started.get())

            gate.complete(Unit)
            jobs.forEach { it.join() }
        }

    @Test
    fun `lowering the limit leaves running downloads alone and holds back new ones`() =
        runTest {
            val slots = DownloadSlots(initialLimit = 3)
            val release = List(4) { CompletableDeferred<Unit>() }
            val started = AtomicInteger(0)
            val jobs = release.map { gate -> launch { slots.withSlot { started.incrementAndGet().also { gate.await() } } } }
            runCurrent()
            assertEquals(3, started.get())

            slots.setLimit(1)
            runCurrent()
            assertEquals(3, slots.running)
            assertTrue(jobs.take(3).all { it.isActive })

            release[0].complete(Unit)
            release[1].complete(Unit)
            runCurrent()
            assertEquals(3, started.get())

            release[2].complete(Unit)
            runCurrent()
            assertEquals(4, started.get())

            release[3].complete(Unit)
            jobs.forEach { it.join() }
        }

    @Test
    fun `nothing starts until the limit is known`() =
        runTest {
            val slots = DownloadSlots()
            var ran = false
            val job = launch { slots.withSlot { ran = true } }
            runCurrent()
            assertEquals(false, ran)

            slots.setLimit(2)
            job.join()
            assertTrue(ran)
        }

    @Test
    fun `a cancelled wait does not take a slot`() =
        runTest {
            val slots = DownloadSlots(initialLimit = 1)
            val gate = CompletableDeferred<Unit>()
            val holder = launch { slots.withSlot { gate.await() } }
            val waiter = launch { slots.withSlot { } }
            runCurrent()
            waiter.cancel()
            gate.complete(Unit)
            holder.join()
            assertEquals(0, slots.running)
        }
}
