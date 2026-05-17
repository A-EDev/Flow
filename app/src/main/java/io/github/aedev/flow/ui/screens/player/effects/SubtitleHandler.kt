package io.github.aedev.flow.ui.screens.player.effects

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.media3.common.util.UnstableApi
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.seekbarpreview.SeekbarPreviewThumbnailHelper
import io.github.aedev.flow.ui.screens.player.VideoPlayerUiState
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import org.schabi.newpipe.extractor.stream.StreamInfo

private const val TAG = "SubtitleHandler"

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
