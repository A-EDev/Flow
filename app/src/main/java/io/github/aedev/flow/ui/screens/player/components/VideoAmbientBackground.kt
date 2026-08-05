package io.github.aedev.flow.ui.screens.player.components

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.palette.graphics.Palette
import io.github.aedev.flow.player.EnhancedPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

private const val SAMPLE_W = 96
private const val SAMPLE_H = 54
private const val DISPLAY_W = 32
private const val DISPLAY_H = 18

/** 96/32 == 54/18 == 3, so decimation is an exact 3x3 box and needs no resampler. */
private const val DECIMATION = 3

private const val CAPTURE_MS_ASYNC = 900L
private const val CAPTURE_MS_BLOCKING = 1800L
private const val IDLE_MS = 1500L

/**
 * Temporal smoothing. One exponential filter over the pixel buffer replaces what used to be three
 * overlapping Compose animations — `Crossfade(800ms)` on the frame plus `animateColorAsState(700ms)`
 * on base and accent.
 *
 * That mattered for more than tidiness. Compose animations run a frame every vsync, so the ambient
 * layer drove a full-screen redraw at the display's refresh rate for the whole of playback.
 * Stepping the filter at ~16 Hz and emitting only on change decouples the glow from the refresh
 * rate entirely.
 *
 * `alpha = 1 - exp(-dt / tau)` uses the *measured* elapsed time rather than a fixed per-tick
 * constant. The usual `current += (target - current) * 0.1` shortcut is a filter whose cutoff
 * depends on how often it happens to run, so it behaves differently on 60 Hz, on 120 Hz, and on a
 * thermally throttled device.
 *
 * SAFETY: tau also bounds how fast the glow can respond to strobing content. A first-order filter
 * at tau = 600 ms sits ~11x above its 0.265 Hz cutoff at 3 Hz, attenuating roughly 21 dB, which
 * keeps concert and animation footage under WCAG 2.3.1's three-flashes-per-second threshold. Do not
 * add a "snap on large delta" fast path to make cuts feel crisper — that is exactly the change that
 * reintroduces the hazard. If cuts feel soft, lower tau globally instead.
 */
private const val SMOOTH_TICK_MS = 60L
private const val SMOOTH_IDLE_TICK_MS = 200L
private const val SMOOTH_TAU_MS = 600f

/** Once every channel is within this of its target (linear units) the filter stops emitting. */
private const val CONVERGENCE_EPS = 0.0015f

private const val BLUR_RADIUS_PX = 2
private const val BLUR_PASSES = 3

/**
 * Below this mean per-channel delta (0..255, sRGB) the frame is treated as unchanged and neither
 * the palette nor the smoothing target is touched. Static shots are most of a watch session.
 */
internal const val FRAME_CHANGE_THRESHOLD = 4

/** Every Nth pixel is compared for change detection; the sample is already tiny. */
internal const val CHANGE_SAMPLE_STEP = 7

/**
 * Only a permanent signal disables capture. `ERROR_SOURCE_INVALID` on a protected surface is a
 * platform guarantee that the readback will never succeed, so reissuing it every cadence for the
 * length of a video is pure waste.
 *
 * Everything else backs off but never latches. A surface can be legitimately unavailable for
 * seconds — during startup, after a fullscreen transition, or while the surface is re-created — and
 * a counter that disabled the effect after N of those would turn a transient hiccup into "ambient
 * is black for the rest of this video" with no way back.
 */
private const val MAX_BACKOFF_MULTIPLIER = 8L

private const val CAPTURE_OK = 0
private const val CAPTURE_RETRY = 1
private const val CAPTURE_UNSUPPORTED = 2

private const val LINEAR_LUT_SIZE = 4096

/** Exact sRGB EOTF; 256 entries covers every possible input byte. */
private val SRGB_TO_LINEAR =
    FloatArray(256) { i ->
        val c = i / 255f
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }

/**
 * Inverse EOTF. 4096 entries rather than the ~2048 that would give half-LSB accuracy across most of
 * the range: the curve's slope is 12.92 near black, so a coarser table quantises visibly there —
 * and near-black is exactly where a dim glow lives.
 */
