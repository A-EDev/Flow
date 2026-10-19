package io.github.aedev.flow.data.repository

import io.github.aedev.flow.data.model.DeArrowContent
import io.github.aedev.flow.data.model.DeArrowResult
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Turns a branding answer into what a card shows, the way the DeArrow extension does: the server's
 * first usable submission wins, a winning "original" keeps YouTube's version, and a video with no
 * thumbnail submission shows the frame at the server's random time for it, so every DeArrow user
 * sees the same one.
 */
internal object DeArrowBrandingPolicy {
    const val THUMBNAIL_URL = "https://dearrow-thumb.ajay.app/api/v1/getThumbnail"

    fun resolve(
        content: DeArrowContent,
        videoId: String,
        fallbackDurationSeconds: Int,
        useRandomFrame: Boolean,
        isLiveOrUpcoming: Boolean,
    ): DeArrowResult? {
        val title =
            content.titles
                .firstOrNull { it.locked || it.votes >= 0 }
                ?.takeUnless { it.original }
                ?.title
                ?.let(::stripCaseMarkers)
                ?.takeIf { it.isNotBlank() }

        val thumbnail = content.thumbnails.firstOrNull { it.locked || it.votes >= 0 }
        val frameTime =
            when {
                thumbnail != null -> thumbnail.takeUnless { it.original }?.timestamp
                useRandomFrame && !isLiveOrUpcoming -> randomFrameTime(content, fallbackDurationSeconds)
                else -> null
            }
        val thumbnailUrl = frameTime?.let { frameUrl(videoId, it) }

        return if (title == null && thumbnailUrl == null) null else DeArrowResult(title, thumbnailUrl)
    }

    private fun randomFrameTime(
        content: DeArrowContent,
        fallbackDurationSeconds: Int,
    ): Double? {
        val randomTime = content.randomTime ?: return null
        val duration =
            content.videoDuration?.takeIf { it > 0 }
                ?: fallbackDurationSeconds.takeIf { it > 0 }?.toDouble()
                ?: return null
        return randomTime * duration
    }

    fun frameUrl(
        videoId: String,
        timeSeconds: Double,
    ): String =
        THUMBNAIL_URL
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("videoID", videoId)
            .addQueryParameter("time", timeSeconds.toString())
            .build()
            .toString()
}

/**
 * DeArrow submitters prefix a word with `>` to keep its casing when titles are auto-formatted; the
 * marker is formatting metadata, never part of the title.
 */
internal fun stripCaseMarkers(title: String): String =
    buildString(title.length) {
        var atWordStart = true
        title.forEachIndexed { i, c ->
            val isMarker =
                c == '>' && atWordStart &&
                    title.getOrNull(i + 1)?.isWhitespace() == false
            if (!isMarker) append(c)
            atWordStart = c.isWhitespace()
        }
    }
