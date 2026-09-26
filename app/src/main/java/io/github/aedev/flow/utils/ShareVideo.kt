package io.github.aedev.flow.utils

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.aedev.flow.MainActivity
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

fun youtubeShortsUrl(videoId: String): String = "https://youtube.com/shorts/$videoId"

fun youtubeMusicWatchUrl(videoId: String): String = "https://music.youtube.com/watch?v=$videoId"

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
    isShort: Boolean = false,
): Intent {
    val shareText =
        when {
            linkOnly && isShort -> youtubeShortsUrl(videoId)
            linkOnly -> context.getString(R.string.share_link_only_template, videoId)
            isShort -> context.getString(R.string.check_out_short_template, title, videoId)
            else -> context.getString(R.string.check_out_video_template, title, videoId)
        }
    return textShareChooser(shareText, title.takeUnless { linkOnly }, context.getString(R.string.share_video))
}

/** The song counterpart of [shareVideoIntent], linking to YouTube Music. */
fun shareSongIntent(
    context: Context,
    videoId: String,
    title: String,
    artist: String,
    linkOnly: Boolean,
): Intent {
    val shareText =
        if (linkOnly) {
            youtubeMusicWatchUrl(videoId)
        } else {
            context.getString(R.string.share_message_template, title, artist, videoId)
        }
    return textShareChooser(shareText, title.takeUnless { linkOnly }, context.getString(R.string.share_song))
}

/** Shares a YouTube playlist's link, with its [title] as the subject unless only the link is shared. */
fun sharePlaylist(
    context: Context,
    playlistId: String,
    title: String,
    linkOnly: Boolean,
) = shareLink(context, "https://www.youtube.com/playlist?list=$playlistId", title, linkOnly)

/**
 * Shares [url] as plain text through the system sheet. [title] goes in the subject, which some
 * apps paste above the link, so it is left out when the user shares links without text.
 */
fun shareLink(
    context: Context,
    url: String,
    title: String,
    linkOnly: Boolean = false,
) {
    context.startActivity(textShareChooser(url, title.takeUnless { linkOnly }, context.getString(R.string.share)))
}

private fun textShareChooser(
    text: String,
    subject: String?,
    chooserTitle: String,
): Intent {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            putExtra(Intent.EXTRA_TEXT, text)
        }
    return Intent.createChooser(send, chooserTitle)
}

/**
 * Shares a playlist file, which another Flow imports when it is opened. Flow itself is left out of
 * the chooser so the sheet never offers to import the file back into the playlist it came from.
 */
fun sharePlaylistFile(
    context: Context,
    file: Uri,
    title: String,
) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = PLAYLIST_FILE_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, file)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = ClipData.newRawUri(title, file)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    val chooser =
        Intent.createChooser(send, context.getString(R.string.share)).apply {
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))
        }
    context.startActivity(chooser)
}

const val PLAYLIST_FILE_MIME_TYPE = "application/json"

fun shareVideo(
    context: Context,
    videoId: String,
    title: String,
    linkOnly: Boolean,
    isShort: Boolean = false,
) {
    context.startActivity(shareVideoIntent(context, videoId, title, linkOnly, isShort))
}
