package io.github.aedev.flow.ui.screens.shorts

import android.app.Activity
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.github.aedev.flow.player.PictureInPictureHelper
import io.github.aedev.flow.player.shorts.ShortsPlayerPool

private const val TAG = "ShortsPipEffects"

@Composable
internal fun ShortsPipActionEffect() {
    val activity = LocalContext.current as? Activity

    val pool = remember { ShortsPlayerPool.getInstance() }

    DisposableEffect(activity, pool) {
        if (activity == null) return@DisposableEffect onDispose { }

        fun restateWindow(isPlaying: Boolean) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            PictureInPictureHelper.updatePipParams(
                activity = activity,
                aspectRatio = pool.activeVideoAspectRatio() ?: PictureInPictureHelper.currentVideoAspectRatio,
                isPlaying = isPlaying,
            )
        }

        val receiver =
            PictureInPictureHelper.createPipActionReceiver(
                onPlay = {
                    pool.play()
                    restateWindow(isPlaying = true)
                },
                onPause = {
                    pool.pause()
                    restateWindow(isPlaying = false)
                },
                onClose = { pool.pauseAll() },
            )

        ContextCompat.registerReceiver(
            activity,
            receiver,
            PictureInPictureHelper.getPipIntentFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        onDispose {
            runCatching { activity.unregisterReceiver(receiver) }
                .onFailure { Log.w(TAG, "Failed to unregister the Shorts PiP receiver", it) }
        }
    }
}
