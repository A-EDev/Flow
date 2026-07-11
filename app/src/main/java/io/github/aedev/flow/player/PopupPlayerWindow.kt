package io.github.aedev.flow.player

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.aedev.flow.R

@UnstableApi
internal class PopupPlayerWindow(
    private val context: Context,
    private val onClose: () -> Unit,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var container: FrameLayout? = null
    private var playerView: PlayerView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    @RequiresApi(Build.VERSION_CODES.M)
    fun show(player: Player): Boolean {
        if (container != null) return true

        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels
        val width = (screenWidth * 0.72f).toInt().coerceIn((240 * density).toInt(), (420 * density).toInt())
        val height = width * 9 / 16
        val params = WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - width) / 2
            y = (72 * density).toInt()
        }

        val root = FrameLayout(context)
        val video = PlayerView(context).apply {
            this.player = player
            useController = true
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setKeepContentOnPlayerReset(true)
        }
        root.addView(video, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))

        val closeButton = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            background = null
            contentDescription = context.getString(R.string.close_popup_player)
            setOnClickListener { onClose() }
        }
        root.addView(closeButton, FrameLayout.LayoutParams(
            (48 * density).toInt(),
            (48 * density).toInt(),
            Gravity.TOP or Gravity.END,
        ))

        val dragHandle = View(context)
        root.addView(dragHandle, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (40 * density).toInt(),
        ).apply { marginEnd = (48 * density).toInt() })
        attachDragHandling(dragHandle, params)

        return runCatching {
            windowManager.addView(root, params)
            container = root
            playerView = video
            layoutParams = params
            PictureInPictureHelper.setPopupActive(true)
            true
        }.getOrElse {
            video.player = null
            false
        }
    }

    fun dismiss() {
        val view = container ?: return
        playerView?.player = null
        runCatching { windowManager.removeView(view) }
        container = null
        playerView = null
        layoutParams = null
        PictureInPictureHelper.setPopupActive(false)
    }

    private fun attachDragHandling(handle: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    container?.let { runCatching { windowManager.updateViewLayout(it, params) } }
                    true
                }
                else -> false
            }
        }
    }
}
