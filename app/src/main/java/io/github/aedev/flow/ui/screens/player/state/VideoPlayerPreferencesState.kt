package io.github.aedev.flow.ui.screens.player.state

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.aedev.flow.data.local.DownloadDialogStyle
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.PlayerRelatedCardStyle
import io.github.aedev.flow.player.stream.CaptionTrackResolver
import io.github.aedev.flow.ui.components.videoplayer.subtitle.SubtitleStyle

/**
 * One snapshot of every [PlayerPreferences] flow the video player overlay reads, plus the store
 * itself for the handful of writes the overlay performs. A fresh instance per composition keeps
 * the identity semantics of the individual `by collectAsState()` locals it replaced.
 */
@Stable
internal class VideoPlayerPreferencesState(
    val preferences: PlayerPreferences,
    val brightnessSwipeGesturesEnabled: Boolean,
    val rememberBrightnessEnabled: Boolean,
    val rememberedBrightnessLevel: Float,
    val volumeSwipeGesturesEnabled: Boolean,
    val seekSwipeGesturesEnabled: Boolean,
    val allowVolumeBoost: Boolean,
    val sbSubmitEnabled: Boolean,
    val doubleTapSeekSeconds: Int,
    val longPressPlaybackSpeed: Float,
    val disableShortsPlayer: Boolean,
    val showShortsPlayerPrompt: Boolean,
    val savedSubtitleStyle: SubtitleStyle,
    val rememberPlaybackSpeed: Boolean,
    val ambientModeEnabled: Boolean,
    val adaptivePlayerSizeEnabled: Boolean,
    val groupedQualitySelectorEnabled: Boolean,
    val lockModeEnabled: Boolean,
    val commentsEnabled: Boolean,
    val preferredSubtitleLanguage: String,
    val autoEnableSubtitles: Boolean,
    val commentsPreviewEnabled: Boolean,
    val showRelatedVideos: Boolean,
    val relatedCardStyle: PlayerRelatedCardStyle,
    val deArrowEnabled: Boolean,
    val downloadDialogStyle: DownloadDialogStyle?,
)

@Composable
internal fun rememberVideoPlayerPreferences(context: Context): VideoPlayerPreferencesState {
    val playerPreferences = remember { PlayerPreferences(context) }
    val brightnessSwipeGesturesEnabled by playerPreferences.brightnessSwipeGesturesEnabled.collectAsState(initial = true)
    val rememberBrightnessEnabled by playerPreferences.rememberBrightnessEnabled.collectAsState(initial = false)
    val rememberedBrightnessLevel by playerPreferences.rememberedBrightnessLevel.collectAsState(initial = -1f)
    val volumeSwipeGesturesEnabled by playerPreferences.volumeSwipeGesturesEnabled.collectAsState(initial = true)
    val seekSwipeGesturesEnabled by playerPreferences.seekSwipeGesturesEnabled.collectAsState(initial = true)
    val allowVolumeBoost by playerPreferences.allowVolumeBoost.collectAsState(initial = false)
    val sbSubmitEnabled by playerPreferences.sbSubmitEnabled.collectAsState(initial = false)
    val doubleTapSeekSeconds by playerPreferences.doubleTapSeekSeconds.collectAsState(initial = 10)
    val longPressPlaybackSpeed by playerPreferences.longPressPlaybackSpeed.collectAsState(initial = 2.0f)
    val disableShortsPlayer by playerPreferences.effectiveDisableShortsPlayer.collectAsState(initial = false)
    val showShortsPlayerPrompt by playerPreferences.showShortsPlayerPrompt.collectAsState(initial = true)
    val savedSubtitleStyle by playerPreferences.subtitleStyle.collectAsState(initial = SubtitleStyle())
    val rememberPlaybackSpeed by playerPreferences.rememberPlaybackSpeed.collectAsState(initial = false)
    val ambientModeEnabled by playerPreferences.videoAmbientModeEnabled.collectAsState(initial = false)
    val adaptivePlayerSizeEnabled by playerPreferences.adaptivePlayerSizeEnabled.collectAsState(initial = true)
    val groupedQualitySelectorEnabled by playerPreferences.groupedQualitySelectorEnabled.collectAsState(initial = false)
    val lockModeEnabled by playerPreferences.overlayLockModeEnabled.collectAsState(initial = false)
    val commentsEnabled by playerPreferences.commentsEnabled.collectAsState(initial = true)
    val preferredSubtitleLanguage by playerPreferences.preferredSubtitleLanguage
        .collectAsState(initial = CaptionTrackResolver.NO_PREFERRED_LANGUAGE)
    val autoEnableSubtitles by playerPreferences.autoEnableSubtitles.collectAsState(initial = false)
    val commentsPreviewEnabled by playerPreferences.commentsPreviewEnabled.collectAsState(initial = true)
    val showRelatedVideos by playerPreferences.showRelatedVideos.collectAsState(initial = true)
    val relatedCardStyle by playerPreferences.playerRelatedCardStyle.collectAsState(initial = PlayerRelatedCardStyle.FULL_WIDTH)
    val deArrowEnabled by playerPreferences.deArrowEnabled.collectAsState(initial = false)
    val downloadDialogStyle by playerPreferences.downloadDialogStyle.collectAsState(initial = null)

    return VideoPlayerPreferencesState(
        preferences = playerPreferences,
        brightnessSwipeGesturesEnabled = brightnessSwipeGesturesEnabled,
        rememberBrightnessEnabled = rememberBrightnessEnabled,
        rememberedBrightnessLevel = rememberedBrightnessLevel,
        volumeSwipeGesturesEnabled = volumeSwipeGesturesEnabled,
        seekSwipeGesturesEnabled = seekSwipeGesturesEnabled,
        allowVolumeBoost = allowVolumeBoost,
        sbSubmitEnabled = sbSubmitEnabled,
        doubleTapSeekSeconds = doubleTapSeekSeconds,
        longPressPlaybackSpeed = longPressPlaybackSpeed,
        disableShortsPlayer = disableShortsPlayer,
        showShortsPlayerPrompt = showShortsPlayerPrompt,
        savedSubtitleStyle = savedSubtitleStyle,
        rememberPlaybackSpeed = rememberPlaybackSpeed,
        ambientModeEnabled = ambientModeEnabled,
        adaptivePlayerSizeEnabled = adaptivePlayerSizeEnabled,
        groupedQualitySelectorEnabled = groupedQualitySelectorEnabled,
        lockModeEnabled = lockModeEnabled,
        commentsEnabled = commentsEnabled,
        preferredSubtitleLanguage = preferredSubtitleLanguage,
        autoEnableSubtitles = autoEnableSubtitles,
        commentsPreviewEnabled = commentsPreviewEnabled,
        showRelatedVideos = showRelatedVideos,
        relatedCardStyle = relatedCardStyle,
        deArrowEnabled = deArrowEnabled,
        downloadDialogStyle = downloadDialogStyle,
    )
}
