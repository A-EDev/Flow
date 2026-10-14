package io.github.aedev.flow.data.localmedia

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalLibraryLoaderTest {
    private class FakeSource : LocalMediaSource {
        var reads = 0
        var failures = 0
        var gate: CompletableDeferred<Unit>? = null
        var completeGate: CompletableDeferred<Unit>? = null
        val calls = mutableListOf<String>()
        var reindexFails = false
        val changeSignal = MutableSharedFlow<Unit>()

        override suspend fun read(): LocalLibrary {
            reads++
            calls += "read"
            gate?.await()
            if (failures > 0) {
                failures--
                throw IllegalArgumentException("provider failed")
            }
            return LocalLibrary(videos = List(reads) { item(it.toLong()) })
        }

        override fun generation(): Long = 1L

        override fun changes(): Flow<Unit> = changeSignal

        override suspend fun reindex() {
            calls += "reindex"
            if (reindexFails) throw IllegalStateException("scanner unavailable")
        }

        override suspend fun complete(read: LocalLibrary): LocalLibrary {
            completeGate?.await()
            return read.copy(videos = read.videos.map { it.copy(title = "${it.title} from its file") })
        }
    }

    // advanceUntilIdle() skips backgroundScope work, so the loader gets its own scope on the test clock.
    private fun TestScope.loader(source: LocalMediaSource): LocalLibraryLoader {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        backgroundScope.coroutineContext.job.invokeOnCompletion { scope.cancel() }
        return LocalLibraryLoader(source, scope, changeDebounceMs = 0L, stopTimeoutMs = 0L)
    }

    @Test
    fun `a refresh with nobody watching still ends`() =
        runTest {
            val source = FakeSource()
            val loader = loader(source)

            loader.refresh()
            assertThat(loader.refreshing.value).isTrue()
            advanceUntilIdle()

            assertThat(loader.refreshing.value).isFalse()
            assertThat(source.reads).isEqualTo(1)
        }

    @Test
    fun `a refresh queued behind a change still ends when the screen leaves`() =
        runTest {
            val source = FakeSource()
            val loader = loader(source)
            val watcher = launch { loader.library.collect {} }
            advanceUntilIdle()

            source.gate = CompletableDeferred()
            source.changeSignal.emit(Unit)
            advanceUntilIdle()
            loader.refresh()
            advanceUntilIdle()
            watcher.cancel()
            source.gate?.complete(Unit)
            advanceUntilIdle()

            assertThat(loader.refreshing.value).isFalse()
        }

    @Test
    fun `a failed read reports failure and the next refresh recovers`() =
        runTest {
            val source = FakeSource().apply { failures = 1 }
            val loader = loader(source)

            assertThat(loader.library.first().failed).isTrue()
            loader.refresh()
            advanceUntilIdle()

            assertThat(loader.refreshing.value).isFalse()
            assertThat(loader.library.first().failed).isFalse()
        }

    @Test
    fun `a refresh brings the index up to date before reading it`() =
        runTest {
            val source = FakeSource()
            val loader = loader(source)

            loader.refresh()
            advanceUntilIdle()

            assertThat(source.calls).containsExactly("reindex", "read").inOrder()
        }

    @Test
    fun `a failed reindex still reads and ends the refresh`() =
        runTest {
            val source = FakeSource().apply { reindexFails = true }
            val loader = loader(source)

            loader.refresh()
            advanceUntilIdle()

            assertThat(source.reads).isEqualTo(1)
            assertThat(loader.refreshing.value).isFalse()
        }

    @Test
    fun `opening the library reads without reindexing`() =
        runTest {
            val source = FakeSource()
            val loader = loader(source)

            loader.library.first()
            advanceUntilIdle()

            assertThat(source.calls).containsExactly("read")
        }

    @Test
    fun `reopening with an unchanged generation reuses the last read`() =
        runTest {
            val source = FakeSource()
            val loader = loader(source)

            loader.library.first()
            advanceUntilIdle()
            loader.library.first()
            advanceUntilIdle()

            assertThat(source.reads).isEqualTo(1)
        }

    @Test
    fun `the completed read is shown after the first look`() =
        runTest {
            val source = FakeSource().apply { completeGate = CompletableDeferred() }
            val loader = loader(source)
            val shown = mutableListOf<LocalLibrary>()
            val watcher = launch { loader.library.collect { shown += it } }
            advanceUntilIdle()

            assertThat(
                shown
                    .last()
                    .videos
                    .single()
                    .title,
            ).isEqualTo("Clip 0")
            source.completeGate?.complete(Unit)
            advanceUntilIdle()

            assertThat(
                shown
                    .last()
                    .videos
                    .single()
                    .title,
            ).isEqualTo("Clip 0 from its file")
            watcher.cancel()
        }

    @Test
    fun `an older completion never replaces a newer read`() =
        runTest {
            val source = FakeSource().apply { completeGate = CompletableDeferred() }
            val loader = loader(source)
            val shown = mutableListOf<LocalLibrary>()
            val watcher = launch { loader.library.collect { shown += it } }
            advanceUntilIdle()

            source.changeSignal.emit(Unit)
            advanceUntilIdle()
            source.completeGate?.complete(Unit)
            advanceUntilIdle()

            assertThat(shown.last().videos.map { it.title }).containsExactly("Clip 0 from its file", "Clip 1 from its file")
            assertThat(shown.dropWhile { it.videos.size < 2 }.all { it.videos.size == 2 }).isTrue()
            watcher.cancel()
        }

    @Test
    fun `a failed read stays shown when the last good read completes`() =
        runTest {
            val source = FakeSource().apply { completeGate = CompletableDeferred() }
            val loader = loader(source)
            val shown = mutableListOf<LocalLibrary>()
            val watcher = launch { loader.library.collect { shown += it } }
            advanceUntilIdle()

            source.failures = 1
            loader.refresh()
            advanceUntilIdle()
            source.completeGate?.complete(Unit)
            advanceUntilIdle()

            assertThat(shown.last().failed).isTrue()
            watcher.cancel()
        }

    private companion object {
        fun item(id: Long) =
            LocalMediaItem(
                id = id,
                isVideo = true,
                contentUri = "content://media/external/video/media/$id",
                title = "Clip $id",
                durationMs = 1_000,
                sizeBytes = 1,
                dateAddedMs = id,
            )
    }
}
