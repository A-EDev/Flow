package io.github.aedev.flow.ui.screens.settings.taste

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.backup.BackupCoordinator
import io.github.aedev.flow.data.local.dao.WatchHistoryDao
import io.github.aedev.flow.data.recommendation.ContentVector
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.FlowPersona
import io.github.aedev.flow.data.recommendation.TimeBucket
import io.github.aedev.flow.data.recommendation.UserBrain
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import io.github.aedev.flow.data.stats.VideoStatsRecorder
import io.github.aedev.flow.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Five traits of a taste vector, each 0..1, in the order the radar draws them. */
@Immutable
data class TasteTraits(
    val pacing: Float,
    val complexity: Float,
    val duration: Float,
    val live: Float,
    val breadth: Float,
) {
    val values: List<Float> get() = listOf(pacing, complexity, duration, live, breadth)
}

@Immutable
data class TasteTopic(
    val id: String,
    /** This topic's weight relative to the strongest one, 0..1. */
    val share: Float,
    val preferred: Boolean,
)

@Immutable
data class TasteChannel(
    val id: String,
    val name: String,
    val score: Float,
)

@Immutable
data class NamedItem(
    val id: String,
    val name: String,
)

@Immutable
data class MusicTaste(
    val maturity: String,
    val appetite: Float,
    val plays: Int,
    val genres: List<Pair<String, Float>>,
)

@Immutable
data class HiddenContent(
    val topics: List<String> = emptyList(),
    val channels: List<NamedItem> = emptyList(),
    val artists: List<NamedItem> = emptyList(),
) {
    val count: Int get() = topics.size + channels.size + artists.size
}

@Immutable
data class TasteState(
    val loading: Boolean = true,
    val persona: FlowPersona? = null,
    val maturity: Float = 0f,
    val profile: TasteTraits? = null,
    val now: TasteTraits? = null,
    val topics: List<TasteTopic> = emptyList(),
    val channels: List<TasteChannel> = emptyList(),
    val music: MusicTaste? = null,
    val hidden: HiddenContent = HiddenContent(),
    val engine: EngineDetails? = null,
)

/**
 * How the engines see the viewer, and the levers that change it. Everything is read from local
 * state; channel names come from the recap ledger and watch history, never from the network.
 */
