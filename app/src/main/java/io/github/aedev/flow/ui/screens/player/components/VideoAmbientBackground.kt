package io.github.aedev.flow.ui.screens.player.components

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.palette.graphics.Palette
import io.github.aedev.flow.player.EnhancedPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val SAMPLE_W = 96
private const val SAMPLE_H = 54
private const val DISPLAY_W = 32
private const val DISPLAY_H = 18
private const val UPDATE_MS = 600L
private const val IDLE_MS = 1200L

/** Latest sampled frame plus the dominant/accent colours extracted from it. */
data class AmbientFrameState(
    val frame: ImageBitmap? = null,
    val base: Color? = null,
    val accent: Color? = null
)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun rememberAmbientFrame(playerView: PlayerView, active: Boolean): AmbientFrameState {
    var state by remember { mutableStateOf(AmbientFrameState()) }

    LaunchedEffect(active, playerView) {
        if (!active) {
            state = AmbientFrameState()
            return@LaunchedEffect
        }
        val sample = Bitmap.createBitmap(SAMPLE_W, SAMPLE_H, Bitmap.Config.ARGB_8888)
        val handler = Handler(Looper.getMainLooper())
        try {
            while (isActive) {
                val surface = playerView.videoSurfaceView
                val player = EnhancedPlayerManager.getInstance().getPlayer()
                val playing = player?.isPlaying == true
                if (playing && surface != null && surface.width > 0 && surface.height > 0) {
                    val captured = captureSurface(surface, sample, handler)
                    if (captured) {
                        val display = withContext(Dispatchers.Default) {
                            val (base, accent) = extractColors(sample)
                            val scaled = Bitmap.createScaledBitmap(sample, DISPLAY_W, DISPLAY_H, true)
                            Triple(scaled.asImageBitmap(), base, accent)
                        }
                        state = AmbientFrameState(display.first, display.second, display.third)
                    }
                }
                delay(if (playing) UPDATE_MS else IDLE_MS)
            }
        } finally {
            sample.recycle()
        }
    }

    return state
}

private suspend fun captureSurface(surface: View, dst: Bitmap, handler: Handler): Boolean =
    suspendCancellableCoroutine { cont ->
        try {
            when {
                surface is TextureView -> {
                    if (surface.isAvailable) {
                        surface.getBitmap(dst)
                        cont.resume(true)
                    } else {
                        cont.resume(false)
                    }
                }
                surface is SurfaceView && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                    val holderSurface = surface.holder?.surface
                    if (holderSurface != null && holderSurface.isValid) {
                        PixelCopy.request(
                            surface,
                            dst,
                            { result -> cont.resume(result == PixelCopy.SUCCESS) },
                            handler
                        )
                    } else {
                        cont.resume(false)
                    }
                }
                else -> cont.resume(false)
            }
        } catch (t: Throwable) {
            cont.resume(false)
        }
    }

private fun extractColors(bmp: Bitmap): Pair<Color?, Color?> {
    val palette = Palette.from(bmp).clearFilters().generate()
    val baseSwatch = palette.darkMutedSwatch ?: palette.darkVibrantSwatch ?: palette.dominantSwatch
    val accentSwatch = palette.vibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: palette.mutedSwatch
        ?: palette.dominantSwatch
    return baseSwatch?.let { Color(it.rgb) } to accentSwatch?.let { Color(it.rgb) }
}

@Composable
fun VideoAmbientBackground(
    frame: ImageBitmap?,
    baseColor: Color?,
    accentColor: Color?,
    modifier: Modifier = Modifier
) {
    val animatedBase by animateColorAsState(
        targetValue = baseColor ?: Color.Transparent,
        animationSpec = tween(700),
        label = "ambientBase"
    )
    val animatedAccent by animateColorAsState(
        targetValue = accentColor ?: Color.Transparent,
        animationSpec = tween(700),
        label = "ambientAccent"
    )
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(animatedBase.copy(alpha = 0.55f))
        )

        Crossfade(
            targetState = frame,
            animationSpec = tween(600),
            label = "ambientFrame",
            modifier = Modifier.matchParentSize()
        ) { img ->
            if (img != null) {
                Image(
                    bitmap = img,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = if (supportsBlur) {
                        Modifier
                            .fillMaxSize()
                            .blur(24.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    } else {
                        Modifier.fillMaxSize()
                    },
                    alpha = 0.6f
                )
            } else {
                Box(Modifier.fillMaxSize())
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.20f),
                            0.50f to animatedAccent.copy(alpha = 0.14f),
                            1.00f to Color.Black.copy(alpha = 0.20f)
                        )
                    )
                )
        )
    }
}
