package io.github.aedev.flow.ui.tv.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.VideoCodec
import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.ui.tv.components.TvSectionHeader
import io.github.aedev.flow.ui.tv.components.TvSelectionRow
import io.github.aedev.flow.ui.tv.components.TvToggleRow
import kotlinx.coroutines.launch

@Composable
fun TvPlaybackSettingsPane(
    playerPreferences: PlayerPreferences,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val backgroundPlay by playerPreferences.backgroundPlayEnabled.collectAsStateWithLifecycle(initialValue = false)
    val autoplay by playerPreferences.autoplayEnabled.collectAsStateWithLifecycle(initialValue = true)
    val skipSilence by playerPreferences.skipSilenceEnabled.collectAsStateWithLifecycle(initialValue = false)
    val stableVolume by playerPreferences.stableVolumeEnabled.collectAsStateWithLifecycle(initialValue = false)
    val subtitles by playerPreferences.subtitlesEnabled.collectAsStateWithLifecycle(initialValue = false)
    val ambientMode by playerPreferences.videoAmbientModeEnabled.collectAsStateWithLifecycle(initialValue = false)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "background-play") {
            TvToggleRow(
                label = stringResource(R.string.player_settings_background_play),
                supportingText = stringResource(R.string.player_settings_background_play_subtitle),
                checked = backgroundPlay,
                onCheckedChange = { scope.launch { playerPreferences.setBackgroundPlayEnabled(it) } },
            )
        }
        item(key = "autoplay") {
            TvToggleRow(
                label = stringResource(R.string.player_settings_autoplay),
                supportingText = stringResource(R.string.player_settings_autoplay_subtitle),
                checked = autoplay,
                onCheckedChange = { scope.launch { playerPreferences.setAutoplayEnabled(it) } },
            )
        }
        item(key = "subtitles") {
            TvToggleRow(
                label = stringResource(R.string.filter_subtitles),
                checked = subtitles,
                onCheckedChange = { scope.launch { playerPreferences.setSubtitlesEnabled(it) } },
            )
        }
        item(key = "skip-silence") {
            TvToggleRow(
                label = stringResource(R.string.player_settings_skip_silence),
                supportingText = stringResource(R.string.player_settings_skip_silence_subtitle),
                checked = skipSilence,
                onCheckedChange = { scope.launch { playerPreferences.setSkipSilenceEnabled(it) } },
            )
        }
        item(key = "stable-volume") {
            TvToggleRow(
                label = stringResource(R.string.player_settings_stable_voice),
                supportingText = stringResource(R.string.player_settings_stable_voice_subtitle),
                checked = stableVolume,
                onCheckedChange = { scope.launch { playerPreferences.setStableVolumeEnabled(it) } },
            )
        }
        item(key = "ambient") {
            TvToggleRow(
                label = stringResource(R.string.player_settings_ambient_mode),
                supportingText = stringResource(R.string.player_settings_ambient_mode_subtitle),
                checked = ambientMode,
                onCheckedChange = { scope.launch { playerPreferences.setVideoAmbientModeEnabled(it) } },
            )
        }
    }
}

@Composable
fun TvQualitySettingsPane(
    playerPreferences: PlayerPreferences,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val wifiQuality by playerPreferences.defaultQualityWifi.collectAsStateWithLifecycle(initialValue = VideoQuality.AUTO)
    val cellularQuality by playerPreferences.defaultQualityCellular.collectAsStateWithLifecycle(initialValue = VideoQuality.AUTO)
    val codec by playerPreferences.defaultVideoCodec.collectAsStateWithLifecycle(initialValue = VideoCodec.AUTO)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "wifi-header") {
            TvSectionHeader(title = stringResource(R.string.tv_settings_wifi_quality))
        }
        items(count = VideoQuality.entries.size, key = { "wifi:${VideoQuality.entries[it].name}" }) { index ->
            val quality = VideoQuality.entries[index]
            TvSelectionRow(
                label = quality.label,
                selected = quality == wifiQuality,
                onClick = { scope.launch { playerPreferences.setDefaultQualityWifi(quality) } },
            )
        }
        item(key = "cellular-header") {
            TvSectionHeader(title = stringResource(R.string.tv_settings_cellular_quality))
        }
        items(count = VideoQuality.entries.size, key = { "cell:${VideoQuality.entries[it].name}" }) { index ->
            val quality = VideoQuality.entries[index]
            TvSelectionRow(
                label = quality.label,
                selected = quality == cellularQuality,
                onClick = { scope.launch { playerPreferences.setDefaultQualityCellular(quality) } },
            )
        }
        item(key = "codec-header") {
            TvSectionHeader(title = stringResource(R.string.tv_settings_codec))
        }
        items(count = VideoCodec.entries.size, key = { "codec:${VideoCodec.entries[it].name}" }) { index ->
            val option = VideoCodec.entries[index]
            TvSelectionRow(
                label = option.label,
                selected = option == codec,
                onClick = { scope.launch { playerPreferences.setDefaultVideoCodec(option) } },
            )
        }
    }
}

@Composable
fun TvContentSettingsPane(
    playerPreferences: PlayerPreferences,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val sponsorBlock by playerPreferences.sponsorBlockEnabled.collectAsStateWithLifecycle(initialValue = false)
    val deArrow by playerPreferences.deArrowEnabled.collectAsStateWithLifecycle(initialValue = false)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "sponsor-block") {
            TvToggleRow(
                label = stringResource(R.string.player_settings_sponsorblock),
                supportingText = stringResource(R.string.player_settings_sponsorblock_subtitle),
                checked = sponsorBlock,
                onCheckedChange = { scope.launch { playerPreferences.setSponsorBlockEnabled(it) } },
            )
        }
        item(key = "dearrow") {
            TvToggleRow(
                label = stringResource(R.string.player_settings_dearrow),
                supportingText = stringResource(R.string.player_settings_dearrow_subtitle),
                checked = deArrow,
                onCheckedChange = { scope.launch { playerPreferences.setDeArrowEnabled(it) } },
            )
        }
    }
}
