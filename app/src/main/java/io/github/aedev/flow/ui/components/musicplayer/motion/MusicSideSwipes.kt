package io.github.aedev.flow.ui.components.musicplayer.motion

import android.media.AudioManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.local.DEFAULT_SPEED_SWIPE_STEP
import io.github.aedev.flow.data.local.GestureOverlayStyle
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.ui.components.musicplayer.full.MusicPlayerLayout
import io.github.aedev.flow.ui.components.musicplayer.full.hasSideSwipes
import io.github.aedev.flow.ui.components.shared.SpeedSwipeDrag
import io.github.aedev.flow.ui.components.shared.SpeedSwipeOverlay
import io.github.aedev.flow.ui.components.shared.SpeedSwipeStepTravel
import io.github.aedev.flow.ui.components.shared.VolumeOverlay
import io.github.aedev.flow.ui.components.shared.centredGestureHudAlignment
import kotlin.math.abs

private val HudEdgePadding = 24.dp
private val HudTopInset = 12.dp

/** Held open even where the system reports no gesture inset (button navigation, HyperOS), as the video player does. */
private val MinSideEdgeIgnore = 24.dp

/**
 * Speed and volume swipes on the open music player's side columns (#1403). The middle column keeps
 * the queue and collapse swipes; [musicSwipeZoneAt] decides which column a touch belongs to.
 */
internal class MusicSideSwipes(
    private val haptics: HapticFeedback,
    private val audioManager: AudioManager?,
    speedStep: () -> Float,
    private val overlayStyle: State<GestureOverlayStyle>,
    private val topBandPx: () -> Float,
    private val leftEdgePx: () -> Float,
    private val rightEdgePx: () -> Float,
    private val blocked: () -> Boolean,
) {
    var zone by mutableStateOf(MusicSwipeZone.CENTER)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var volume by mutableFloatStateOf(0f)
        private set
    val style: GestureOverlayStyle get() = overlayStyle.value

    private val speedDrag =
        SpeedSwipeDrag(
            haptics = haptics,
            step = speedStep,
            currentSpeed = { EnhancedMusicPlayerManager.playbackSpeed.value },
            isLiveStream = { false },
            setSpeed = { EnhancedMusicPlayerManager.setPlaybackSpeed(it, persist = false) },
            onSpeed = { speed = it },
            onEnd = { EnhancedMusicPlayerManager.setPlaybackSpeed(it) },
        )
    private var maxVolumeStep = 0
    private var volumeStart = 0f
    private var volumeTravelUp = 0f
    private var volumeStep = -1

    fun zoneAt(
        position: Offset,
        size: IntSize,
    ): MusicSwipeZone =
        if (blocked()) {
            MusicSwipeZone.CENTER
        } else {
            musicSwipeZoneAt(position.x, position.y, size.width.toFloat(), topBandPx(), leftEdgePx(), rightEdgePx())
        }

    fun begin(zone: MusicSwipeZone) {
        when (zone) {
            MusicSwipeZone.SPEED -> {
                speedDrag.begin()
                speed = EnhancedMusicPlayerManager.playbackSpeed.value
            }

            MusicSwipeZone.VOLUME -> {
                // The stream is the source of truth (#1062): each swipe starts from what it holds now.
                maxVolumeStep = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
                volumeStep = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                volumeStart = if (maxVolumeStep > 0) volumeStep.toFloat() / maxVolumeStep else 0f
                volumeTravelUp = 0f
                volume = volumeStart
            }

            MusicSwipeZone.CENTER -> {
                Unit
            }
        }
        this.zone = zone
    }

    fun drag(
        dy: Float,
        heightPx: Float,
        stepTravelPx: Float,
    ) {
        when (zone) {
            MusicSwipeZone.SPEED -> speedDrag.drag(dy, stepTravelPx)
            MusicSwipeZone.VOLUME -> dragVolume(dy, heightPx)
            MusicSwipeZone.CENTER -> Unit
        }
    }

    fun end() {
        if (zone == MusicSwipeZone.SPEED) speedDrag.end()
        zone = MusicSwipeZone.CENTER
    }

    private fun dragVolume(
        dy: Float,
        heightPx: Float,
    ) {
        if (maxVolumeStep <= 0) return
        volumeTravelUp -= dy
        val level = MusicVolumeSwipe.levelFor(volumeStart, volumeTravelUp, heightPx)
        // Travel past either end is not banked, so turning back moves the volume at once.
        if (level <= 0f || level >= 1f) {
            volumeStart = level
            volumeTravelUp = 0f
        }
        val step = MusicVolumeSwipe.stepFor(level, maxVolumeStep)
        if (step != volumeStep) {
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            volumeStep = step
        }
        volume = level
    }
}

