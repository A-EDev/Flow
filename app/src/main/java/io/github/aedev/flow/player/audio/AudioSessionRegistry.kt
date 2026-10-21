package io.github.aedev.flow.player.audio

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.data.audio.eq.EqualizerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Announces each player's audio session to equalizer apps, which attach their effects only to
 * sessions they have been told about; Media3 sends no such broadcast itself.
 *
 * Several players can be alive at once, and MusicFX follows only the session a package announced
 * last, so a session is announced when it starts playing, or when it appears while nothing else
 * plays. The active session is the one announced last.
 */
@Singleton
class AudioSessionRegistry
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val open = LinkedHashMap<Int, Int>()
        private val playing = LinkedHashSet<Int>()
        private val _activeSessionId = MutableStateFlow(AudioEffect.ERROR_BAD_VALUE)

        /** The session announced most recently and still open, or [AudioEffect.ERROR_BAD_VALUE]. */
        val activeSessionId: StateFlow<Int> = _activeSessionId.asStateFlow()

        /** The content type [sessionId] was opened with, or null when it is not open. */
        @Synchronized
        fun contentTypeOf(sessionId: Int): Int? = open[sessionId]

        @Synchronized
        fun open(
            sessionId: Int,
            contentType: Int,
        ) {
            if (sessionId <= 0 || open[sessionId] == contentType) return
            open[sessionId] = contentType
            val active = _activeSessionId.value
            if (active == sessionId || active <= 0 || active !in playing) announce(sessionId)
        }

        @Synchronized
        fun playing(sessionId: Int) {
            if (sessionId !in open) return
            playing.remove(sessionId)
            playing.add(sessionId)
            if (_activeSessionId.value != sessionId) announce(sessionId)
        }

        @Synchronized
        fun paused(sessionId: Int) {
            playing.remove(sessionId)
        }

        @Synchronized
        fun close(sessionId: Int) {
            playing.remove(sessionId)
            val contentType = open.remove(sessionId) ?: return
            broadcast(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, sessionId, contentType)
            if (_activeSessionId.value != sessionId) return
            val next = playing.lastOrNull() ?: open.keys.lastOrNull()
            if (next != null) announce(next) else _activeSessionId.value = AudioEffect.ERROR_BAD_VALUE
        }

        private fun announce(sessionId: Int) {
            val contentType = open[sessionId] ?: return
            _activeSessionId.value = sessionId
            broadcast(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, sessionId, contentType)
        }

        private fun broadcast(
            action: String,
            sessionId: Int,
            contentType: Int,
        ) {
            runCatching {
                context.sendBroadcast(
                    Intent(action)
                        .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                        .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                        .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, contentType),
                )
            }.onFailure { Log.w(TAG, "Could not announce audio session $sessionId", it) }
        }

        private companion object {
            const val TAG = "AudioSessionRegistry"
        }
    }

/** For the legacy player singletons and the backup code, which Hilt does not construct. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AudioEffectsEntryPoint {
    fun equalizerRepository(): EqualizerRepository

    fun audioSessionRegistry(): AudioSessionRegistry
}
