package io.github.aedev.flow.ui.components.videoplayer.controls

import androidx.compose.runtime.Immutable

@Immutable
internal data class PlayerControlActions(
    val onPlayPause: () -> Unit = {},
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
    val onSeek: (Long) -> Unit = {},
    val onScrubbingChange: (Boolean) -> Unit = {},
)
