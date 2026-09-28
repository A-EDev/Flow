package io.github.aedev.flow.widget.core.action

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** A transport command a widget button sends to the music session. */
enum class WidgetPlaybackCommand(
    val action: String,
) {
    PLAY_PAUSE("io.github.aedev.flow.widget.PLAY_PAUSE"),
    NEXT("io.github.aedev.flow.widget.NEXT"),
    PREVIOUS("io.github.aedev.flow.widget.PREVIOUS"),
    ;

    @OptIn(UnstableApi::class)
    internal fun run(player: Player) {
        when (this) {
            // Prepares from IDLE and restarts from ENDED, where a bare play() does nothing.
            PLAY_PAUSE -> Util.handlePlayPauseButtonAction(player)

            NEXT -> player.seekToNext()

            PREVIOUS -> player.seekToPrevious()
        }
    }

    fun intent(context: Context): Intent = Intent(context, WidgetPlaybackReceiver::class.java).setAction(action)

    fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(context, ordinal, intent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    companion object {
        fun from(action: String?): WidgetPlaybackCommand? = entries.firstOrNull { it.action == action }
    }
}

/**
 * Receives the widget's transport buttons. They are platform views rather than Glance actions so
 * the launcher can draw their pressed shape, which leaves them their own broadcast to send.
 */
class WidgetPlaybackReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val command = WidgetPlaybackCommand.from(intent.action) ?: return
        val pending = goAsync()
        scope.launch {
            try {
                withMusicController(context) { command.run(it) }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
