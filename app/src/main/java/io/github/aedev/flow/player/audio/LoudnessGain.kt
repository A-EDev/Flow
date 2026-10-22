package io.github.aedev.flow.player.audio

import io.github.aedev.flow.innertube.models.response.PlayerResponse
import kotlin.math.pow

/** YouTube's loudness metadata as a playback gain: loud tracks are turned down, quiet ones stay as they are. */
object LoudnessGain {
    private const val TARGET_LKFS = -14.0
    private const val MIN_GAIN_DB = -20f

    fun gainDb(
        audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        formatLoudnessDb: Double?,
    ): Float? {
        val gainDb =
            audioConfig?.perceptualLoudnessDb?.let { (audioConfig.loudnessTargetLkfs ?: TARGET_LKFS) - it }
                ?: audioConfig?.loudnessDb?.let { -it }
                ?: formatLoudnessDb?.let { -it }
                ?: return null
        return gainDb.toFloat().coerceIn(MIN_GAIN_DB, 0f)
    }

    /** The player volume for [gainDb]; 1 when there is nothing to apply. */
    fun volume(gainDb: Float?): Float = gainDb?.let { 10.0.pow(it / 20.0).toFloat() } ?: 1f
}
