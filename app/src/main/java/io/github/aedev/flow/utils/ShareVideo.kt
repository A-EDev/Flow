package io.github.aedev.flow.utils

import android.content.Context
import android.content.Intent
import io.github.aedev.flow.R

/**
 * The canonical watch link for a video, optionally seeked to [positionSeconds].
 */
fun youtubeWatchUrl(
    videoId: String,
    positionSeconds: Long? = null,
): String {
    val watchUrl = "https://www.youtube.com/watch?v=$videoId"
    return if (positionSeconds == null) watchUrl else "$watchUrl&t=${positionSeconds}s"
}

/**
 * The chooser intent every "share this video" affordance raises. [linkOnly] is the user's
 * "share without text" preference: on, the payload is the bare link; off, it is the link under a
 * one-line introduction naming the video.
 */
fun shareVideoIntent(
    context: Context,
    videoId: String,
    title: String,
    linkOnly: Boolean,
): Intent {
    val shareText =
        if (linkOnly) {
            context.getString(R.string.share_link_only_template, videoId)
        } else {
            context.getString(R.string.check_out_video_template, title, videoId)
        }
    val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
    return Intent.createChooser(shareIntent, context.getString(R.string.share_video))
}

fun shareVideo(
    context: Context,
    videoId: String,
    title: String,
    linkOnly: Boolean,
) {
    context.startActivity(shareVideoIntent(context, videoId, title, linkOnly))
}