private val LINEAR_TO_SRGB =
    IntArray(LINEAR_LUT_SIZE + 1) { i ->
        val c = i / LINEAR_LUT_SIZE.toFloat()
        val s = if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f
        (s * 255f + 0.5f).toInt().coerceIn(0, 255)
    }

/** Latest smoothed frame plus the dominant/accent colours extracted from it. */
data class AmbientFrameState(
    val frame: ImageBitmap? = null,
    val base: Color? = null,
    val accent: Color? = null,
    /** False once capture has been found permanently impossible, e.g. a protected surface. */
    val supported: Boolean = true,
)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun rememberAmbientFrame(
    playerView: PlayerView,
    active: Boolean,
    isPlayingProvider: () -> Boolean = {
        EnhancedPlayerManager.getInstance().getPlayer()?.isPlaying == true
    },
): AmbientFrameState {
    var state by remember { mutableStateOf(AmbientFrameState()) }
    val currentIsPlayingProvider by rememberUpdatedState(isPlayingProvider)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(active, playerView, lifecycleOwner) {
        if (!active) {
            state = AmbientFrameState()
            return@LaunchedEffect
        }
        // Gated on STARTED. Background audio keeps the composition alive and the player playing, so
        // without this the loop kept running a GPU readback plus a palette pass every cadence with
        // the screen off, painting a surface nobody could see.
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val pipeline = AmbientPipeline()
            // coroutineScope, not a bare launch pair: it suspends until both loops finish, so the
            // pipeline is never abandoned while either loop can still touch its buffers.
            coroutineScope {
                // Capture sets targets at whatever cadence the surface can afford; smoothing
                // walks toward them on its own clock. Both run on Main, so the shared target
                // buffers need no synchronisation — the heavy work inside each hops to Default
                // and is joined before anything is published.
                launch { pipeline.runCapture(playerView, currentIsPlayingProvider) }
                launch { pipeline.runSmoothing { state = it } }
            }
        }
    }

    return state
}

/**
 * Owns every buffer the effect needs for the lifetime of one STARTED window. Everything is
 * allocated once: the loops below must not allocate in steady state.
 */
private class AmbientPipeline {
    private val sample = Bitmap.createBitmap(SAMPLE_W, SAMPLE_H, Bitmap.Config.ARGB_8888)
    private val samplePixels = IntArray(SAMPLE_W * SAMPLE_H)
    private val previousPixels = IntArray(SAMPLE_W * SAMPLE_H)
    private val handler = Handler(Looper.getMainLooper())

    private val cells = DISPLAY_W * DISPLAY_H
    private val targetGrid = FloatArray(cells * 3)
    private val currentGrid = FloatArray(cells * 3)
    private val scratchGrid = FloatArray(cells * 3)
    private val stagingGrid = FloatArray(cells * 3)
    private val outPixels = IntArray(cells)

    private val targetBase = FloatArray(3)
    private val targetAccent = FloatArray(3)
    private val currentBase = FloatArray(3)
    private val currentAccent = FloatArray(3)

    // Double buffered: the render thread may still be uploading the bitmap handed over last tick.
    private val buffers =
        arrayOf(
            Bitmap.createBitmap(DISPLAY_W, DISPLAY_H, Bitmap.Config.ARGB_8888),
            Bitmap.createBitmap(DISPLAY_W, DISPLAY_H, Bitmap.Config.ARGB_8888),
        )
    private var bufferIndex = 0

    private var hasPreviousSample = false
    private var hasTarget = false
    private var seeded = false
    private var supported = true
    private var consecutiveFailures = 0

