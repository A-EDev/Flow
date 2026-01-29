package com.flow.youtube.ui.screens.player.effects

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.media3.common.util.UnstableApi
import com.flow.youtube.player.EnhancedPlayerManager
import com.flow.youtube.player.seekbarpreview.SeekbarPreviewThumbnailHelper
import com.flow.youtube.ui.screens.player.VideoPlayerUiState
import com.flow.youtube.ui.components.SubtitleCue
import com.flow.youtube.ui.components.fetchSubtitles
import com.flow.youtube.ui.screens.player.state.PlayerScreenState
import org.schabi.newpipe.extractor.stream.StreamInfo

private const val TAG = "SubtitleHandler"

object SubtitleHandler {
    
    /**
     * Load subtitles from a URL
     */
    suspend fun loadSubtitles(url: String): List<SubtitleCue> {
        return try {
            Log.d(TAG, "Loading subtitles from: $url")
            val subtitles = fetchSubtitles(url)
            Log.d(TAG, "Loaded ${subtitles.size} subtitle cues")
            subtitles
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load subtitles", e)
            emptyList()
        }
    }
}

/**
 * Effect to load subtitles when URL changes
 */
@Composable
fun SubtitleLoadEffect(
    selectedSubtitleUrl: String?,
    onSubtitlesLoaded: (List<SubtitleCue>) -> Unit,
    onSubtitlesEnabled: (Boolean) -> Unit
) {
    LaunchedEffect(selectedSubtitleUrl) {
        selectedSubtitleUrl?.let { url ->
            try {
                Log.d(TAG, "Selected subtitle URL changed: $url")
                val subtitles = fetchSubtitles(url)
                onSubtitlesLoaded(subtitles)
                onSubtitlesEnabled(subtitles.isNotEmpty())
                Log.d(TAG, "Subtitles loaded: ${subtitles.size} cues")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load subtitles from URL", e)
                onSubtitlesLoaded(emptyList())
                onSubtitlesEnabled(false)
            }
        }
    }
}

/**
 * Effect to load subtitles using PlayerScreenState
 */
@Composable
fun SubtitleLoadEffectWithState(screenState: PlayerScreenState) {
    LaunchedEffect(screenState.selectedSubtitleUrl) {
        screenState.selectedSubtitleUrl?.let { url ->
            try {
                Log.d(TAG, "Selected subtitle URL changed: $url")
                val subtitles = fetchSubtitles(url)
                screenState.currentSubtitles = subtitles
                screenState.subtitlesEnabled = subtitles.isNotEmpty()
                Log.d(TAG, "Subtitles loaded: ${subtitles.size} cues, enabled: ${screenState.subtitlesEnabled}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load subtitles from URL", e)
                screenState.currentSubtitles = emptyList()
                screenState.subtitlesEnabled = false
            }
        }
    }
}

/**
 * Seekbar preview helper initialization effect
 */
@UnstableApi
@Composable
fun SeekbarPreviewEffect(
    context: Context,
    streamInfo: StreamInfo?,
    onHelperCreated: (SeekbarPreviewThumbnailHelper?) -> Unit
) {
    LaunchedEffect(streamInfo) {
        streamInfo?.let { info ->
            try {
                val player = EnhancedPlayerManager.getInstance().getPlayer()
                if (player != null) {
                    val helper = SeekbarPreviewThumbnailHelper(
                        context = context,
                        player = player,
                        timeBar = object : androidx.media3.ui.TimeBar {
                            override fun addListener(listener: androidx.media3.ui.TimeBar.OnScrubListener) {}
                            override fun removeListener(listener: androidx.media3.ui.TimeBar.OnScrubListener) {}
                            override fun getPreferredUpdateDelay(): Long = 1000L
                            override fun setAdGroupTimesMs(adGroupTimesMs: LongArray?, playedAdGroups: BooleanArray?, adGroupCount: Int) {}
                            override fun setBufferedPosition(positionMs: Long) {}
                            override fun setDuration(durationMs: Long) {}
                            override fun setEnabled(enabled: Boolean) {}
                            override fun setKeyCountIncrement(increment: Int) {}
                            override fun setKeyTimeIncrement(increment: Long) {}
                            override fun setPosition(positionMs: Long) {}
                        }
                    ).apply {
                        setupSeekbarPreview(info)
                    }
                    onHelperCreated(helper)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize seekbar preview helper", e)
                onHelperCreated(null)
            }
        }
    }
}

/**
 * Seekbar preview helper initialization effect using PlayerScreenState
 */
@UnstableApi
@Composable
fun SeekbarPreviewEffectWithState(
    context: Context,
    uiState: VideoPlayerUiState,
    screenState: PlayerScreenState
) {
    LaunchedEffect(uiState.streamInfo) {
        uiState.streamInfo?.let { streamInfo ->
            try {
                val player = EnhancedPlayerManager.getInstance().getPlayer()
                if (player != null) {
                    screenState.seekbarPreviewHelper = SeekbarPreviewThumbnailHelper(
                        context = context,
                        player = player,
                        timeBar = object : androidx.media3.ui.TimeBar {
                            override fun addListener(listener: androidx.media3.ui.TimeBar.OnScrubListener) {}
                            override fun removeListener(listener: androidx.media3.ui.TimeBar.OnScrubListener) {}
                            override fun getPreferredUpdateDelay(): Long = 1000L
                            override fun setAdGroupTimesMs(adGroupTimesMs: LongArray?, playedAdGroups: BooleanArray?, adGroupCount: Int) {}
                            override fun setBufferedPosition(positionMs: Long) {}
                            override fun setDuration(durationMs: Long) {}
                            override fun setEnabled(enabled: Boolean) {}
                            override fun setKeyCountIncrement(increment: Int) {}
                            override fun setKeyTimeIncrement(increment: Long) {}
                            override fun setPosition(positionMs: Long) {}
                        }
                    ).apply {
                        setupSeekbarPreview(streamInfo)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize seekbar preview helper", e)
            }
        }
    }
}
