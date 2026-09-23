package io.github.aedev.flow.ui.screens.settings.taste

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingsListScope
import io.github.aedev.flow.ui.components.settings.info
import io.github.aedev.flow.ui.components.settings.nav
import io.github.aedev.flow.ui.screens.settings.index.TasteIndex

private const val STRONG_CHANNEL = 0.65f
private const val BALANCED_CHANNEL = 0.4f

internal class TasteActions(
    val onTopicPreferred: (String, Boolean) -> Unit,
    val onBlockTopic: (String) -> Unit,
    val onBlockChannel: (String) -> Unit,
    val onOpenHidden: () -> Unit,
    val onOpenRecap: () -> Unit,
    val onExportVideo: () -> Unit,
    val onImportVideo: () -> Unit,
    val onResetVideo: () -> Unit,
    val onExportMusic: () -> Unit,
    val onImportMusic: () -> Unit,
    val onResetMusic: () -> Unit,
)

internal fun SettingsListScope.tasteContent(
    state: TasteState,
    hiddenLabel: String,
    noQueriesLabel: String,
    actions: TasteActions,
) {
    state.persona?.let { persona -> item("taste.persona") { PersonaHeroCard(persona, state.maturity) } }
    if (state.profile != null && state.now != null) traits(state.profile, state.now)
    interests(state.topics, actions)
    channels(state.channels, actions)
    state.music?.let(::music)
    group(key = "taste.more") {
        nav(TasteIndex.recap, onClick = actions.onOpenRecap, icon = Icons.Outlined.Insights)
        nav(TasteIndex.hidden, onClick = actions.onOpenHidden, value = hiddenLabel, icon = Icons.Outlined.VisibilityOff)
    }
    state.engine?.let { engineDetails(it, noQueriesLabel) }
    data(actions)
}

/** What the video engine currently remembers, read without changing any of it. */
private fun SettingsListScope.engineDetails(
    details: EngineDetails,
    noQueries: String,
) {
    group(key = "taste.engine", header = R.string.diagnostics_engine_header) {
        info(TasteIndex.engineInteractions, details.interactions.toString())
        info(TasteIndex.engineTopics, details.topics.toString())
        info(TasteIndex.engineChannels, details.channels.toString())
        info(TasteIndex.engineHistory, details.history.toString())
        info(TasteIndex.engineFeedMemory, details.feedMemory.toString())
        info(TasteIndex.engineSuppressed, details.suppressed.toString())
        info(TasteIndex.engineShortsSeen, details.shortsSeen.toString())
        info(TasteIndex.engineQueries, details.recentQueries.joinToString("\n").ifEmpty { noQueries })
    }
}

private fun SettingsListScope.traits(
    profile: TasteTraits,
    now: TasteTraits,
) {
    header("taste.shape.header", R.string.taste_shape_header)
    item(TasteIndex.shape.key) { TasteShapeCard(profile, now) }
    val rows =
        listOf(
            R.string.taste_pacing to (profile.pacing to now.pacing),
            R.string.taste_complexity to (profile.complexity to now.complexity),
            R.string.taste_duration to (profile.duration to now.duration),
            R.string.taste_live_affinity to (profile.live to now.live),
            R.string.taste_topic_breadth to (profile.breadth to now.breadth),
        )
    group(key = "taste.traits") {
        rows.forEach { (title, values) ->
            row("taste.trait.$title") { shape ->
                TasteMeterRow(
                    title = stringResource(title),
                    value = values.first,
                    shape = shape,
                    detail = stringResource(R.string.taste_trait_now, values.second.percentLabel()),
                    trailing = { Text(values.first.percentLabel()) },
                )
            }
        }
    }
}

private fun SettingsListScope.interests(
    topics: List<TasteTopic>,
    actions: TasteActions,
) {
    if (topics.isEmpty()) {
        header(TasteIndex.interests.key, R.string.taste_interests_header)
        item("taste.interests.empty") { TasteNote(stringResource(R.string.taste_interests_empty)) }
        return
    }
    group(key = TasteIndex.interests.key, header = R.string.taste_interests_header, footer = R.string.taste_interests_footer) {
        topics.forEach { topic ->
            row("taste.topic.${topic.id}") { shape ->
                val label = topic.id.readableTopic()
                TasteMeterRow(
                    title = label,
                    value = topic.share,
                    shape = shape,
                    detail = if (topic.preferred) stringResource(R.string.taste_topic_boosted) else null,
                    trailing = {
                        ItemMenu(
                            label = label,
                            options =
                                listOf(
                                    stringResource(if (topic.preferred) R.string.taste_topic_unboost else R.string.taste_topic_boost) to {
                                        actions.onTopicPreferred(topic.id, !topic.preferred)
                                    },
                                    stringResource(R.string.taste_topic_block) to { actions.onBlockTopic(topic.id) },
                                ),
                        )
                    },
                )
            }
        }
    }
}

