package io.github.aedev.flow.widget.nowplaying

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import io.github.aedev.flow.widget.core.component.WIDGET_PROGRESS_MAX
import io.github.aedev.flow.widget.core.component.widgetProgressAt
import io.github.aedev.flow.widget.core.state.NowPlayingSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Moves the Now Playing progress bar while a track plays. The wave's own motion is the launcher's; its
 * length along the bar only changes when the widget is rendered again, and a Glance widget has one
 * layout per size, which partial updates cannot reach (RemoteViews.mergeRemoteViews only merges the
 * top level). So this re-renders, at the bar's visible resolution and no faster.
 *
 * It runs only while playing with a Now Playing widget placed, suspends while the screen is off, and
 * skips ticks while Flow itself is in front, when the home screen cannot be seen and a live widget
 * session would tax the app's frames. Every player event restarts it from a fresh snapshot.
 */
internal class NowPlayingProgressTicker(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun follow(snapshot: NowPlayingSnapshot?) {
        job?.cancel()
        job = null
        if (snapshot == null || !snapshot.isPlaying || snapshot.durationMs <= 0L) return
        job = scope.launch { run(snapshot) }
    }

    private suspend fun run(snapshot: NowPlayingSnapshot) {
        if (GlanceAppWidgetManager(context).getGlanceIds(NowPlayingWidget::class.java).isEmpty()) return
        val interval = tickIntervalMs(snapshot.durationMs, snapshot.speed)
        while (true) {
            delay(interval)
            awaitScreenOn()
            if (isAppInFront()) continue
            val now = SystemClock.elapsedRealtime()
            clockState.value = now
            NowPlayingWidget().updateAll(context)
            val progress =
                widgetProgressAt(snapshot.positionMs, snapshot.durationMs, snapshot.capturedAtElapsedMs, true, snapshot.speed, now)
            if (progress >= WIDGET_PROGRESS_MAX) return
        }
    }

    private fun isAppInFront(): Boolean {
        val state = ActivityManager.RunningAppProcessInfo().also(ActivityManager::getMyMemoryState)
        return state.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private suspend fun awaitScreenOn() {
        val power = context.getSystemService(PowerManager::class.java) ?: return
        if (power.isInteractive) return
        suspendCancellableCoroutine { continuation ->
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        context.unregisterReceiver(this)
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_SCREEN_ON),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            continuation.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
        }
    }

    companion object {
        private val clockState = MutableStateFlow(SystemClock.elapsedRealtime())

        /** The time the bar was last placed at; the widget reads it so each tick recomposes. */
        val clock: StateFlow<Long> = clockState.asStateFlow()

        /**
         * One tick per hundredth of the track, between two and ten seconds: about 2 px of bar per tick
         * on a phone card, which reads as steady motion under the scrolling wave.
         */
        internal fun tickIntervalMs(
            durationMs: Long,
            speed: Float,
        ): Long = (durationMs / TICKS_PER_TRACK / speed.coerceAtLeast(0.25f)).toLong().coerceIn(MIN_TICK_MS, MAX_TICK_MS)

        private const val TICKS_PER_TRACK = 100
        private const val MIN_TICK_MS = 2_000L
        private const val MAX_TICK_MS = 10_000L
    }
}
