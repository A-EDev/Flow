package io.github.aedev.flow.ui.theme

import androidx.compose.ui.graphics.Color

val PlayerScrim = Color.Black

/** Letterbox and backdrop drawn behind the video surface itself. */
val PlayerGround = Color.Black

/** Pills, icon buttons and chips resting directly on video. */
val PlayerScrimAffordance = PlayerScrim.copy(alpha = 0.4f)

/** Round icon buttons on the floating mini player; the play/close pair sits a little lighter. */
val PlayerScrimMiniButton = PlayerScrim.copy(alpha = 0.36f)
val PlayerScrimMiniTopButton = PlayerScrim.copy(alpha = 0.28f)

/** Tint over the blurred thumbnail behind an immersive fullscreen player. */
val PlayerScrimImmersiveBackdrop = PlayerScrim.copy(alpha = 0.45f)

/** Brightness and volume readouts shown mid-gesture. */
val PlayerScrimGestureHud = PlayerScrim.copy(alpha = 0.54f)

/** Top and bottom edge gradients behind the portrait-fullscreen controls. */
val PlayerScrimEdgeGradient = PlayerScrim.copy(alpha = 0.72f)

val PlayerScrimContent = Color.White

val PlayerScrimContentDisabled = PlayerScrimContent.copy(alpha = 0.3f)

val PlayerLiveIndicator = Color.Red

/** The thin progress bar along the bottom of the floating mini player. */
val PlayerMiniProgress = Color.Red
