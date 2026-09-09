package io.github.aedev.flow.ui.screens.player.stage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.ui.components.videoplayer.MiniPlayerControls
import io.github.aedev.flow.ui.theme.PlayerScrim
import io.github.aedev.flow.ui.theme.PlayerScrimContent

/** The controls the collapsed player draws over the mini video surface. */
@Composable
internal fun MiniPlayerSlot(
    session: VideoPlayerStageSession,
    miniPlayerShowSkipControls: Boolean,
    miniPlayerShowNextPrevControls: Boolean,
    onClose: () -> Unit,
) {
    val screenState = session.screenState
    val playerState = session.playerState
    val playerUiState = session.uiState
    val playerViewModel = session.viewModel
    val playerSheetState = session.sheetState

    Box(modifier = Modifier.fillMaxSize()) {
        val currentSizeScale = playerSheetState.miniSizeScale.targetValue
        MiniPlayerControls(
            playerState = playerState,
            showSkipControls = miniPlayerShowSkipControls,
            showNextPrevControls = miniPlayerShowNextPrevControls,
            sizeScale = currentSizeScale,
            onPlayPause = {
                if (playerUiState.isRestoredSession) {
                    playerViewModel.resumeRestoredSession(stayMini = true)
                } else if (playerState.hasEnded) {
                    EnhancedPlayerManager.getInstance().replay()
                    playerViewModel.ensureNotificationServiceRunning()
                } else if (playerState.playWhenReady) {
                    EnhancedPlayerManager.getInstance().pause()
                } else {
                    EnhancedPlayerManager.getInstance().play()
                    playerViewModel.ensureNotificationServiceRunning()
                }
            },
            onSkipForward = {
                EnhancedPlayerManager.getInstance().seekTo(screenState.currentPosition + 10000)
            },
            onSkipBack = {
                EnhancedPlayerManager.getInstance().seekTo(screenState.currentPosition - 10000)
            },
            onNext = {
                playerViewModel.playNext()
            },
            onPrevious = {
                playerViewModel.playPrevious()
            },
            onClose = onClose,
        )
        if (playerUiState.isRestoredSession) {
            Text(
                text = stringResource(R.string.player_mini_player_continue_watching_label),
                style = MaterialTheme.typography.labelSmall,
                color = PlayerScrimContent,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .background(
                            PlayerScrim.copy(alpha = 0.73f),
                            RoundedCornerShape(3.dp),
                        ).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
