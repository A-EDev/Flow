package io.github.aedev.flow.data.engagement

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedInvalidationBusTest {
    @Test
    fun `a full buffer suspends the producer instead of losing invalidations`() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val received = mutableListOf<FeedInvalidationBus.Event>()
            val subscriber =
                backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    FeedInvalidationBus.events.collect {
                        received += it
                        if (received.size == 1) release.await()
                    }
                }
            val events = List(20) { FeedInvalidationBus.Event.MarkedWatched("video-$it") }
            val producer = launch { events.forEach { FeedInvalidationBus.emit(it) } }
            runCurrent()
            assertThat(producer.isCompleted).isFalse()
            release.complete(Unit)
            runCurrent()
            producer.join()
            assertThat(received).containsExactlyElementsIn(events).inOrder()
            subscriber.cancel()
        }
}
