package io.github.aedev.flow.player.media

internal class SabrCapacityPolicy {
    private var videoId: String? = null
    private val rejectedVideoItags = mutableSetOf<Int>()

    fun reject(
        videoId: String,
        videoItag: Int,
    ) {
        selectVideo(videoId)
        rejectedVideoItags.add(videoItag)
    }

    fun allows(
        videoId: String,
        videoItag: Int,
    ): Boolean {
        selectVideo(videoId)
        return videoItag !in rejectedVideoItags
    }

    private fun selectVideo(id: String) {
        if (videoId != id) {
            videoId = id
            rejectedVideoItags.clear()
        }
    }
}
