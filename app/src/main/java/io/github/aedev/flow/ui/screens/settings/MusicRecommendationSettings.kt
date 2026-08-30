/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The "Music recommendations" settings group. Playback-behavior toggles only —
 * engine insight and blocked-artist management live in the Flow Control Center.
 */
@Composable
fun MusicRecommendationsSection(
    preferences: PlayerPreferences,
    coroutineScope: CoroutineScope,
) {
    val endlessRadioEnabled by preferences.musicEndlessRadioEnabled.collectAsState(initial = true)

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
    }
}
