package io.github.aedev.flow.player.recovery

internal data class ClearedMediaRecoverySnapshot(
    val videoId: String,
    val positionMs: Long,
    val playWhenReady: Boolean,
    val localFilePath: String?
)

internal class ClearedMediaRecoveryState {
    private var pendingSnapshot: ClearedMediaRecoverySnapshot? = null

    fun capture(
        videoId: String?,
        positionMs: Long,
        playWhenReady: Boolean,
        localFilePath: String?
    ) {
        if (videoId == null) return
        pendingSnapshot = ClearedMediaRecoverySnapshot(
            videoId = videoId,
            positionMs = positionMs.coerceAtLeast(0L),
            playWhenReady = playWhenReady,
            localFilePath = localFilePath
        )
    }

    fun pendingFor(videoId: String?): ClearedMediaRecoverySnapshot? =
        pendingSnapshot?.takeIf { it.videoId == videoId }

    fun complete(snapshot: ClearedMediaRecoverySnapshot) {
        if (pendingSnapshot === snapshot) {
            pendingSnapshot = null
        }
    }

    fun clear() {
        pendingSnapshot = null
    }
}
