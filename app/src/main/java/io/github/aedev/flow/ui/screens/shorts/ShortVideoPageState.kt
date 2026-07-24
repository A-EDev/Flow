package io.github.aedev.flow.ui.screens.shorts

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
import io.github.aedev.flow.data.shorts.ShortVideoQuality
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo

@Immutable
internal data class ShortVideoPageActions(
    val onChannelClick: () -> Unit,
    val onCommentsClick: () -> Unit,
    val onDescriptionClick: () -> Unit,
    val onShareClick: () -> Unit,
    val onWantMore: () -> Unit = {},
    val onNotInterested: () -> Unit = {},
    val onVideoEnded: () -> Unit = {}
)

@Immutable
internal data class ShortVideoPlayerSettings(
    val playbackMode: String,
    val autoScrollSeconds: Int,
    val uiMode: ShortsPlayerUiMode,
    val ambientModeEnabled: Boolean,
    val playbackSpeed: Float,
    val groupedQualitySelectorEnabled: Boolean,
    val customSpeedsEnabled: Boolean,
    val customSpeedPresetsRaw: String,
    val speedSliderEnabled: Boolean,
    val downloadDialogStyle: DownloadDialogStyle
)

@Stable
internal class ShortVideoPageState {
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
    var availableAudioStreams by mutableStateOf<List<AudioStream>>(emptyList())
    var availableQualities by mutableStateOf<List<ShortVideoQuality>>(emptyList())
    var selectedAudioIndex by mutableIntStateOf(0)
    var selectedQualityHeight by mutableIntStateOf(-1)
    var selectedQualityUrl by mutableStateOf<String?>(null)
    var isLoadingStreams by mutableStateOf(false)
    var showDownloadDialog by mutableStateOf(false)
    var currentStreamInfo by mutableStateOf<StreamInfo?>(null)
    var currentStreamSizes by mutableStateOf<Map<String, Long>>(emptyMap())
    var currentInnerTubeVideoFormats by mutableStateOf<List<PlayerResponse.StreamingData.Format>>(emptyList())
    var currentInnerTubeAudioFormats by mutableStateOf<List<PlayerResponse.StreamingData.Format>>(emptyList())
}

@Stable
internal class ShortVideoSessionState {
    var hasRecordedWatched by mutableStateOf(false)
    var hasTouchedHistory by mutableStateOf(false)
    var lastProgressSavedAt by mutableLongStateOf(0L)
    var showImpressiveControls by mutableStateOf(false)
}

@Stable
internal class ShortVideoAutoAdvanceState {
    var hasAutoAdvanced by mutableStateOf(false)
}

@Composable
internal fun rememberShortVideoPlayerSettings(
    playerPreferences: PlayerPreferences
): ShortVideoPlayerSettings {
    val playbackMode by playerPreferences.shortsPlaybackMode.collectAsState(initial = "loop")
    val autoScrollSeconds by playerPreferences.shortsAutoScrollSeconds.collectAsState(initial = 10)
    val uiMode by playerPreferences.shortsPlayerUiMode.collectAsState(initial = ShortsPlayerUiMode.DEFAULT)
    val ambientModeEnabled by playerPreferences.videoAmbientModeEnabled.collectAsState(initial = false)
    val playbackSpeed by playerPreferences.shortsPlaybackSpeed.collectAsState(initial = 1f)
    val groupedQualitySelectorEnabled by playerPreferences.groupedQualitySelectorEnabled.collectAsState(
        initial = false
    )
    val customSpeedsEnabled by playerPreferences.customSpeedsEnabled.collectAsState(initial = false)
    val customSpeedPresetsRaw by playerPreferences.customSpeedPresets.collectAsState(initial = "")
    val speedSliderEnabled by playerPreferences.speedSliderEnabled.collectAsState(initial = false)
    val downloadDialogStyle by playerPreferences.downloadDialogStyle.collectAsState(
        initial = DownloadDialogStyle.FULL
    )

    return ShortVideoPlayerSettings(
        playbackMode = playbackMode,
        autoScrollSeconds = autoScrollSeconds,
        uiMode = uiMode,
        ambientModeEnabled = ambientModeEnabled,
        playbackSpeed = playbackSpeed,
        groupedQualitySelectorEnabled = groupedQualitySelectorEnabled,
        customSpeedsEnabled = customSpeedsEnabled,
        customSpeedPresetsRaw = customSpeedPresetsRaw,
        speedSliderEnabled = speedSliderEnabled,
        downloadDialogStyle = downloadDialogStyle
    )
}
