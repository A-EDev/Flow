package io.github.aedev.flow.ui.screens.player.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.aedev.flow.player.state.SubtitleOption
import io.github.aedev.flow.ui.components.videoplayer.subtitle.SubtitleStyle
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import io.github.aedev.flow.ui.screens.player.state.SubtitleSelection

/**
 * Mirrors the persisted subtitle style onto the screen state and, once per video, turns captions
 * on in the user's preferred language when they asked for automatic captions.
 */
@Composable
internal fun PlayerSubtitleEffects(
    videoId: String,
    screenState: PlayerScreenState,
    savedSubtitleStyle: SubtitleStyle,
    availableSubtitles: List<SubtitleOption>,
    autoEnableSubtitles: Boolean,
    preferredSubtitleLanguage: String,
    rememberSubtitleLanguage: (String) -> Unit,
) {
    LaunchedEffect(savedSubtitleStyle) {
        if (screenState.subtitleStyle != savedSubtitleStyle) {
            screenState.subtitleStyle = savedSubtitleStyle
        }
    }

    var autoEnabledCaptionsFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(videoId, availableSubtitles, autoEnableSubtitles, preferredSubtitleLanguage) {
        if (!autoEnableSubtitles || autoEnabledCaptionsFor == videoId) return@LaunchedEffect
        if (screenState.subtitlesEnabled || availableSubtitles.isEmpty()) return@LaunchedEffect
        val enabled =
            SubtitleSelection.enable(
                screenState = screenState,
                subtitles = availableSubtitles,
                languageTag = preferredSubtitleLanguage,
                rememberLanguage = rememberSubtitleLanguage,
            )
        if (enabled) autoEnabledCaptionsFor = videoId
    }
}
