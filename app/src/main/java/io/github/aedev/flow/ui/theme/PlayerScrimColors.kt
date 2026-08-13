package io.github.aedev.flow.ui.theme

import androidx.compose.ui.graphics.Color

val PlayerScrim = Color.Black

/** Pills, icon buttons and chips resting directly on video. */
val PlayerScrimAffordance = PlayerScrim.copy(alpha = 0.4f)

/** Brightness and volume readouts shown mid-gesture. */
val PlayerScrimGestureHud = PlayerScrim.copy(alpha = 0.54f)

/** Top and bottom edge gradients behind the portrait-fullscreen controls. */
val PlayerScrimEdgeGradient = PlayerScrim.copy(alpha = 0.72f)
