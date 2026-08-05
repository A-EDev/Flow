package io.github.aedev.flow.service

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSessionService
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.utils.FlowCrashHandler

@UnstableApi
internal fun MediaSessionService.recordForegroundStartFailures(tag: String) {
    setListener(
        object : MediaSessionService.Listener {
            @RequiresApi(Build.VERSION_CODES.S)
            override fun onForegroundServiceStartNotAllowedException() {
                FlowCrashHandler.recordPhase(tag, "foreground start refused by platform")
                PlayerDiagnostics.logWarning(
                    tag,
                    "startForegroundService refused: app is in the background without an " +
                        "FGS-start allowance; playback notification stays non-foreground",
                )
            }
        },
    )
}
