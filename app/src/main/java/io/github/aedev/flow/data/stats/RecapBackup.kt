package io.github.aedev.flow.data.stats

import io.github.aedev.flow.data.recommendation.music.MusicStatsStorage
import kotlinx.serialization.Serializable

/**
 * Both recap ledgers as one master-backup entry. A restore replaces what the device holds rather
 * than adding to it: restoring a device's own backup must not double every count.
 */
@Serializable
internal data class RecapBackup(
    val version: Int = VERSION,
    val video: VideoStatsSnapshot? = null,
    val music: MusicStatsStorage.SerializableStats? = null,
) {
    fun encode(): ByteArray = LedgerJson.encodeToString(serializer(), this).encodeToByteArray()

    companion object {
        const val VERSION = 1

        fun decode(bytes: ByteArray): RecapBackup? =
            runCatching { LedgerJson.decodeFromString(serializer(), bytes.decodeToString()) }.getOrNull()
    }
}
