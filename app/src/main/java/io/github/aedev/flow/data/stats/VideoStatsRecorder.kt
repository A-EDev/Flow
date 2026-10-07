package io.github.aedev.flow.data.stats

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.PrivacyGate
import io.github.aedev.flow.data.local.dao.WatchHistoryDao
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place viewing activity enters the recap ledger. Every call is fire-and-forget on the
 * recorder's own scope, so a player or ViewModel tearing down never loses its last session.
 * Nothing is recorded during Deep Flow, matching the music ledger, and views wait out paused watch
 * history too; see [RecapEntry]. Local files are never recorded.
 */
@Singleton
class VideoStatsRecorder
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val watchHistoryDao: WatchHistoryDao,
        private val privacyGate: PrivacyGate,
    ) {
        private val store =
            MonthlyLedgerStore(
                appContext = appContext,
                tag = TAG,
                hotFileName = "flow_video_stats_hot_v1.json",
                coldFileName = "flow_video_stats_v1.json",
                monthSerializer = VideoMonthRecord.serializer(),
            )
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val mutex = Mutex()
        private var ledger = VideoStatsLedger()
        private var initialized = false
        private var pendingSave: Job? = null
        private val saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        /** Emits after each save of the ledger, for readers that show its totals outside the app. */
        val changes: Flow<Unit> = saved.asSharedFlow()

        /** A finished view. [video] supplies the topics; without it the view still counts, topic-less. */
        fun onView(
            event: ViewEvent,
            video: Video?,
        ) = record(RecapEntry.VIEW) {
            val topics =
                if (event.counted && video != null) {
                    runCatching { FlowNeuroEngine.topTopicsFor(video, VideoStatsParams.TOPICS_PER_VIEW) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
            val now = System.currentTimeMillis()
            locked { VideoStatsLedgerOps.recordView(it, now, event, topics) }
        }

        fun onDislike(video: DislikedVideo) =
            record(RecapEntry.ACTIVITY) { locked { VideoStatsLedgerOps.recordDislike(it, video.at, video) } }

        fun onDislikeRemoved(videoId: String) =
            record(RecapEntry.FORGET) { locked { VideoStatsLedgerOps.clearDislike(it, System.currentTimeMillis(), videoId) } }

        fun onAction(action: LedgerAction) =
            record(RecapEntry.ACTIVITY) {
                locked { VideoStatsLedgerOps.recordAction(it, System.currentTimeMillis(), action) }
            }

        /** A submitted search; [query] is null when search history is off, so only the count is kept. */
        fun onSearch(query: String?) =
            record(RecapEntry.ACTIVITY) { locked { VideoStatsLedgerOps.recordSearch(it, System.currentTimeMillis(), query) } }

        fun onSponsorSkip(
            category: String,
            skippedMs: Long,
        ) = record(RecapEntry.ACTIVITY) {
            locked { VideoStatsLedgerOps.recordSponsorSkip(it, System.currentTimeMillis(), category, skippedMs) }
        }

        /** Search history was cleared or switched off: the recap forgets every stored search text. */
        fun onSearchHistoryCleared() = record(RecapEntry.FORGET) { locked(VideoStatsLedgerOps::clearQueries) }

        /** Counted views in one month, without copying the ledger. */
        suspend fun monthViews(monthKey: String): Int {
            ensureInitialized()
            return mutex.withLock { ledger.months[monthKey]?.views ?: 0 }
        }

        suspend fun snapshot(): VideoStatsSnapshot {
            ensureInitialized()
            return mutex.withLock { ledger.toSnapshot() }
        }

        suspend fun restore(snapshot: VideoStatsSnapshot) {
            ensureInitialized()
            mutex.withLock {
                ledger = snapshot.toLedger()
                VideoStatsLedgerOps.prune(ledger)
                store.replace(
                    months = ledger.months.mapValues { it.value.toRecord() },
                    knownKeys = ledger.seedChannels,
                    currentKey = LedgerTime.at(System.currentTimeMillis()).monthKey,
                )
            }
        }

        private fun record(
            entry: RecapEntry,
            block: suspend () -> Unit,
        ) {
            scope.launch {
                runCatching {
                    if (!entry.allowed(privacyGate)) return@launch
                    ensureInitialized()
                    block()
                    scheduleSave()
                }.onFailure { Log.w(TAG, "Recap ledger update failed", it) }
            }
        }

        private suspend fun locked(update: (VideoStatsLedger) -> Unit) = mutex.withLock { update(ledger) }

        private suspend fun ensureInitialized() {
            if (initialized) return
            mutex.withLock {
                if (initialized) return
                val loaded = store.load()
                ledger =
                    VideoStatsSnapshot(
                        months = loaded.months,
                        seedChannels = loaded.knownKeys.toList(),
                    ).toLedger()
                if (loaded.months.isEmpty() && loaded.knownKeys.isEmpty()) {
                    ledger.seedChannels.addAll(runCatching { watchHistoryDao.getWatchedChannelIds() }.getOrDefault(emptyList()))
                    ledger.rebuildKnownChannels()
                }
                initialized = true
            }
        }

        private fun scheduleSave() {
            pendingSave?.cancel()
            pendingSave =
                scope.launch {
                    delay(SAVE_DEBOUNCE_MS)
                    mutex.withLock {
                        val currentKey = LedgerTime.at(System.currentTimeMillis()).monthKey
                        store.save(
                            currentKey = currentKey,
                            current = ledger.months[currentKey]?.toRecord(),
                            closedKeys = ledger.months.keys - currentKey,
                            knownKeys = ledger.seedChannels,
                        ) { ledger.months.filterKeys { it != currentKey }.mapValues { it.value.toRecord() } }
                    }
                    saved.tryEmit(Unit)
                }
        }

        private companion object {
            const val TAG = "VideoStatsRecorder"
            const val SAVE_DEBOUNCE_MS = 5_000L
        }
    }

/** Which privacy settings stop an entry from reaching the recap ledger. */
internal enum class RecapEntry {
    /** A watched video, with its title and channel: Deep Flow and paused watch history stop it. */
    VIEW,

    /** Searches, actions, dislikes and skipped segments: Deep Flow stops them. */
    ACTIVITY,

    /** Removing something already stored: always allowed. */
    FORGET,
    ;

    fun allows(
        deepFlowActive: Boolean,
        watchHistoryPaused: Boolean,
    ): Boolean =
        when (this) {
            VIEW -> !deepFlowActive && !watchHistoryPaused
            ACTIVITY -> !deepFlowActive
            FORGET -> true
        }

    suspend fun allowed(gate: PrivacyGate): Boolean =
        this == FORGET || allows(gate.isDeepFlowActive(), this == VIEW && gate.isWatchHistoryPaused())
}