    suspend fun runCapture(
        playerView: PlayerView,
        isPlaying: () -> Boolean,
    ) {
        while (currentCoroutineContext().isActive && supported) {
            val surface = playerView.videoSurfaceView
            val playing = isPlaying()
            var cadence = IDLE_MS
            if (playing && surface != null && surface.width > 0 && surface.height > 0) {
                val base = if (surface is TextureView) CAPTURE_MS_BLOCKING else CAPTURE_MS_ASYNC
                when (captureSurface(surface, sample, handler)) {
                    CAPTURE_OK -> {
                        consecutiveFailures = 0
                        cadence = base
                        // Heavy work off Main; withContext joins before anything is published, so
                        // the staging buffers are never read concurrently.
                        val accepted = withContext(Dispatchers.Default) { computeTarget() }
                        if (accepted) {
                            stagingGrid.copyInto(targetGrid)
                            hasTarget = true
                        }
                    }

                    CAPTURE_UNSUPPORTED -> {
                        supported = false
                    }

                    else -> {
                        consecutiveFailures++
                        val multiplier =
                            (1L shl consecutiveFailures.coerceAtMost(3))
                                .coerceAtMost(MAX_BACKOFF_MULTIPLIER)
                        cadence = base * multiplier
                    }
                }
            }
            delay(cadence)
        }
    }

    /** Runs on Default. Returns true when the frame differed enough to become a new target. */
    private fun computeTarget(): Boolean {
        sample.getPixels(samplePixels, 0, SAMPLE_W, 0, 0, SAMPLE_W, SAMPLE_H)
        val changed =
            !hasPreviousSample ||
                meanAbsDiff(samplePixels, previousPixels) >= FRAME_CHANGE_THRESHOLD
        if (!changed) return false
        // previousPixels advances only on an accepted frame, so a slow fade accumulates against a
        // fixed reference instead of never clearing the threshold.
        samplePixels.copyInto(previousPixels)
        hasPreviousSample = true

        decimateToLinear(samplePixels, stagingGrid)
        boxBlurLinear(stagingGrid, scratchGrid, DISPLAY_W, DISPLAY_H, BLUR_RADIUS_PX, BLUR_PASSES)

        val (base, accent) = extractColors(sample)
        base?.let { toLinear(it, targetBase) }
        accent?.let { toLinear(it, targetAccent) }
        return true
    }

    suspend fun runSmoothing(emit: (AmbientFrameState) -> Unit) {
        var last = SystemClock.elapsedRealtime()
        while (currentCoroutineContext().isActive) {
            if (!hasTarget) {
                delay(SMOOTH_IDLE_TICK_MS)
                last = SystemClock.elapsedRealtime()
                continue
            }
            if (!seeded) {
                targetGrid.copyInto(currentGrid)
                targetBase.copyInto(currentBase)
                targetAccent.copyInto(currentAccent)
                seeded = true
                emit(publish())
                delay(SMOOTH_TICK_MS)
                last = SystemClock.elapsedRealtime()
                continue
            }

            val now = SystemClock.elapsedRealtime()
            val dt = (now - last).coerceIn(1L, 500L)
            last = now
            val alpha = 1f - exp(-dt.toFloat() / SMOOTH_TAU_MS)

            val gridMoved = step(currentGrid, targetGrid, alpha)
            val baseMoved = step(currentBase, targetBase, alpha)
            val accentMoved = step(currentAccent, targetAccent, alpha)

            if (gridMoved || baseMoved || accentMoved) {
                emit(publish())
                delay(SMOOTH_TICK_MS)
            } else {
                delay(SMOOTH_IDLE_TICK_MS)
                last = SystemClock.elapsedRealtime()
            }
        }
    }

    private fun publish(): AmbientFrameState {
        encodeToPixels(currentGrid, outPixels)
        bufferIndex = bufferIndex xor 1
        val bitmap = buffers[bufferIndex]
        bitmap.setPixels(outPixels, 0, DISPLAY_W, 0, 0, DISPLAY_W, DISPLAY_H)
        return AmbientFrameState(
            frame = bitmap.asImageBitmap(),
            base = fromLinear(currentBase),
            accent = fromLinear(currentAccent),
            supported = supported,
        )
    }
}

/** Advances [current] toward [target]; returns whether anything moved beyond the epsilon. */
internal fun step(
    current: FloatArray,
    target: FloatArray,
    alpha: Float,
): Boolean {
    var moved = false
    for (i in current.indices) {
        val delta = target[i] - current[i]
        if (abs(delta) > CONVERGENCE_EPS) {
            current[i] += delta * alpha
            moved = true
        } else {
            current[i] = target[i]
        }
    }
    return moved
}

