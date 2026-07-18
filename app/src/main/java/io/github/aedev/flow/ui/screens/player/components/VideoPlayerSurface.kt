package io.github.aedev.flow.ui.screens.player.components

import android.graphics.Outline
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.PictureInPictureHelper
import io.github.aedev.flow.player.sanitizeDisplayAspectRatio

private fun pickPlayerViewLayoutRes(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        R.layout.video_player_view_surface
    } else {
        R.layout.video_player_view
    }

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerSurface(
    video: Video,
    resizeMode: Int,
    modifier: Modifier = Modifier,
    onVideoAspectRatioChanged: ((Float) -> Unit)? = null,
    cornerRadiusDp: Float = 0f,
    ambientMode: Boolean = false
) {
    val ambientActive = ambientMode && resizeMode == 0
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRestoreTrigger by remember { mutableIntStateOf(0) }
    var attachedVideoId by remember { mutableStateOf<String?>(null) }
    var lastAudioOnlySkipLogKey by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    val currentVideoId by rememberUpdatedState(video.id)
    val currentAspectRatioCallback by rememberUpdatedState(onVideoAspectRatioChanged)
    val cornerRadiusPx = with(density) { cornerRadiusDp.dp.toPx() }

    val playerView = remember(video.id) {
        Log.d("EnhancedVideoPlayer", "Creating shared PlayerView (sdk=${Build.VERSION.SDK_INT})")
        (LayoutInflater.from(context).inflate(pickPlayerViewLayoutRes(), null) as PlayerView).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            setKeepContentOnPlayerReset(true)
        }
    }

    val videoSizeListener = remember {
        object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val playerMediaId = EnhancedPlayerManager.getInstance().getPlayer()
                    ?.currentMediaItem
                    ?.mediaId
                if (playerMediaId == null || playerMediaId == currentVideoId) {
                    videoSize.toDisplayAspectRatioOrNull()?.let { ratio ->
                        currentAspectRatioCallback?.invoke(ratio)
                    }
                }
            }
        }
    }

    DisposableEffect(playerView) {
        onDispose {
            playerView.player?.removeListener(videoSizeListener)
            playerView.player = null
        }
    }

    DisposableEffect(playerView) {
        val rect = android.graphics.Rect()
        val updateHint = {
            if (playerView.getGlobalVisibleRect(rect) && !rect.isEmpty) {
                PictureInPictureHelper.sourceRectHint = android.graphics.Rect(rect)
            }
        }
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateHint() }
        playerView.addOnLayoutChangeListener(listener)
        updateHint()
        onDispose {
            playerView.removeOnLayoutChangeListener(listener)
            PictureInPictureHelper.sourceRectHint = null
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                surfaceRestoreTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val currentSurfaceRestoreTrigger = surfaceRestoreTrigger

    val ambientFrame = rememberAmbientFrame(playerView, ambientActive)

    key(video.id) {
      Box(modifier = modifier.fillMaxSize()) {
        if (ambientActive) {
            VideoAmbientBackground(
                frame = ambientFrame.frame,
                baseColor = ambientFrame.base,
                accentColor = ambientFrame.accent,
                modifier = Modifier.fillMaxSize()
            )
        }
        AndroidView(
            factory = { playerView },
            update = { view ->
                @Suppress("UNUSED_VARIABLE")
                val restoreTrigger = currentSurfaceRestoreTrigger
                val manager = EnhancedPlayerManager.getInstance()
                val newPlayer = manager.getPlayer()
                val oldPlayer = view.player
                val videoChanged = attachedVideoId != video.id

                if (oldPlayer !== newPlayer || videoChanged) {
                    val isVideoSwitch = attachedVideoId != null && videoChanged
                    oldPlayer?.removeListener(videoSizeListener)
                    if (oldPlayer === newPlayer && oldPlayer != null) {
                        view.player = null
                    }
                    newPlayer?.addListener(videoSizeListener)
                    view.player = newPlayer
                    attachedVideoId = video.id
                    val playerMediaId = newPlayer?.currentMediaItem?.mediaId
                    currentAspectRatioCallback?.invoke(
                        if (isVideoSwitch && playerMediaId != video.id) {
                            16f / 9f
                        } else {
                            newPlayer?.videoSize?.toDisplayAspectRatioOrNull() ?: (16f / 9f)
                        }
                    )
                }

                if (newPlayer != null && manager.isInAudioOnlyMode()) {
                    val lifecycleReadyForVideo =
                        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                    val displayInteractive = context.isDisplayInteractive()
                    if (lifecycleReadyForVideo && displayInteractive) {
                        Log.d("VideoPlayerSurface", "Restoring video output after audio-only background mode")
                        lastAudioOnlySkipLogKey = null
                        manager.restoreVideoOutput()
                    } else {
                        val skipLogKey = lifecycleReadyForVideo to displayInteractive
                        if (lastAudioOnlySkipLogKey != skipLogKey) {
                            Log.d(
                                "VideoPlayerSurface",
                                "Keeping audio-only background mode (started=$lifecycleReadyForVideo interactive=$displayInteractive)"
                            )
                            lastAudioOnlySkipLogKey = skipLogKey
                        }
                    }
                }

                if (newPlayer != null &&
                    newPlayer.playbackState == Player.STATE_IDLE &&
                    newPlayer.currentMediaItem != null
                ) {
                    Log.d("VideoPlayerSurface", "PlayerView attached; player IDLE with media - calling prepare()")
                    newPlayer.prepare()
                }

                view.subtitleView?.let { subtitleView ->
                    if (subtitleView.visibility != View.GONE) {
                        subtitleView.visibility = View.GONE
                    }
                }

                val desiredResizeMode = when (resizeMode) {
                    0 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                    2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                if (view.resizeMode != desiredResizeMode) {
                    view.resizeMode = desiredResizeMode
                }

                view.setBackgroundColor(
                    if (ambientActive) android.graphics.Color.TRANSPARENT else android.graphics.Color.BLACK
                )

                applyOutlineCornerRadius(view, cornerRadiusPx)
            },
            modifier = Modifier.fillMaxSize()
        )
      }
    }
}

private fun applyOutlineCornerRadius(view: PlayerView, radiusPx: Float) {
    val current = view.getTag(R.id.player_view) as? Float
    if (current != null && current == radiusPx) return
    if (radiusPx <= 0f) {
        view.clipToOutline = false
        view.outlineProvider = ViewOutlineProvider.BACKGROUND
    } else {
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, radiusPx)
            }
        }
        view.clipToOutline = true
    }
    view.invalidateOutline()
    view.setTag(R.id.player_view, radiusPx)
}

private fun VideoSize.toDisplayAspectRatioOrNull(): Float? {
    if (width <= 0 || height <= 0) return null
    val par = if (pixelWidthHeightRatio > 0f) pixelWidthHeightRatio else 1f
    return sanitizeDisplayAspectRatio(width * par / height)
}

private fun Context.isDisplayInteractive(): Boolean =
    runCatching {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
    }.getOrDefault(true)

private val Float.dp: androidx.compose.ui.unit.Dp
    get() = androidx.compose.ui.unit.Dp(this)
