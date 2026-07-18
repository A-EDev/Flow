package io.github.aedev.flow.data.migration

import android.util.Log
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.WatchLaterMetadataMigrationStore
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.YouTubeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class WatchLaterMetadataMigrator @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val youTubeRepository: YouTubeRepository,
    private val migrationStore: WatchLaterMetadataMigrationStore
) {
    private val migrationMutex = Mutex()

    suspend fun migrate(videos: List<Video>) = migrationMutex.withLock {
        withContext(Dispatchers.IO) {
            val state = migrationStore.state()
            if (state.isComplete) return@withContext

            val pending = videos.filter { video ->
                video.id.isNotBlank() &&
                    video.title.isNotBlank() &&
                    video.id !in state.processedVideoIds
            }
            if (pending.isEmpty()) {
                migrationStore.complete()
                return@withContext
            }

            pending.forEachIndexed { index, video ->
                val refreshed = withTimeoutOrNull(METADATA_TIMEOUT_MS) {
                    youTubeRepository.refreshVideoMetadata(video)
                }
                if (refreshed == null) {
                    if (video.id in state.failedOnceVideoIds) {
                        migrationStore.markProcessed(video.id)
                    } else {
                        migrationStore.markFailedOnce(video.id)
                        Log.w(TAG, "Pausing migration after ${video.id} failed")
                        return@withContext
                    }
                } else {
                    playlistRepository.updateVideoMetadata(refreshed)
                    migrationStore.markProcessed(video.id)
                }

                if (index < pending.lastIndex) {
                    delay(Random.nextLong(MIN_REQUEST_DELAY_MS, MAX_REQUEST_DELAY_MS + 1L))
                    if ((index + 1) % PAUSE_AFTER_REQUESTS == 0) delay(BATCH_PAUSE_MS)
                }
            }

            migrationStore.complete()
        }
    }

    private companion object {
        const val TAG = "WatchLaterMigrator"
        const val METADATA_TIMEOUT_MS = 10_000L
        const val MIN_REQUEST_DELAY_MS = 1_500L
        const val MAX_REQUEST_DELAY_MS = 2_500L
        const val PAUSE_AFTER_REQUESTS = 25
        const val BATCH_PAUSE_MS = 5_000L
    }
}
