package com.flow.youtube.player.gesture

import android.content.Context
import android.media.AudioManager
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

/**
 * Enhanced gesture listener for video player with seek, volume, and brightness controls.
 * Based on NewPipe's gesture handling patterns.
 */
class VideoPlayerGestureListener(
    context: Context,
    private val playerView: View,
    private val onSeek: (Long) -> Unit,
    private val onVolumeChange: (Float) -> Unit,
    private val onBrightnessChange: (Float) -> Unit,
    private val onPlayPause: () -> Unit,
    private val onShowControls: () -> Unit
) : BasePlayerGestureListener(context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var initialVolume = 0
    private var initialBrightness = 0f
    private var currentGestureType = GestureType.NONE

    enum class GestureType {
        NONE, SEEK, VOLUME, BRIGHTNESS
    }

    override fun onSingleTap(e: MotionEvent): Boolean {
        onShowControls()
        return true
    }

    override fun onDoubleTapEvent(e: MotionEvent): Boolean {
        val portion = getDisplayPortion(e.x, playerView.width)

        when (portion) {
            DisplayPortion.LEFT -> {
                // Double tap left side: seek backward
                onSeek(-10000L) // 10 seconds back
            }
            DisplayPortion.RIGHT -> {
                // Double tap right side: seek forward
                onSeek(10000L) // 10 seconds forward
            }
            DisplayPortion.CENTER -> {
                // Double tap center: play/pause
                onPlayPause()
            }
        }
        return true
    }

    override fun onStartMoving(e: MotionEvent) {
        val deltaX = abs(e.x - initialTouchX)
        val deltaY = abs(e.y - initialTouchY)

        // Determine gesture type based on initial movement direction
        currentGestureType = when {
            deltaX > deltaY -> GestureType.SEEK // Horizontal movement
            deltaX < deltaY -> {
                // Vertical movement - determine left/right side
                val portion = getDisplayPortion(initialTouchX, playerView.width)
                when (portion) {
                    DisplayPortion.LEFT -> {
                        initialBrightness = getCurrentBrightness()
                        GestureType.BRIGHTNESS
                    }
                    DisplayPortion.RIGHT -> {
                        initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        GestureType.VOLUME
                    }
                    else -> GestureType.NONE
                }
            }
            else -> GestureType.NONE
        }

        isDragging = currentGestureType != GestureType.NONE
    }

    override fun onMoving(e: MotionEvent) {
        if (!isDragging) return

        when (currentGestureType) {
            GestureType.SEEK -> {
                val deltaX = e.x - initialTouchX
                val seekDistance = calculateSeekDistance(deltaX, playerView.width)
                // Show seek preview (would be implemented in UI)
            }
            GestureType.VOLUME -> {
                val deltaY = initialTouchY - e.y // Inverted for natural feel
                val volumeChange = calculateVolumeChange(deltaY, playerView.height)
                val newVolume = (initialVolume + (volumeChange * getMaxVolume())).toInt()
                setVolume(newVolume)
            }
            GestureType.BRIGHTNESS -> {
                val deltaY = initialTouchY - e.y // Inverted for natural feel
                val brightnessChange = calculateBrightnessChange(deltaY, playerView.height)
                val newBrightness = (initialBrightness + brightnessChange).coerceIn(0f, 1f)
                setBrightness(newBrightness)
            }
            GestureType.NONE -> {}
        }
    }

    override fun onEndMoving(e: MotionEvent) {
        when (currentGestureType) {
            GestureType.SEEK -> {
                val deltaX = e.x - initialTouchX
                val seekDistance = calculateSeekDistance(deltaX, playerView.width)
                onSeek(seekDistance)
            }
            GestureType.VOLUME, GestureType.BRIGHTNESS -> {
                // Volume and brightness changes are applied in real-time during movement
            }
            GestureType.NONE -> {}
        }

        currentGestureType = GestureType.NONE
    }

    private fun getCurrentBrightness(): Float {
        return try {
            val layoutParams = playerView.context.let { ctx ->
                val window = (ctx as? android.app.Activity)?.window
                window?.attributes?.screenBrightness ?: 0.5f
            }
            layoutParams.coerceIn(0f, 1f)
        } catch (e: Exception) {
            0.5f
        }
    }

    private fun setBrightness(brightness: Float) {
        try {
            val layoutParams = (playerView.context as? android.app.Activity)?.window?.attributes
            layoutParams?.screenBrightness = brightness
            (playerView.context as? android.app.Activity)?.window?.attributes = layoutParams
            onBrightnessChange(brightness)
        } catch (e: Exception) {
            // Handle cases where brightness cannot be set
        }
    }

    private fun getMaxVolume(): Int {
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }

    private fun setVolume(volume: Int) {
        val clampedVolume = volume.coerceIn(0, getMaxVolume())
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clampedVolume, 0)
        onVolumeChange(clampedVolume.toFloat() / getMaxVolume())
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        // Handle scrolling gestures if needed
        return false
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        // Handle fling gestures if needed
        return false
    }
}