/**
 * Fuses the 3x3 decimation with the sRGB to linear conversion in one pass.
 *
 * The previous implementation used `createScaledBitmap(..., filter = true)`, which is bilinear: it
 * samples 2x2 texels where a 3x reduction needs 3x3, so roughly half the source was discarded and
 * the aliasing the 96x54 capture existed to prevent came back as a crawling glow on fine detail.
 * Averaging in gamma-encoded sRGB was the other half — mixing saturated complementaries there lands
 * darker than either input.
 */
private fun decimateToLinear(
    src: IntArray,
    dst: FloatArray,
) {
    val n = (DECIMATION * DECIMATION).toFloat()
    var o = 0
    for (y in 0 until DISPLAY_H) {
        for (x in 0 until DISPLAY_W) {
            var r = 0f
            var g = 0f
            var b = 0f
            for (dy in 0 until DECIMATION) {
                var idx = (y * DECIMATION + dy) * SAMPLE_W + x * DECIMATION
                for (dx in 0 until DECIMATION) {
                    val c = src[idx++]
                    r += SRGB_TO_LINEAR[(c shr 16) and 0xFF]
                    g += SRGB_TO_LINEAR[(c shr 8) and 0xFF]
                    b += SRGB_TO_LINEAR[c and 0xFF]
                }
            }
            dst[o++] = r / n
            dst[o++] = g / n
            dst[o++] = b / n
        }
    }
}

internal fun boxBlurLinear(
    buf: FloatArray,
    scratch: FloatArray,
    w: Int,
    h: Int,
    radius: Int,
    passes: Int,
) {
    if (radius <= 0 || w <= 0 || h <= 0) return
    repeat(passes) {
        blurAxisLinear(buf, scratch, w, h, radius, horizontal = true)
        blurAxisLinear(scratch, buf, w, h, radius, horizontal = false)
    }
}

private fun blurAxisLinear(
    src: FloatArray,
    dst: FloatArray,
    w: Int,
    h: Int,
    radius: Int,
    horizontal: Boolean,
) {
    val lines = if (horizontal) h else w
    val span = if (horizontal) w else h
    for (line in 0 until lines) {
        for (i in 0 until span) {
            var r = 0f
            var g = 0f
            var b = 0f
            var n = 0
            for (k in -radius..radius) {
                val j = (i + k).coerceIn(0, span - 1)
                val o = (if (horizontal) line * w + j else j * w + line) * 3
                r += src[o]
                g += src[o + 1]
                b += src[o + 2]
                n++
            }
            val o = (if (horizontal) line * w + i else i * w + line) * 3
            dst[o] = r / n
            dst[o + 1] = g / n
            dst[o + 2] = b / n
        }
    }
}

private fun encodeToPixels(
    grid: FloatArray,
    out: IntArray,
) {
    for (i in out.indices) {
        val o = i * 3
        out[i] = (0xFF shl 24) or
            (linearToSrgb(grid[o]) shl 16) or
            (linearToSrgb(grid[o + 1]) shl 8) or
            linearToSrgb(grid[o + 2])
    }
}

/**
 * Rounds rather than truncates into the table. Flooring costs up to a full table step, which near
 * black — where the EOTF slope is 12.92 — is enough to lose a whole output level and break the
 * round trip.
 */
internal fun linearToSrgb(v: Float): Int = LINEAR_TO_SRGB[(v.coerceIn(0f, 1f) * LINEAR_LUT_SIZE + 0.5f).toInt()]

internal fun srgbToLinear(byteValue: Int): Float = SRGB_TO_LINEAR[byteValue.coerceIn(0, 255)]

private fun toLinear(
    color: Color,
    out: FloatArray,
) {
    out[0] = srgbToLinear((color.red * 255f + 0.5f).toInt())
    out[1] = srgbToLinear((color.green * 255f + 0.5f).toInt())
    out[2] = srgbToLinear((color.blue * 255f + 0.5f).toInt())
}

private fun fromLinear(v: FloatArray): Color = Color(linearToSrgb(v[0]), linearToSrgb(v[1]), linearToSrgb(v[2]))

