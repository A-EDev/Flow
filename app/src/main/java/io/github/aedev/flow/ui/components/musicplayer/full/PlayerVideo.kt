package io.github.aedev.flow.ui.components.musicplayer.full

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.aedev.flow.ui.components.shared.clipToCornerRadius
import io.github.aedev.flow.ui.components.shared.flowPlayerViewLayout

/**
 * The music video in the artwork box. It draws under the artwork, which steps aside once
 * [onShowingChange] reports the first frame, so the cover holds the box until the picture arrives.
 * Mounting it hands the player a surface, which is what turns video decoding on.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun PlayerVideo(
    player: Player,
    cornerRadius: Dp,
    onShowingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    val reportShowing by rememberUpdatedState(onShowingChange)
    val view =
        remember {
            (LayoutInflater.from(context).inflate(flowPlayerViewLayout(), null) as PlayerView).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setKeepContentOnPlayerReset(false)
                artworkDisplayMode = PlayerView.ARTWORK_DISPLAY_MODE_OFF
                subtitleView?.visibility = View.GONE
            }
        }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onRenderedFirstFrame() = reportShowing(true)

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) = reportShowing(false)

                override fun onTracksChanged(tracks: Tracks) {
                    if (!tracks.isTypeSelected(C.TRACK_TYPE_VIDEO)) reportShowing(false)
                }
            }
        view.player = player
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            view.player = null
            reportShowing(false)
        }
    }

    AndroidView(
        factory = { view },
        update = { it.clipToCornerRadius(radiusPx) },
        modifier = modifier,
    )
}