@HiltViewModel
class TasteViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val musicBrain: MusicBrainEngine,
        private val videoStats: VideoStatsRecorder,
        private val watchHistoryDao: WatchHistoryDao,
        private val backup: BackupCoordinator,
    ) : SettingsViewModel() {
        private val _state = MutableStateFlow(TasteState())
        val state: StateFlow<TasteState> = _state.asStateFlow()
        val operation = backup.operation

        init {
            reload()
        }

        fun reload() {
            viewModelScope.launch {
                _state.value = withContext(Dispatchers.Default) { load() }
            }
        }

        fun setTopicPreferred(
            topic: String,
            preferred: Boolean,
        ) = act {
            if (preferred) FlowNeuroEngine.addPreferredTopic(context, topic) else FlowNeuroEngine.removePreferredTopic(context, topic)
        }

        fun blockTopic(topic: String) = act { FlowNeuroEngine.addBlockedTopic(context, topic) }

        fun unblockTopic(topic: String) = act { FlowNeuroEngine.removeBlockedTopic(context, topic) }

        fun blockChannel(channelId: String) = act { FlowNeuroEngine.blockChannel(context, channelId) }

        fun unblockChannel(channelId: String) = act { FlowNeuroEngine.unblockChannel(context, channelId) }

        fun unblockArtist(artistKey: String) = act { musicBrain.unblockArtist(artistKey) }

        fun resetVideoProfile() = act { FlowNeuroEngine.resetBrain(context) }

        fun resetMusicProfile() = act { musicBrain.resetBrain() }

        fun exportVideoProfile(uri: Uri) = backup.exportEngine(uri)

        fun importVideoProfile(uri: Uri) = backup.importEngine(uri)

        fun exportMusicProfile(uri: Uri) = backup.exportMusicBrain(uri)

        fun importMusicProfile(uri: Uri) = backup.importMusicBrain(uri)

        fun dismissOperation() = backup.dismiss()

        private fun act(block: suspend () -> Unit) {
            viewModelScope.launch {
                runCatching { block() }
                _state.value = withContext(Dispatchers.Default) { load() }
            }
        }

        private suspend fun load(): TasteState {
            FlowNeuroEngine.initialize(context)
            val brain = FlowNeuroEngine.getBrainSnapshot()
            val names = channelNames()
            val profile = runCatching { musicBrain.tasteProfile() }.getOrNull()
            val strongest =
                brain.globalVector.topics.values
                    .maxOrNull()
                    ?.takeIf { it > 0.0 } ?: 1.0
            return TasteState(
                loading = false,
                persona = FlowNeuroEngine.getPersona(brain),
                maturity = (brain.totalInteractions / MATURE_INTERACTIONS).coerceIn(0f, 1f),
                profile = brain.globalVector.traits(),
                now = (brain.timeVectors[TimeBucket.current()] ?: ContentVector()).traits(),
                topics =
                    brain.globalVector.topics.entries
                        .filter { it.value > 0.0 && it.key !in brain.blockedTopics }
                        .sortedByDescending { it.value }
                        .take(TOPIC_COUNT)
                        .map { TasteTopic(it.key, (it.value / strongest).toFloat(), it.key in brain.preferredTopics) },
                channels =
                    brain.channelScores.entries
                        .filter { it.key !in brain.blockedChannels }
                        .sortedByDescending { it.value }
                        .take(CHANNEL_COUNT)
                        .map { TasteChannel(it.key, names[it.key] ?: it.key, it.value.toFloat()) },
                music =
                    profile?.let { taste ->
                        val topGenre = taste.topGenres.maxOfOrNull { it.second }?.takeIf { it > 0.0 } ?: 1.0
                        MusicTaste(
                            maturity = taste.maturity,
                            appetite = taste.discoveryAppetite.toFloat(),
                            plays = taste.totalPlays,
                            genres = taste.topGenres.take(GENRE_COUNT).map { (name, weight) -> name to (weight / topGenre).toFloat() },
                        )
                    },
                hidden = hidden(brain, names),
                engine = EngineDetails.of(brain),
            )
        }

        private suspend fun hidden(
            brain: UserBrain,
            names: Map<String, String>,
        ) = HiddenContent(
            topics = brain.blockedTopics.sorted(),
            channels = brain.blockedChannels.map { NamedItem(it, names[it] ?: it) }.sortedBy { it.name.lowercase() },
            artists =
                runCatching { musicBrain.getBlockedArtistsWithNames() }
                    .getOrDefault(
                        emptyList(),
                    ).map { NamedItem(it.first, it.second) },
        )

        /** Channel names the device already knows: the recap ledger first, then watch history. */
        private suspend fun channelNames(): Map<String, String> {
            val names = HashMap<String, String>()
            runCatching { watchHistoryDao.getChannelVideoCounts(NAME_LOOKUP_LIMIT) }
                .getOrDefault(emptyList())
                .forEach { if (it.channelName.isNotBlank()) names[it.channelId] = it.channelName }
            runCatching { videoStats.snapshot() }
                .getOrNull()
                ?.months
                ?.toSortedMap()
                ?.values
                ?.forEach { names.putAll(it.channelNames) }
            return names
        }

        private fun ContentVector.traits() =
            TasteTraits(
                pacing = pacing.toFloat(),
                complexity = complexity.toFloat(),
                duration = duration.toFloat(),
                live = isLive.toFloat(),
                breadth = (topics.size / BREADTH_TOPICS).coerceIn(0f, 1f),
            )

        private companion object {
            const val MATURE_INTERACTIONS = 250f
            const val BREADTH_TOPICS = 50f
            const val TOPIC_COUNT = 12
            const val CHANNEL_COUNT = 10
            const val GENRE_COUNT = 8
            const val NAME_LOOKUP_LIMIT = 2_000
        }
    }
