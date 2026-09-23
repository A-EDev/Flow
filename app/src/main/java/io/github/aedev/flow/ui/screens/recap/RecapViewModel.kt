package io.github.aedev.flow.ui.screens.recap

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.SearchHistoryRepository
import io.github.aedev.flow.data.local.dao.ChannelVideoCount
import io.github.aedev.flow.data.local.dao.WatchHistoryDao
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import io.github.aedev.flow.data.recommendation.music.MusicStatsStorage
import io.github.aedev.flow.data.stats.RecapAggregates
import io.github.aedev.flow.data.stats.RecapImageResolver
import io.github.aedev.flow.data.stats.RecapPeriod
import io.github.aedev.flow.data.stats.RecapSummary
import io.github.aedev.flow.data.stats.VideoStatsRecorder
import io.github.aedev.flow.data.stats.VideoStatsSnapshot
import io.github.aedev.flow.data.stats.withImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** Which ledger a recap page shows. */
enum class RecapSource { ALL, VIDEO, MUSIC }

@Immutable
data class RecapUiState(
    val loading: Boolean = true,
    val months: List<YearMonth> = emptyList(),
    val period: RecapPeriod = RecapPeriod.Month(YearMonth.now()),
    val source: RecapSource = RecapSource.ALL,
    val summary: RecapSummary? = null,
    /** Distinct videos per channel from watch history, shown only for all time. */
    val history: List<ChannelVideoCount> = emptyList(),
) {
    val years: List<Int> get() = months.map { it.year }.distinct()
    val isEmpty: Boolean get() = !loading && months.isEmpty()
}

/**
 * The recap pages' data. Both ledgers are read once when the page opens; every period the viewer
 * picks after that is folded from those snapshots off the main thread. Nothing here uses the network.
 */
@HiltViewModel
class RecapViewModel
    @Inject
    constructor(
        private val videoStats: VideoStatsRecorder,
        private val musicBrain: MusicBrainEngine,
        private val watchHistoryDao: WatchHistoryDao,
        private val searchHistory: SearchHistoryRepository,
        private val images: RecapImageResolver,
    ) : ViewModel() {
        private val _state = MutableStateFlow(RecapUiState())
        val state: StateFlow<RecapUiState> = _state.asStateFlow()

        private var video = VideoStatsSnapshot()
        private var music = MusicStatsStorage.SerializableStats()
        private var queriesSince: YearMonth? = null
        private var portraits: Map<String, String> = emptyMap()
        private var historyLoaded = false
        private var loaded = false

        /** A period asked for before the ledgers finished loading; it wins over the newest month. */
        private var requested: Pair<RecapPeriod, RecapSource>? = null
        private var foldJob: Job? = null

        init {
            viewModelScope.launch {
                withContext(Dispatchers.Default) {
                    video = runCatching { videoStats.snapshot() }.getOrDefault(VideoStatsSnapshot())
                    music = runCatching { musicBrain.listeningStats() }.getOrDefault(MusicStatsStorage.SerializableStats())
                    queriesSince = searchQueryCutoff()
                    portraits = runCatching { images.localImages() }.getOrDefault(emptyMap())
                }
                val months = RecapAggregates.availableMonths(video, music)
                val newest = months.firstOrNull()?.let { RecapPeriod.Month(it) } ?: RecapPeriod.Month(YearMonth.now())
                _state.update { it.copy(months = months) }
                loaded = true
                val (period, source) = requested ?: (newest to _state.value.source)
                show(period, source)
            }
        }

        fun selectPeriod(period: RecapPeriod) = show(period, _state.value.source)

        fun selectSource(source: RecapSource) = show(_state.value.period, source)

        /** Opens on [period] rather than the newest month, for a recap opened from its own card. */
        fun openAt(
            period: RecapPeriod,
            source: RecapSource = _state.value.source,
        ) {
            requested = period to source
            if (loaded) show(period, source)
        }

        private fun show(
            period: RecapPeriod,
            source: RecapSource,
        ) {
            foldJob?.cancel()
            foldJob =
                viewModelScope.launch {
                    val summary =
                        withContext(Dispatchers.Default) {
                            RecapAggregates
                                .summarize(period, video.forSource(source), music.forSource(source), queriesSince)
                                .withImages(portraits)
                        }
                    val history = if (period == RecapPeriod.AllTime && source != RecapSource.MUSIC) channelHistory() else emptyList()
                    _state.update { it.copy(loading = false, period = period, source = source, summary = summary, history = history) }
                    fillMissingPortraits(summary)
                }
        }

        private suspend fun channelHistory(): List<ChannelVideoCount> {
            if (historyLoaded) return _state.value.history
            historyLoaded = true
            return withContext(Dispatchers.IO) {
                runCatching { watchHistoryDao.getChannelVideoCounts(HISTORY_CHANNELS) }.getOrDefault(emptyList())
            }
        }

        /** Fetches the portraits the shown summary still lacks, then shows them where it still applies. */
        private suspend fun fillMissingPortraits(summary: RecapSummary) {
            val found = runCatching { images.fetchMissing(summary) }.getOrDefault(emptyMap())
            if (found.isEmpty()) return
            portraits = portraits + found
            _state.update { state -> state.summary?.let { state.copy(summary = it.withImages(found)) } ?: state }
        }

        private fun VideoStatsSnapshot.forSource(source: RecapSource) = if (source == RecapSource.MUSIC) VideoStatsSnapshot() else this

        private fun MusicStatsStorage.SerializableStats.forSource(source: RecapSource) =
            if (source == RecapSource.VIDEO) MusicStatsStorage.SerializableStats() else this

        /** Search texts follow the search history's own auto-delete, so the recap never outlives it. */
        private suspend fun searchQueryCutoff(): YearMonth? {
            if (!searchHistory.isAutoDeleteHistoryEnabledFlow().first()) return null
            val days = searchHistory.getHistoryRetentionDaysFlow().first()
            return YearMonth.from(LocalDate.now().minusDays(days.toLong()))
        }

        private companion object {
            const val HISTORY_CHANNELS = 10
        }
    }
