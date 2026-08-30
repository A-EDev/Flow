/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicRecommendationSettingsViewModel
    @Inject
    constructor(
        private val musicBrain: MusicBrainEngine,
    ) : ViewModel() {
        private val _blockedArtists = MutableStateFlow<List<Pair<String, String>>>(emptyList())
        val blockedArtists: StateFlow<List<Pair<String, String>>> = _blockedArtists.asStateFlow()

        fun loadBlockedArtists() {
            viewModelScope.launch {
                _blockedArtists.value = musicBrain.getBlockedArtistsWithNames()
            }
        }

        fun unblock(artistKey: String) {
            viewModelScope.launch {
                musicBrain.unblockArtist(artistKey)
                _blockedArtists.value = musicBrain.getBlockedArtistsWithNames()
            }
        }
    }

/** The "Music recommendations" settings group: endless radio + blocked artists. */
@Composable
fun MusicRecommendationsSection(
    preferences: PlayerPreferences,
    coroutineScope: CoroutineScope,
    viewModel: MusicRecommendationSettingsViewModel = hiltViewModel(),
) {
    val endlessRadioEnabled by preferences.musicEndlessRadioEnabled.collectAsState(initial = true)
    val blockedArtists by viewModel.blockedArtists.collectAsState()
    var showBlockedDialog by remember { mutableStateOf(false) }

    SectionHeader(text = stringResource(R.string.music_prefs_section_title))
    SettingsGroup {
        SettingsSwitchItem(
            icon = Icons.Outlined.Radio,
            title = stringResource(R.string.music_endless_radio_title),
            subtitle = stringResource(R.string.music_endless_radio_desc),
            checked = endlessRadioEnabled,
            onCheckedChange = { enabled ->
                coroutineScope.launch { preferences.setMusicEndlessRadioEnabled(enabled) }
            },
        )
        HorizontalDivider(
            Modifier.padding(start = 56.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        )
        SettingsItem(
            icon = Icons.Outlined.Block,
            title = stringResource(R.string.music_blocked_artists_title),
            subtitle = stringResource(R.string.dont_recommend_artist_desc),
            onClick = {
                viewModel.loadBlockedArtists()
                showBlockedDialog = true
            },
        )
    }

    if (showBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            title = { Text(stringResource(R.string.music_blocked_artists_title)) },
            text = {
                if (blockedArtists.isEmpty()) {
                    Text(
                        text = stringResource(R.string.music_blocked_artists_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn {
                        items(blockedArtists, key = { it.first }) { (key, name) ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { viewModel.unblock(key) }) {
                                    Text(stringResource(R.string.music_unblock))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
}