private fun SettingsListScope.channels(
    channels: List<TasteChannel>,
    actions: TasteActions,
) {
    group(key = TasteIndex.channels.key, header = R.string.taste_channels_header) {
        channels.forEach { channel ->
            row("taste.channel.${channel.id}") { shape ->
                TasteMeterRow(
                    title = channel.name,
                    value = channel.score,
                    shape = shape,
                    detail = stringResource(channelStrength(channel.score)),
                    trailing = {
                        ItemMenu(
                            label = channel.name,
                            options = listOf(stringResource(R.string.taste_channel_block) to { actions.onBlockChannel(channel.id) }),
                        )
                    },
                )
            }
        }
    }
}

private fun channelStrength(score: Float): Int =
    when {
        score >= STRONG_CHANNEL -> R.string.channel_affinity_strong
        score >= BALANCED_CHANNEL -> R.string.channel_affinity_balanced
        else -> R.string.channel_affinity_cooling
    }

private fun SettingsListScope.music(music: MusicTaste) {
    group(key = TasteIndex.music.key, header = R.string.taste_music_header) {
        row("taste.music.stage") { shape ->
            TasteMeterRow(
                title = stringResource(R.string.taste_music_stage),
                value = musicStageProgress(music.maturity),
                shape = shape,
                detail = stringResource(musicStageLabel(music.maturity)),
            )
        }
        row(TasteIndex.appetite.key) { shape ->
            TasteMeterRow(
                title = stringResource(R.string.music_discovery_appetite),
                value = music.appetite,
                shape = shape,
                detail = stringResource(R.string.music_discovery_appetite_detail),
                trailing = { Text(music.appetite.percentLabel()) },
            )
        }
    }
    group(key = "taste.genres", header = R.string.music_genre_affinity_title) {
        music.genres.forEach { (genre, share) ->
            row("taste.genre.$genre") { shape -> TasteMeterRow(title = genre.readableTopic(), value = share, shape = shape) }
        }
    }
}

private fun musicStageLabel(maturity: String): Int =
    when (maturity) {
        "mature" -> R.string.music_persona_maturity_mature
        "warming" -> R.string.music_persona_maturity_warming
        else -> R.string.music_persona_maturity_cold
    }

private fun musicStageProgress(maturity: String): Float =
    when (maturity) {
        "mature" -> 1f
        "warming" -> 0.5f
        else -> 0.15f
    }

private fun SettingsListScope.data(actions: TasteActions) {
    group(key = "taste.data", header = R.string.taste_data_header) {
        nav(TasteIndex.exportVideo, onClick = actions.onExportVideo, showChevron = false, icon = Icons.Outlined.Upload)
        nav(TasteIndex.importVideo, onClick = actions.onImportVideo, showChevron = false, icon = Icons.Outlined.Download)
        nav(TasteIndex.resetVideo, onClick = actions.onResetVideo, showChevron = false, icon = Icons.Outlined.RestartAlt)
        nav(TasteIndex.exportMusic, onClick = actions.onExportMusic, showChevron = false, icon = Icons.Outlined.Upload)
        nav(TasteIndex.importMusic, onClick = actions.onImportMusic, showChevron = false, icon = Icons.Outlined.Download)
        nav(TasteIndex.resetMusic, onClick = actions.onResetMusic, showChevron = false, icon = Icons.Outlined.RestartAlt)
    }
}

@Composable
private fun ItemMenu(
    label: String,
    options: List<Pair<String, () -> Unit>>,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.taste_item_actions, label))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, action) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        open = false
                        action()
                    },
                )
            }
        }
    }
}

@Composable
internal fun hiddenCountLabel(count: Int): String =
    if (count == 0) stringResource(R.string.taste_hidden_none) else pluralStringResource(R.plurals.taste_hidden_count, count, count)
