/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.screens.personality

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import io.github.aedev.flow.data.recommendation.music.MusicTasteProfile
import io.github.aedev.flow.data.recommendation.music.MusicTimeBucket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Feeds the Control Center's music cards: taste profile + blocked artist management. */
@HiltViewModel
class MusicPersonaViewModel
    @Inject
    constructor(
        private val musicBrain: MusicBrainEngine,
    ) : ViewModel() {
        private val _profile = MutableStateFlow<MusicTasteProfile?>(null)
        val profile: StateFlow<MusicTasteProfile?> = _profile.asStateFlow()

        private val _blockedArtists = MutableStateFlow<List<Pair<String, String>>>(emptyList())
        val blockedArtists: StateFlow<List<Pair<String, String>>> = _blockedArtists.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            viewModelScope.launch {
                _profile.value = musicBrain.tasteProfile()
                _blockedArtists.value = musicBrain.getBlockedArtistsWithNames()
            }
        }

        fun unblock(artistKey: String) {
            viewModelScope.launch {
                musicBrain.unblockArtist(artistKey)
                refresh()
            }
        }
    }

@Composable
internal fun MusicTasteOverviewSection(profile: MusicTasteProfile) {
    DashboardSection(
        title = stringResource(R.string.music_persona_overview_title),
        subtitle = stringResource(R.string.music_persona_overview_subtitle),
        icon = Icons.Outlined.LibraryMusic,
    ) {
        if (profile.totalPlays == 0) {
            EmptyPanelMessage(stringResource(R.string.music_persona_empty))
            return@DashboardSection
        }
        StatusChip(
            label = maturityLabel(profile.maturity),
            color =
                when (profile.maturity) {
                    "mature" -> MaterialTheme.colorScheme.primary
                    "warming" -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.tertiary
                },
        )
        MetricGrid(
            metrics =
                listOf(
                    DashboardMetric(
                        label = stringResource(R.string.music_metric_total_plays),
                        value = profile.totalPlays.toString(),
                        detail = stringResource(R.string.music_metric_total_plays_detail),
                        icon = Icons.Outlined.MusicNote,
                    ),
                    DashboardMetric(
                        label = stringResource(R.string.music_metric_artists),
                        value = profile.distinctArtists.toString(),
                        detail = stringResource(R.string.music_metric_artists_detail),
                        icon = Icons.Outlined.Person,
                    ),
                    DashboardMetric(
                        label = stringResource(R.string.music_metric_tracks),
                        value = profile.trackedTracks.toString(),
                        detail = stringResource(R.string.music_metric_tracks_detail),
                        icon = Icons.Outlined.LibraryMusic,
                    ),
                    DashboardMetric(
                        label = stringResource(R.string.music_metric_on_repeat),
                        value = profile.onRepeatCount.toString(),
                        detail = stringResource(R.string.music_metric_on_repeat_detail),
                        icon = Icons.Outlined.Repeat,
                    ),
                ),
        )
        MetricBar(
            label = stringResource(R.string.music_discovery_appetite),
            value = profile.discoveryAppetite,
            detail = stringResource(R.string.music_discovery_appetite_detail),
        )
    }
}

@Composable
internal fun MusicTopArtistsSection(profile: MusicTasteProfile) {
    if (profile.topArtists.isEmpty()) return
    DashboardSection(
        title = stringResource(R.string.music_top_artists_title),
        subtitle = stringResource(R.string.music_top_artists_subtitle),
        icon = Icons.Outlined.Person,
    ) {
        profile.topArtists.forEach { artist ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (artist.liked) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.music_top_artist_plays, artist.plays),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = artist.score.percentLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun MusicListeningPatternsSection(profile: MusicTasteProfile) {
    val maxPlays = profile.timeOfDay.maxOfOrNull { it.plays } ?: 0
    if (maxPlays == 0) return
    DashboardSection(
        title = stringResource(R.string.music_listening_patterns_title),
        subtitle = stringResource(R.string.music_listening_patterns_subtitle),
        icon = Icons.Outlined.Schedule,
    ) {
        profile.timeOfDay.forEach { bucket ->
            CompactProgressRow(
                title = bucketLabel(bucket.bucket),
                subtitle = stringResource(R.string.music_bucket_plays, bucket.plays),
                value = bucket.plays.toDouble() / maxPlays,
            )
        }
    }
}

@Composable
internal fun MusicGenreAffinitySection(profile: MusicTasteProfile) {
    val maxAffinity = profile.topGenres.maxOfOrNull { it.second } ?: 0.0
    if (maxAffinity <= 0.0) return
    DashboardSection(
        title = stringResource(R.string.music_genre_affinity_title),
        subtitle = stringResource(R.string.music_genre_affinity_subtitle),
        icon = Icons.Outlined.LibraryMusic,
    ) {
        profile.topGenres.take(8).forEach { (genre, affinity) ->
            CompactProgressRow(
                title = genre.replaceFirstChar { it.titlecase() },
                subtitle = affinity.percentLabel(),
                value = affinity / maxAffinity,
            )
        }
    }
}

@Composable
internal fun MusicBlockedArtistsSection(
    blockedArtists: List<Pair<String, String>>,
    onUnblock: (String) -> Unit,
) {
    DashboardSection(
        title = stringResource(R.string.music_blocked_artists_title),
        subtitle = stringResource(R.string.music_blocked_artists_subtitle),
        icon = Icons.Outlined.Block,
    ) {
        blockedArtists.forEach { (key, name) ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onUnblock(key) }) {
                    Text(stringResource(R.string.music_unblock))
                }
            }
        }
    }
}

@Composable
private fun maturityLabel(maturity: String): String =
    when (maturity) {
        "mature" -> stringResource(R.string.music_persona_maturity_mature)
        "warming" -> stringResource(R.string.music_persona_maturity_warming)
        else -> stringResource(R.string.music_persona_maturity_cold)
    }

@Composable
private fun bucketLabel(bucket: MusicTimeBucket): String =
    when (bucket) {
        MusicTimeBucket.WEEKDAY_MORNING -> stringResource(R.string.music_bucket_weekday_morning)
        MusicTimeBucket.WEEKDAY_AFTERNOON -> stringResource(R.string.music_bucket_weekday_afternoon)
        MusicTimeBucket.WEEKDAY_EVENING -> stringResource(R.string.music_bucket_weekday_evening)
        MusicTimeBucket.WEEKDAY_NIGHT -> stringResource(R.string.music_bucket_weekday_night)
        MusicTimeBucket.WEEKEND_MORNING -> stringResource(R.string.music_bucket_weekend_morning)
        MusicTimeBucket.WEEKEND_AFTERNOON -> stringResource(R.string.music_bucket_weekend_afternoon)
        MusicTimeBucket.WEEKEND_EVENING -> stringResource(R.string.music_bucket_weekend_evening)
        MusicTimeBucket.WEEKEND_NIGHT -> stringResource(R.string.music_bucket_weekend_night)
    }
