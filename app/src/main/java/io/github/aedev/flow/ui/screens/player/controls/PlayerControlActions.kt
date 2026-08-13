package io.github.aedev.flow.ui.screens.player.controls

import androidx.compose.runtime.Immutable

@Immutable
data class PlayerControlActions(
    val onPlayPause: () -> Unit = {},
    val onSeek: (Long) -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onSettingsClick: () -> Unit = {},
    val onQualityClick: () -> Unit = {},
    val onSpeedClick: () -> Unit = {},
    val onFullscreenClick: () -> Unit = {},
    val onResizeClick: () -> Unit = {},
    val onPipClick: () -> Unit = {},
    val onChapterClick: () -> Unit = {},
    val onSubtitleClick: () -> Unit = {},
    val onSubtitleLongClick: () -> Unit = {},
    val onAutoplayToggle: (Boolean) -> Unit = {},
    val onSbSubmitClick: () -> Unit = {},
    val onCastClick: () -> Unit = {},
    val onLiveClick: () -> Unit = {},
    val onLiveChatClick: () -> Unit = {},
    val onCommentsClick: () -> Unit = {},
    val onSleepTimerClick: () -> Unit = {},
    val onToggleRemainingTime: () -> Unit = {},
    val onTouchLockToggle: () -> Unit = {},
)
