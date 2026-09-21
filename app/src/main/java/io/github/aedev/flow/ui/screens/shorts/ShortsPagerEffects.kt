package io.github.aedev.flow.ui.screens.shorts

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.ShortVideo
import io.github.aedev.flow.player.shorts.ShortsPlayerPool
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "ShortsPagerEffects"

/** A reel the user swiped past in under a second was never really shown. */
private const val SHOWN_DWELL_MS = 1_000L

/**
 * Wifi or not, kept current by the platform callback. Seeded synchronously rather than defaulting
 * to false: the ViewModel's prefetch reads the transport synchronously too, and the two must agree
 * or they key the playback-stream cache differently and the prefetch is wasted.
 */
@Composable
internal fun rememberIsOnWifi(): Boolean {
    val context = LocalContext.current
    var isWifi by remember { mutableStateOf(isOnWifi(context)) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        fun update() {
            isWifi = manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        update()
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    caps: NetworkCapabilities,
                ) = update()

                override fun onLost(network: Network) = update()

                override fun onAvailable(network: Network) = update()
            }
        manager.registerDefaultNetworkCallback(callback)
        onDispose { manager.unregisterNetworkCallback(callback) }
    }
    return isWifi
}

/**
 * Prepares the settled reel and its neighbours whenever the pager rests, and re-resolves them when
 * the quality preference changes underneath a running reel.
 */
@Composable
internal fun ShortsPagerPlaybackEffects(
    pagerState: PagerState,
    shorts: List<ShortVideo>,
    targetHeight: Int?,
    playerPreferences: PlayerPreferences,
    viewModel: ShortsViewModel,
) {
    val context = LocalContext.current
    val settledShortId = shorts.getOrNull(pagerState.settledPage)?.id

    LaunchedEffect(pagerState.settledPage, settledShortId, targetHeight) {
        val height = targetHeight ?: return@LaunchedEffect
        val settled = pagerState.settledPage
        val playerPool = ShortsPlayerPool.getInstance()
        playerPool.initialize(context)
        playerPool.setCurrentVideo(shorts.getOrNull(settled))
        val preferredLang = playerPreferences.preferredAudioLanguage.first()

        playerPool.activatePlayer(settled)

        // Awaited, not launched alongside the neighbours. Each resolve mints a BotGuard PoToken, and
        // those serialise on one process-wide WebView — so firing all of them at once can leave the
        // short the user is looking at queued behind two it cannot see.
        shorts.getOrNull(settled)?.let { current ->
            prepareReel(playerPool, viewModel, settled, current, height, preferredLang, shouldPlay = true)
            viewModel.loadShortDetails(current.id)
            launch {
                delay(SHOWN_DWELL_MS)
                viewModel.onReelShown(current.id)
            }
        }

        playerPool.releaseUnusedPlayers(settled)

        shorts.getOrNull(settled + 1)?.let { next ->
            launch { prepareReel(playerPool, viewModel, settled + 1, next, height, preferredLang, shouldPlay = false) }
        }
        shorts.getOrNull(settled - 1)?.let { previous ->
            launch { prepareReel(playerPool, viewModel, settled - 1, previous, height, preferredLang, shouldPlay = false) }
        }
        // Two ahead: resolved only, not handed to a player. Last so it never competes with the visible short.
        shorts.getOrNull(settled + 2)?.let { preload ->
            launch { runCatching { viewModel.getPlaybackStreams(preload.id, height, preferredLang) } }
        }
    }

    val previousTargetHeight = remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(targetHeight) {
        val height = targetHeight ?: return@LaunchedEffect
        val previous = previousTargetHeight.value
        previousTargetHeight.value = height
        // The first non-null value is the preference loading, not the user changing it. The settle
        // effect above already prepares at that height.
        if (previous == null || height == previous) return@LaunchedEffect

        val settled = pagerState.settledPage
        val playerPool = ShortsPlayerPool.getInstance()
        val preferredLang = playerPreferences.preferredAudioLanguage.first()

        shorts.getOrNull(settled)?.let { current -> reloadReel(playerPool, viewModel, settled, current, height, preferredLang) }
        shorts.getOrNull(settled + 1)?.let { next ->
            launch { reloadReel(playerPool, viewModel, settled + 1, next, height, preferredLang) }
        }
        shorts.getOrNull(settled - 1)?.let { previousShort ->
            launch { reloadReel(playerPool, viewModel, settled - 1, previousShort, height, preferredLang) }
        }
    }
}

private suspend fun prepareReel(
    playerPool: ShortsPlayerPool,
    viewModel: ShortsViewModel,
    index: Int,
    short: ShortVideo,
    targetHeight: Int,
    preferredLang: String,
    shouldPlay: Boolean,
) {
    try {
        val streams = viewModel.getPlaybackStreams(short.id, targetHeight, preferredLang)
        if (streams == null) {
            Log.w(TAG, "No stream URL resolved for ${short.id}")
            return
        }
        playerPool.prepare(
            index = index,
            videoId = short.id,
            videoUrl = streams.videoUrl,
            audioUrl = streams.audioUrl,
            shouldPlay = shouldPlay,
            videoDashManifest = streams.videoDashManifest,
            audioDashManifest = streams.audioDashManifest,
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to prepare player for ${short.id}", e)
    }
}

private suspend fun reloadReel(
    playerPool: ShortsPlayerPool,
    viewModel: ShortsViewModel,
    index: Int,
    short: ShortVideo,
    targetHeight: Int,
    preferredLang: String,
) {
    try {
        val streams = viewModel.getPlaybackStreams(short.id, targetHeight, preferredLang) ?: return
        playerPool.reloadWithVideoUrl(index, short.id, streams.videoUrl, streams.videoDashManifest)
    } catch (e: Exception) {
        Log.e(TAG, "Quality change: failed to reload ${short.id}", e)
    }
}
