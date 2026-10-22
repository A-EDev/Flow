package io.github.aedev.flow.ui.screens.shorts

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.shorts.ShortsPlayerPool
import io.github.aedev.flow.ui.components.shared.MediaKeyAction
import io.github.aedev.flow.ui.components.shared.mediaKeyboardShortcuts
import kotlinx.coroutines.launch

/**
 * Keyboard shortcuts for the Shorts reel. Up and Down page between Shorts, as on the web, so the
 * volume stays with the device's own keys here.
 */
@Composable
internal fun Modifier.shortsKeyboardShortcuts(
    enabled: Boolean,
    pagerState: PagerState,
    shortIdAt: (Int) -> String?,
    playerPreferences: PlayerPreferences,
): Modifier {
    val scope = rememberCoroutineScope()
    val seekSeconds by playerPreferences.doubleTapSeekSeconds.collectAsState(initial = 10)

    fun page(offset: Int): Boolean {
        val target = pagerState.currentPage + offset
        if (target !in 0 until pagerState.pageCount) return false
        scope.launch { pagerState.animateScrollToPage(target) }
        return true
    }

    return mediaKeyboardShortcuts(enabled) { action ->
        val pool = ShortsPlayerPool.getInstance()
        val player = pool.ownedPlayer(pagerState.currentPage, shortIdAt(pagerState.currentPage))
        when (action) {
            MediaKeyAction.TogglePlayback -> {
                pool.togglePlayPause()
                true
            }

            MediaKeyAction.SeekBack, MediaKeyAction.SeekForward -> {
                val step = if (action == MediaKeyAction.SeekForward) seekSeconds * 1000L else -seekSeconds * 1000L
                player?.let { pool.seekTo((it.currentPosition + step).coerceIn(0L, it.duration.coerceAtLeast(0L))) }
                player != null
            }

            MediaKeyAction.VolumeUp, MediaKeyAction.Previous -> {
                page(-1)
            }

            MediaKeyAction.VolumeDown, MediaKeyAction.Next -> {
                page(1)
            }

            is MediaKeyAction.JumpTo -> {
                val duration = player?.duration?.takeIf { it > 0 }
                duration?.let { pool.seekTo(it * action.tenths / 10) }
                duration != null
            }

            else -> {
                false
            }
        }
    }
}
