package io.github.aedev.flow

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.utils.cipher.CipherDeobfuscator
import io.github.aedev.flow.utils.cipher.PipePipeNsigDecoder
import io.github.aedev.flow.utils.potoken.WebPoTokenSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The network warm-ups that only pay off when someone is about to watch: the n-decoder player id,
 * a missing visitor id, the PoToken WebView and the player script. They wait for the first activity,
 * so a process started in the background for a worker, a widget or the media controls skips them.
 */
internal class StartupWarmups(
    private val application: Application,
) {
    private val proxyApplied = CompletableDeferred<Unit>()
    private val identityRestored = CompletableDeferred<Unit>()

    /** The warm-ups wait for the first proxy application so they go through the proxy too. */
    fun onProxyApplied() {
        proxyApplied.complete(Unit)
    }

    fun onIdentityRestored() {
        identityRestored.complete(Unit)
    }

    fun startWithFirstActivity() {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    application.unregisterActivityLifecycleCallbacks(this)
                    run()
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    private fun run() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            proxyApplied.await()
            // Resolving the remote n-decoder player id is a round trip the first video would
            // otherwise pay on its path to first frame; it is persisted for 24 h.
            PipePipeNsigDecoder.warmUp()
        }
        scope.launch {
            identityRestored.await()
            // The X-Goog-Visitor-Id header keeps search from coming back empty on tablets and
            // fresh Android 16 installs (#223).
            if (YouTube.visitorData.isNullOrEmpty()) {
                YouTube
                    .visitorData()
                    .onSuccess { data -> if (!data.isNullOrEmpty()) YouTube.visitorData = data }
                    .onFailure { e -> Log.w(TAG, "visitorData fetch failed: ${e.message}") }
            }
            try {
                WebPoTokenSession.prewarm()
            } catch (e: Exception) {
                Log.w(TAG, "WebPoTokenSession prewarm failed: ${e.message}")
            }
            // A cold player script is a 3 MB download the first web-client extraction would wait on.
            CipherDeobfuscator.ensureSignatureTimestamp()
        }
    }

    private companion object {
        const val TAG = "StartupWarmups"
    }
}
