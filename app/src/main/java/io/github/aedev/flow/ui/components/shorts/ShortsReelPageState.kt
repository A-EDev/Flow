package io.github.aedev.flow.ui.components.shorts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.aedev.flow.data.local.DownloadDialogStyle
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.ShortsPlayerUiMode
import io.github.aedev.flow.data.shorts.ShortAudioTrack
import io.github.aedev.flow.data.shorts.ShortVideoQuality
import io.github.aedev.flow.innertube.models.response.PlayerResponse

internal const val SHORTS_PLAYBACK_LOOP = "loop"
internal const val SHORTS_PLAYBACK_AUTO_NEXT = "auto_next"
internal const val SHORTS_PLAYBACK_AUTO_INTERVAL = "auto_interval"

@Immutable
internal data class ShortsReelActions(
    val onChannelClick: () -> Unit,
    val onCommentsClick: () -> Unit,
    val onDescriptionClick: () -> Unit,
    val onShareClick: () -> Unit,
    val onWantMore: () -> Unit = {},
    val onNotInterested: () -> Unit = {},
    val onVideoEnded: () -> Unit = {},
)

@Immutable
internal data class ShortsReelSettings(
    val playbackMode: String,
    val autoScrollSeconds: Int,
    val style: ShortsOverlayStyle,
    val ambientModeEnabled: Boolean,
    val playbackSpeed: Float,
    val groupedQualitySelectorEnabled: Boolean,
    val customSpeedsEnabled: Boolean,
    val customSpeedPresetsRaw: String,
    val speedSliderEnabled: Boolean,
    val downloadDialogStyle: DownloadDialogStyle,
)

@Stable
internal class ShortsReelPageState {
    var isPlaying by mutableStateOf(false)
    var currentPosition by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var isBuffering by mutableStateOf(false)
    var showPauseIndicator by mutableStateOf(false)
    var showLikeAnimation by mutableStateOf(false)
    var isFastForwarding by mutableStateOf(false)
    var hasStartedPlaying by mutableStateOf(false)
    var isDragging by mutableStateOf(false)
    var dragProgress by mutableFloatStateOf(0f)
    var showShortsOptionsSheet by mutableStateOf(false)
    var showAudioTrackSheet by mutableStateOf(false)
    var showQualitySheet by mutableStateOf(false)
    var showSpeedSheet by mutableStateOf(false)
    var availableAudioTracks by mutableStateOf<List<ShortAudioTrack>>(emptyList())
    var availableQualities by mutableStateOf<List<ShortVideoQuality>>(emptyList())
    var selectedAudioIndex by mutableIntStateOf(0)
    var selectedQualityHeight by mutableIntStateOf(-1)
    var selectedQualityUrl by mutableStateOf<String?>(null)
    var isLoadingStreams by mutableStateOf(false)
    var showDownloadDialog by mutableStateOf(false)
    var currentStreamSizes by mutableStateOf<Map<String, Long>>(emptyMap())
    var currentInnerTubeVideoFormats by mutableStateOf<List<PlayerResponse.StreamingData.Format>>(emptyList())
    var currentInnerTubeAudioFormats by mutableStateOf<List<PlayerResponse.StreamingData.Format>>(emptyList())

    val anySheetOpen: Boolean
        get() = showShortsOptionsSheet || showSpeedSheet || showAudioTrackSheet || showQualitySheet || isLoadingStreams
}

@Stable
internal class ShortsReelSessionState {
    var hasRecordedWatched by mutableStateOf(false)
    var hasTouchedHistory by mutableStateOf(false)
    var lastProgressSavedAt by mutableLongStateOf(0L)
    var showOnDemandControls by mutableStateOf(false)
}

@Stable
internal class ShortsReelAutoAdvanceState {
    var hasAutoAdvanced by mutableStateOf(false)

    /** An advance that came due while a sheet was open, held back until the sheet is gone. */
    var deferredWhileSheetOpen by mutableStateOf(false)
}

@Composable
internal fun rememberShortsReelSettings(playerPreferences: PlayerPreferences): ShortsReelSettings {
    val playbackMode by playerPreferences.shortsPlaybackMode.collectAsState(initial = SHORTS_PLAYBACK_LOOP)
    val autoScrollSeconds by playerPreferences.shortsAutoScrollSeconds.collectAsState(initial = 10)
    val uiMode by playerPreferences.shortsPlayerUiMode.collectAsState(initial = ShortsPlayerUiMode.DEFAULT)
    val ambientModeEnabled by playerPreferences.videoAmbientModeEnabled.collectAsState(initial = false)
    val playbackSpeed by playerPreferences.shortsPlaybackSpeed.collectAsState(initial = 1f)
    val groupedQualitySelectorEnabled by playerPreferences.groupedQualitySelectorEnabled.collectAsState(initial = false)
    val customSpeedsEnabled by playerPreferences.customSpeedsEnabled.collectAsState(initial = false)
    val customSpeedPresetsRaw by playerPreferences.customSpeedPresets.collectAsState(initial = "")
    val speedSliderEnabled by playerPreferences.speedSliderEnabled.collectAsState(initial = false)
    val downloadDialogStyle by playerPreferences.downloadDialogStyle.collectAsState(initial = DownloadDialogStyle.FULL)

    return ShortsReelSettings(
        playbackMode = playbackMode,
        autoScrollSeconds = autoScrollSeconds,
        style = ShortsOverlayStyle.from(uiMode),
        ambientModeEnabled = ambientModeEnabled,
        playbackSpeed = playbackSpeed,
        groupedQualitySelectorEnabled = groupedQualitySelectorEnabled,
        customSpeedsEnabled = customSpeedsEnabled,
        customSpeedPresetsRaw = customSpeedPresetsRaw,
        speedSliderEnabled = speedSliderEnabled,
        downloadDialogStyle = downloadDialogStyle,
    )
}
