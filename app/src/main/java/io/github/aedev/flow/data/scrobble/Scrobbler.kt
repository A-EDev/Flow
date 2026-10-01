package io.github.aedev.flow.data.scrobble

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.BuildConfig
import io.github.aedev.flow.data.localmedia.LocalMediaIds
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.network.ProxyAwareClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends what the music player plays to every signed-in service. Finished listens are queued on the
 * device first and sent by [ScrobbleWorker], so listening offline loses nothing.
 */
@Singleton
class Scrobbler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val store: ScrobbleStore,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val http = ProxyAwareClient()
        private val lastFm = AudioscrobblerClient(AudioscrobblerClient.LASTFM_URL, http::get)
        private val libreFm = AudioscrobblerClient(AudioscrobblerClient.LIBREFM_URL, http::get)
        private val listenBrainz = ListenBrainzClient(http::get)

        /** The key Last.fm calls are signed with: the viewer's own when they turned it on, else the build's. */
        fun lastFmKeys(settings: ScrobbleSettings): AudioscrobblerKeys? =
            if (settings.ownKeyEnabled) {
                settings.ownKeys.takeIf { it.isUsable }
            } else {
                BundledLastFmKeys.takeIf { it.isUsable }
            }

        fun onNowPlaying(
            track: MusicTrack,
            durationMs: Long,
        ) {
            scope.launch {
                val settings = store.current()
                if (!settings.nowPlaying || !settings.accepts(track)) return@launch
                val entry = ScrobbleRules.entryFor(track, durationMs, System.currentTimeMillis()) ?: return@launch
                settings.accounts.forEach { (service, account) ->
                    val outcome =
                        when (service) {
                            ScrobbleService.LASTFM -> lastFmKeys(settings)?.let { lastFm.nowPlaying(entry, account, it) }
                            ScrobbleService.LIBREFM -> libreFm.nowPlaying(entry, account, AudioscrobblerClient.LibreFmKeys)
                            ScrobbleService.LISTENBRAINZ -> listenBrainz.nowPlaying(entry, account)
                        }
                    if (outcome == SendOutcome.SignedOut) store.setAccount(service, null)
                }
            }
        }

        fun onListened(
            track: MusicTrack,
            durationMs: Long,
            playedMs: Long,
            startedAtMs: Long,
        ) {
            if (!ScrobbleRules.counts(durationMs, playedMs)) return
            scope.launch {
                val settings = store.current()
                if (settings.accounts.isEmpty() || !settings.accepts(track)) return@launch
                val entry = ScrobbleRules.entryFor(track, durationMs, startedAtMs) ?: return@launch
                settings.accounts.keys.forEach { store.enqueue(it, entry) }
                ScrobbleWorker.enqueue(context)
            }
        }

        /** Sends every queued listen; false when something is left to retry. */
        suspend fun flush(): Boolean {
            val settings = store.current()
            var done = true
            settings.accounts.forEach { (service, account) ->
                while (true) {
                    val batch = store.pending(service).take(batchSize(service))
                    if (batch.isEmpty()) break
                    when (send(service, batch, account, settings)) {
                        SendOutcome.Sent -> {
                            store.drop(service, batch)
                        }

                        SendOutcome.Retry -> {
                            done = false
                            break
                        }

                        SendOutcome.SignedOut -> {
                            Log.w(TAG, "$service signed the account out")
                            store.setAccount(service, null)
                            break
                        }
                    }
                }
            }
            return done
        }

        suspend fun signIn(
            service: ScrobbleService,
            userName: String,
            passwordOrToken: String,
        ): Result<ScrobbleAccount> {
            val result =
                when (service) {
                    ScrobbleService.LASTFM -> {
                        val keys = lastFmKeys(store.current()) ?: return Result.failure(IllegalStateException("No API key"))
                        lastFm.signIn(userName.trim(), passwordOrToken, keys)
                    }

                    ScrobbleService.LIBREFM -> {
                        libreFm.signIn(userName.trim(), passwordOrToken, AudioscrobblerClient.LibreFmKeys)
                    }

                    ScrobbleService.LISTENBRAINZ -> {
                        listenBrainz.signIn(passwordOrToken.trim())
                    }
                }
            result.onSuccess { store.setAccount(service, it) }
            return result
        }

        suspend fun signOut(service: ScrobbleService) = store.setAccount(service, null)

        private suspend fun send(
            service: ScrobbleService,
            batch: List<ScrobbleEntry>,
            account: ScrobbleAccount,
            settings: ScrobbleSettings,
        ): SendOutcome =
            when (service) {
                ScrobbleService.LASTFM -> lastFmKeys(settings)?.let { lastFm.scrobble(batch, account, it) } ?: SendOutcome.SignedOut
                ScrobbleService.LIBREFM -> libreFm.scrobble(batch, account, AudioscrobblerClient.LibreFmKeys)
                ScrobbleService.LISTENBRAINZ -> listenBrainz.scrobble(batch, account)
            }

        private fun batchSize(service: ScrobbleService) =
            if (service == ScrobbleService.LISTENBRAINZ) ListenBrainzClient.MAX_BATCH else AudioscrobblerClient.MAX_BATCH

        private fun ScrobbleSettings.accepts(track: MusicTrack) = scrobbleLocal || !LocalMediaIds.isLocal(track.videoId)

        private companion object {
            const val TAG = "Scrobbler"
            val BundledLastFmKeys = AudioscrobblerKeys(BuildConfig.LASTFM_API_KEY, BuildConfig.LASTFM_API_SECRET)
        }
    }
