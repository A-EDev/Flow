package com.flow.youtube.player.gesture

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Base gesture listener for player interactions.
 * Based on NewPipe's BasePlayerGestureListener implementation.
 */
abstract class BasePlayerGestureListener(
    protected val context: Context
) : View.OnTouchListener, GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {

    protected var isMoving = false
    protected var isDragging = false
    protected var initialTouchX = 0f
    protected var initialTouchY = 0f

    private val gestureDetector = GestureDetector(context, this).apply {
        setOnDoubleTapListener(this@BasePlayerGestureListener)
    }

    // Touch event constants
    protected val SWIPE_MIN_DISTANCE = 25
    protected val SWIPE_MAX_OFF_PATH = 250
    protected val SWIPE_THRESHOLD_VELOCITY = 200

    enum class DisplayPortion {
        LEFT, CENTER, RIGHT
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.x
                initialTouchY = event.y
                isMoving = false
                isDragging = false
                onDown(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isMoving && !isDragging) {
                    val deltaX = abs(event.x - initialTouchX)
                    val deltaY = abs(event.y - initialTouchY)

                    if (deltaX > SWIPE_MIN_DISTANCE || deltaY > SWIPE_MIN_DISTANCE) {
                        isMoving = true
                        onStartMoving(event)
                    }
                }

                if (isMoving) {
                    onMoving(event)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isMoving) {
                    onEndMoving(event)
                }
                isMoving = false
                isDragging = false
            }
        }

        return true
    }

    // GestureDetector.OnGestureListener methods
    override fun onDown(e: MotionEvent): Boolean {
        return false
    }

    override fun onShowPress(e: MotionEvent) {}

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        return false
    }

    override fun onLongPress(e: MotionEvent) {}

    // GestureDetector.OnDoubleTapListener methods
    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        return onSingleTap(e)
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        return onDoubleTapEvent(e)
    }

    // Abstract methods for subclasses to implement
    abstract fun onSingleTap(e: MotionEvent): Boolean
    abstract override fun onDoubleTapEvent(e: MotionEvent): Boolean
    abstract fun onStartMoving(e: MotionEvent)
    abstract fun onMoving(e: MotionEvent)
    abstract fun onEndMoving(e: MotionEvent)

    // Utility methods
    protected fun getDisplayPortion(x: Float, viewWidth: Int): DisplayPortion {
        val portionWidth = viewWidth / 3f
        return when {
            x < portionWidth -> DisplayPortion.LEFT
            x > portionWidth * 2 -> DisplayPortion.RIGHT
            else -> DisplayPortion.CENTER
        }
    }

    protected fun calculateSeekDistance(deltaX: Float, viewWidth: Int): Long {
        // Convert horizontal movement to seek time
        // 1 second per 100 pixels of movement
        return (deltaX / 100f * 1000f).toLong()
    }

    protected fun calculateVolumeChange(deltaY: Float, viewHeight: Int): Float {
        // Convert vertical movement to volume change
        // Max volume change of 1.0 per full screen height
        return deltaY / viewHeight.toFloat()
    }

    protected fun calculateBrightnessChange(deltaY: Float, viewHeight: Int): Float {
        // Convert vertical movement to brightness change
        // Max brightness change of 1.0 per full screen height
        return -deltaY / viewHeight.toFloat() // Negative because up should increase brightness
    }
}