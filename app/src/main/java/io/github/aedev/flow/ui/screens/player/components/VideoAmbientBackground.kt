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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
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
private const val UPDATE_MS = 900L
private const val IDLE_MS = 1200L
private const val AMBIENT_BASE_ALPHA = 0.52f
private const val AMBIENT_FRAME_ALPHA = 0.46f
private const val AMBIENT_ACCENT_ALPHA = 0.24f
private const val AMBIENT_SCRIM_ALPHA = 0.24f
private const val AMBIENT_BLUR_DP = 42
private const val MIN_COLOR_POPULATION = 3
private const val MIN_PREFERRED_LUMA = 0.20f
private const val MAX_PREFERRED_LUMA = 0.86f

/** Latest sampled frame plus the dominant/accent colours extracted from it. */
data class AmbientFrameState(
    val frame: ImageBitmap? = null,
    val base: Color? = null,
    val accent: Color? = null
)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun rememberAmbientFrame(
    playerView: PlayerView,
    active: Boolean,
    isPlayingProvider: () -> Boolean = {
        EnhancedPlayerManager.getInstance().getPlayer()?.isPlaying == true
    }
): AmbientFrameState {
    var state by remember { mutableStateOf(AmbientFrameState()) }
    val currentIsPlayingProvider by rememberUpdatedState(isPlayingProvider)

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
                val playing = currentIsPlayingProvider()
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
    val usableSwatches = palette.swatches
        .filter { it.population >= MIN_COLOR_POPULATION }
        .sortedWith(
            compareByDescending<Palette.Swatch> { swatch ->
                val hsl = swatch.hsl
                val lumaFit = 1f - kotlin.math.abs(hsl[2].coerceIn(0f, 1f) - 0.56f)
                (hsl[1] * 1.5f + lumaFit) * swatch.population
            }
        )
    val baseSwatch = usableSwatches.firstOrNull { swatch ->
        swatch.hsl[2] in MIN_PREFERRED_LUMA..MAX_PREFERRED_LUMA
    } ?: palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
    val accentSwatch = palette.vibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: usableSwatches.firstOrNull()
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
                .background(animatedBase.copy(alpha = AMBIENT_BASE_ALPHA))
        )

        Crossfade(
            targetState = frame,
            animationSpec = tween(800),
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
                            .blur(AMBIENT_BLUR_DP.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    } else {
                        Modifier.fillMaxSize()
                    },
                    alpha = AMBIENT_FRAME_ALPHA
                )
            } else {
                Box(Modifier.fillMaxSize())
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(animatedAccent.copy(alpha = AMBIENT_ACCENT_ALPHA))
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = AMBIENT_SCRIM_ALPHA))
        )
    }
}