/** Mean absolute per-channel difference over a strided subset of the two buffers. */
internal fun meanAbsDiff(
    current: IntArray,
    previous: IntArray,
): Int {
    var sum = 0L
    var count = 0
    var i = 0
    while (i < current.size) {
        val a = current[i]
        val b = previous[i]
        sum += abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
        sum += abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
        sum += abs((a and 0xFF) - (b and 0xFF))
        count += 3
        i += CHANGE_SAMPLE_STEP
    }
    return if (count == 0) 0 else (sum / count).toInt()
}

private suspend fun captureSurface(
    surface: View,
    dst: Bitmap,
    handler: Handler,
): Int =
    suspendCancellableCoroutine { cont ->
        try {
            when {
                surface is TextureView -> {
                    if (surface.isAvailable) {
                        surface.getBitmap(dst)
                        cont.resume(CAPTURE_OK)
                    } else {
                        cont.resume(CAPTURE_RETRY)
                    }
                }

                surface is SurfaceView && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                    val holderSurface = surface.holder?.surface
                    if (holderSurface != null && holderSurface.isValid) {
                        PixelCopy.request(
                            surface,
                            dst,
                            { result ->
                                cont.resume(
                                    when (result) {
                                        PixelCopy.SUCCESS -> CAPTURE_OK
                                        PixelCopy.ERROR_SOURCE_INVALID -> CAPTURE_UNSUPPORTED
                                        else -> CAPTURE_RETRY
                                    },
                                )
                            },
                            handler,
                        )
                    } else {
                        cont.resume(CAPTURE_RETRY)
                    }
                }

                else -> {
                    cont.resume(CAPTURE_RETRY)
                }
            }
        } catch (t: Throwable) {
            cont.resume(CAPTURE_RETRY)
        }
    }

private const val MIN_COLOR_POPULATION = 3
private const val MIN_PREFERRED_LUMA = 0.20f
private const val MAX_PREFERRED_LUMA = 0.86f

private fun extractColors(bmp: Bitmap): Pair<Color?, Color?> {
    val palette = Palette.from(bmp).clearFilters().generate()
    val usableSwatches =
        palette.swatches
            .filter { it.population >= MIN_COLOR_POPULATION }
            .sortedWith(
                compareByDescending<Palette.Swatch> { swatch ->
                    val hsl = swatch.hsl
                    val lumaFit = 1f - kotlin.math.abs(hsl[2].coerceIn(0f, 1f) - 0.56f)
                    (hsl[1] * 1.5f + lumaFit) * swatch.population
                },
            )
    val baseSwatch =
        usableSwatches.firstOrNull { swatch ->
            swatch.hsl[2] in MIN_PREFERRED_LUMA..MAX_PREFERRED_LUMA
        } ?: palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
    val accentSwatch =
        palette.vibrantSwatch
            ?: palette.lightVibrantSwatch
            ?: usableSwatches.firstOrNull()
            ?: palette.mutedSwatch
            ?: palette.dominantSwatch
    return baseSwatch?.let { Color(it.rgb) } to accentSwatch?.let { Color(it.rgb) }
}

private const val AMBIENT_BASE_ALPHA = 0.52f
private const val AMBIENT_FRAME_ALPHA = 0.46f
private const val AMBIENT_ACCENT_ALPHA = 0.24f
private const val AMBIENT_SCRIM_ALPHA = 0.24f

@Composable
fun VideoAmbientBackground(
    frame: ImageBitmap?,
    baseColor: Color?,
    accentColor: Color?,
    modifier: Modifier = Modifier,
) {
    val base = baseColor ?: Color.Transparent
    val accent = accentColor ?: Color.Transparent

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(base.copy(alpha = AMBIENT_BASE_ALPHA)),
        )

        if (frame != null) {
            Image(
                bitmap = frame,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                alpha = AMBIENT_FRAME_ALPHA,
            )
        }

        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(accent.copy(alpha = AMBIENT_ACCENT_ALPHA)),
        )

        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = AMBIENT_SCRIM_ALPHA)),
        )
    }
}
