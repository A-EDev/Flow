package io.github.aedev.flow.player

/** Finished downloads, so a queue that advances onto one plays the file instead of streaming it. */
fun interface LocalCopySource {
    /** Path of a finished, still-present download of [videoId], or null. */
    suspend fun localCopyPath(videoId: String): String?
}
