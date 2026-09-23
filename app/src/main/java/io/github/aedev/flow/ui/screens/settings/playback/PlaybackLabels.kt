package io.github.aedev.flow.ui.screens.settings.playback

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.player.stream.CaptionTrackResolver
import io.github.aedev.flow.ui.components.shared.playbackSpeedLabel
import io.github.aedev.flow.utils.LanguageCatalog

@Composable
internal fun countdownLabel(seconds: Int): String =
    if (seconds <= 0) {
        stringResource(R.string.player_settings_autoplay_countdown_none)
    } else {
        pluralStringResource(R.plurals.player_settings_autoplay_countdown_seconds_template, seconds, seconds)
    }

@Composable
internal fun longPressSpeedLabel(speed: Float): String =
    if (speed <= 0f) stringResource(R.string.player_appearance_long_press_speed_disabled) else playbackSpeedLabel(speed)

@Composable
internal fun audioLanguageLabel(code: String): String =
    if (code == PlaybackSettingsViewModel.ORIGINAL_AUDIO) {
        stringResource(R.string.player_settings_audio_original)
    } else {
        LanguageCatalog.displayName(code)
    }

@Composable
internal fun subtitleLanguageLabel(code: String): String =
    if (code == CaptionTrackResolver.NO_PREFERRED_LANGUAGE) {
        stringResource(R.string.player_settings_subtitle_language_none)
    } else {
        LanguageCatalog.displayName(code)
    }

@Composable
internal fun shortsModeLabel(
    mode: String,
    seconds: Int,
): String =
    when (mode) {
        ShortsPlaybackModes.AUTO_NEXT -> {
            stringResource(R.string.player_settings_shorts_playback_mode_auto_next)
        }

        ShortsPlaybackModes.AUTO_INTERVAL -> {
            pluralStringResource(R.plurals.player_settings_shorts_playback_mode_auto_interval_summary, seconds, seconds)
        }

        else -> {
            stringResource(R.string.player_settings_shorts_playback_mode_loop)
        }
    }
