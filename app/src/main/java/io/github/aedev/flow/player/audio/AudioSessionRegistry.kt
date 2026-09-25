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
 */
@Singleton
class AudioSessionRegistry
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val open = LinkedHashMap<Int, Int>()
        private val _activeSessionId = MutableStateFlow(AudioEffect.ERROR_BAD_VALUE)

        /** The session opened most recently and still open, or [AudioEffect.ERROR_BAD_VALUE]. */
        val activeSessionId: StateFlow<Int> = _activeSessionId.asStateFlow()

        @Synchronized
        fun open(
            sessionId: Int,
            contentType: Int,
        ) {
            if (sessionId <= 0 || open[sessionId] == contentType) return
            open.remove(sessionId)
            open[sessionId] = contentType
            _activeSessionId.value = sessionId
            broadcast(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, sessionId, contentType)
        }

        @Synchronized
        fun close(sessionId: Int) {
            val contentType = open.remove(sessionId) ?: return
            _activeSessionId.value = open.keys.lastOrNull() ?: AudioEffect.ERROR_BAD_VALUE
            broadcast(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, sessionId, contentType)
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
