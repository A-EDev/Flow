package io.github.aedev.flow.ui.components.videoplayer.gesture

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

internal fun HapticFeedback.playerTick() = performHapticFeedback(HapticFeedbackType.TextHandleMove)

internal fun HapticFeedback.playerPress() = performHapticFeedback(HapticFeedbackType.LongPress)
