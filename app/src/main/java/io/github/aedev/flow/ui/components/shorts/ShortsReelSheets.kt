package io.github.aedev.flow.ui.components.shorts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.aedev.flow.data.local.DownloadDialogStyle
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.ShortVideo
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.shorts.ShortsPlayerPool
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.ui.components.shared.MediaDownloadDialog
import io.github.aedev.flow.ui.components.shared.MediaDownloadDialogCompact
import io.github.aedev.flow.ui.screens.shorts.ShortsViewModel
import kotlinx.coroutines.launch

/** The sheets and dialogs a reel can open, and the stream lookups each of them waits on. */
@Composable
internal fun ShortsReelSheets(
    short: ShortVideo,
    video: Video,
    pageIndex: Int,
    pageState: ShortsReelPageState,
    settings: ShortsReelSettings,
    sheetInsets: ShortsSheetInsetState,
    playerPool: ShortsPlayerPool,
    viewModel: ShortsViewModel,
    playerPreferences: PlayerPreferences,
    actions: ShortsReelActions,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    fun withStreams(block: suspend () -> Unit) {
        if (pageState.isLoadingStreams) return
        pageState.isLoadingStreams = true
        scope.launch {
            try {
                block()
            } finally {
                pageState.isLoadingStreams = false
            }
        }
    }

    if (pageState.showShortsOptionsSheet) {
        ShortsOptionsSheet(
            isLoadingStreams = pageState.isLoadingStreams,
            ambientModeEnabled = settings.ambientModeEnabled,
            currentSpeed = settings.playbackSpeed,
            onAmbientModeToggle = { enabled -> scope.launch { playerPreferences.setVideoAmbientModeEnabled(enabled) } },
            onWantMore = {
                pageState.showShortsOptionsSheet = false
                actions.onWantMore()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onNotInterested = {
                pageState.showShortsOptionsSheet = false
                actions.onNotInterested()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onDownloadClick = {
                pageState.showShortsOptionsSheet = false
                withStreams {
                    val (videoFormats, audioFormats) = viewModel.downloadFormats(short.id)
                    pageState.currentInnerTubeVideoFormats = videoFormats
                    pageState.currentInnerTubeAudioFormats = audioFormats
                    if (videoFormats.isNotEmpty()) {
                        pageState.currentStreamSizes = viewModel.streamSizesFor(short.id, videoFormats, audioFormats)
                        pageState.showDownloadDialog = true
                    }
                }
            },
            onAudioTrackClick = {
                pageState.showShortsOptionsSheet = false
                withStreams {
                    pageState.availableAudioTracks = viewModel.availableAudioTracks(short.id)
                    if (pageState.availableAudioTracks.isNotEmpty()) pageState.showAudioTrackSheet = true
                }
            },
            onQualityClick = {
                pageState.showShortsOptionsSheet = false
                withStreams {
                    pageState.availableQualities = viewModel.availableQualities(short.id)
                    val activeFormat = playerPool.ownedPlayer(pageIndex)?.videoFormat
                    val activeQuality =
                        findActiveShortQuality(
                            qualities = pageState.availableQualities,
                            currentVideoUrl = playerPool.getVideoUrlForIndex(pageIndex),
                            activeVideoWidth = activeFormat?.width ?: 0,
                            activeVideoHeight = activeFormat?.height ?: 0,
                            activeCodecKey = activeFormat?.let { VideoCodecUtils.codecKeyFromMimeType(it.fullMimeType()) },
                        )
                    pageState.selectedQualityHeight = activeQuality?.heightClass ?: -1
                    pageState.selectedQualityUrl = activeQuality?.videoUrl
                    if (pageState.availableQualities.isNotEmpty()) pageState.showQualitySheet = true
                }
            },
            onSpeedClick = {
                pageState.showShortsOptionsSheet = false
                pageState.showSpeedSheet = true
            },
            sheetInsets = sheetInsets,
            onDismiss = { pageState.showShortsOptionsSheet = false },
        )
    }

    if (pageState.showSpeedSheet) {
        ShortsSpeedSheet(
            currentSpeed = settings.playbackSpeed,
            speedSliderEnabled = settings.speedSliderEnabled,
            customSpeedsEnabled = settings.customSpeedsEnabled,
            customSpeedPresetsRaw = settings.customSpeedPresetsRaw,
            onSpeedSelected = playerPool::setBasePlaybackSpeed,
            onSpeedSelectionFinished = { speed -> scope.launch { playerPreferences.setShortsPlaybackSpeed(speed) } },
            sheetInsets = sheetInsets,
            onDismiss = { pageState.showSpeedSheet = false },
        )
    }

    if (pageState.showAudioTrackSheet && pageState.availableAudioTracks.isNotEmpty()) {
        ShortsAudioTrackSheet(
            audioTracks = pageState.availableAudioTracks,
            selectedIndex = pageState.selectedAudioIndex,
            onTrackSelected = { index ->
                val track = pageState.availableAudioTracks[index]
                playerPool.reloadWithAudioUrl(pageIndex, short.id, track.url, track.dashManifest)
                pageState.selectedAudioIndex = index
                pageState.showAudioTrackSheet = false
            },
            sheetInsets = sheetInsets,
            onDismiss = { pageState.showAudioTrackSheet = false },
        )
    }

    if (pageState.showQualitySheet && pageState.availableQualities.isNotEmpty()) {
        ShortsQualitySheet(
            qualities = pageState.availableQualities,
            selectedHeight = pageState.selectedQualityHeight.takeIf { it >= 0 },
            selectedVideoUrl = pageState.selectedQualityUrl,
            groupedByResolution = settings.groupedQualitySelectorEnabled,
            onQualitySelected = { quality ->
                playerPool.reloadWithVideoUrl(pageIndex, short.id, quality.videoUrl, quality.dashManifest)
                pageState.selectedQualityHeight = quality.heightClass
                pageState.selectedQualityUrl = quality.videoUrl
                pageState.showQualitySheet = false
            },
            sheetInsets = sheetInsets,
            onDismiss = { pageState.showQualitySheet = false },
        )
    }

    if (pageState.showDownloadDialog && pageState.currentInnerTubeVideoFormats.isNotEmpty()) {
        if (settings.downloadDialogStyle == DownloadDialogStyle.COMPACT) {
            MediaDownloadDialogCompact(
                streamInfo = null,
                streamSizes = pageState.currentStreamSizes,
                innerTubeVideoFormats = pageState.currentInnerTubeVideoFormats,
                innerTubeAudioFormats = pageState.currentInnerTubeAudioFormats,
                video = video,
                onDismiss = { pageState.showDownloadDialog = false },
            )
        } else {
            MediaDownloadDialog(
                streamInfo = null,
                streamSizes = pageState.currentStreamSizes,
                innerTubeVideoFormats = pageState.currentInnerTubeVideoFormats,
                innerTubeAudioFormats = pageState.currentInnerTubeAudioFormats,
                video = video,
                onDismiss = { pageState.showDownloadDialog = false },
            )
        }
    }
}

/** The MIME string the codec helpers expect, rebuilt from the halves Media3 keeps apart. */
private fun androidx.media3.common.Format.fullMimeType(): String =
    buildString {
        append(sampleMimeType.orEmpty())
        codecs?.takeIf { it.isNotBlank() }?.let { codecs ->
            append("; codecs=\"")
            append(codecs)
            append('"')
        }
    }