/** Null unless the user turned the swipes on and the player is [expanded] in a [layout] that has them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberMusicSideSwipes(
    layout: MusicPlayerLayout,
    expanded: Boolean,
    blocked: () -> Boolean,
): MusicSideSwipes? {
    val context = LocalContext.current
    val preferences = remember(context) { PlayerPreferences(context) }
    val enabled by preferences.musicSideSwipes.collectAsState(initial = false)
    val step = preferences.speedSwipeStep.collectAsState(initial = DEFAULT_SPEED_SWIPE_STEP)
    val style = preferences.gestureOverlayStyle.collectAsState(initial = GestureOverlayStyle.CIRCULAR)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val topBand =
        rememberUpdatedState(
            WindowInsets.statusBars.getTop(density) + with(density) { TopAppBarDefaults.TopAppBarExpandedHeight.toPx() },
        )
    val systemGestures = WindowInsets.systemGestures
    val minEdge = with(density) { MinSideEdgeIgnore.toPx() }
    val leftEdge = rememberUpdatedState(maxOf(systemGestures.getLeft(density, layoutDirection).toFloat(), minEdge))
    val rightEdge = rememberUpdatedState(maxOf(systemGestures.getRight(density, layoutDirection).toFloat(), minEdge))
    val currentBlocked = rememberUpdatedState(blocked)
    val haptics = LocalHapticFeedback.current
    val swipes =
        remember(context, haptics) {
            MusicSideSwipes(
                haptics = haptics,
                audioManager = context.getSystemService(AudioManager::class.java),
                speedStep = { step.value },
                overlayStyle = style,
                topBandPx = { topBand.value },
                leftEdgePx = { leftEdge.value },
                rightEdgePx = { rightEdge.value },
                blocked = { currentBlocked.value() },
            )
        }
    return swipes.takeIf { enabled && expanded && layout.hasSideSwipes }
}

/**
 * Claims vertical swipes that start in a side column. It listens in the Initial pass, ahead of the
 * queue pull-up and the artwork underneath, and consumes only once the swipe passes vertical touch
 * slop: those handlers and the sheet's collapse drag then see consumed input and stand down. Taps
 * and sideways drags are never consumed.
 */
internal fun Modifier.musicSideSwipes(swipes: MusicSideSwipes?): Modifier {
    if (swipes == null) return this
    return pointerInput(swipes) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val zone = swipes.zoneAt(down.position, size)
            if (zone == MusicSwipeZone.CENTER) return@awaitEachGesture
            val slop = viewConfiguration.touchSlop
            var travel = Offset.Zero
            var claimed = false
            try {
                while (true) {
                    val change =
                        awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed || (!claimed && change.isConsumed)) break
                    val delta = change.positionChange()
                    if (!claimed) {
                        travel += delta
                        if (abs(travel.x) > slop && abs(travel.x) >= abs(travel.y)) break
                        if (abs(travel.y) <= slop) continue
                        claimed = true
                        swipes.begin(zone)
                    }
                    change.consume()
                    swipes.drag(delta.y, size.height.toFloat(), SpeedSwipeStepTravel.toPx())
                }
            } finally {
                if (claimed) swipes.end()
            }
        }
    }
}

/**
 * The read-out for a side swipe, placed by the video player's rule: the Vertical bar stands opposite
 * the thumb, the others sit at the top centre just under the status bar, or in the middle for the ring.
 */
@Composable
internal fun MusicSideSwipeHud(swipes: MusicSideSwipes?) {
    if (swipes == null) return
    val vertical = swipes.style == GestureOverlayStyle.VERTICAL
    val centred = centredGestureHudAlignment(swipes.style)
    val centredModifier = Modifier.padding(top = if (centred == Alignment.TopCenter) HudTopInset else 0.dp)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = HudEdgePadding),
    ) {
        SpeedSwipeOverlay(
            isVisible = swipes.zone == MusicSwipeZone.SPEED,
            speed = { swipes.speed },
            style = swipes.style,
            modifier = if (vertical) Modifier.align(AbsoluteAlignment.CenterRight) else Modifier.align(centred).then(centredModifier),
        )
        VolumeOverlay(
            isVisible = swipes.zone == MusicSwipeZone.VOLUME,
            volumeLevel = { swipes.volume },
            style = swipes.style,
            maxVolumeLevel = 1f,
            modifier = if (vertical) Modifier.align(AbsoluteAlignment.CenterLeft) else Modifier.align(centred).then(centredModifier),
        )
    }
}
