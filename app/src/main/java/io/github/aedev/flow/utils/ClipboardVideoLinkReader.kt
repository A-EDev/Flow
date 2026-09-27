package io.github.aedev.flow.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log

/**
 * Reads the clipboard for a single-video link so Flow can pick up where another player left off.
 *
 * The clipboard is only read while Flow is in the foreground: Android 10+ denies background
 * reads, and reading anything the user copied is a foreground-only affordance anyway.
 *
 * [consumeVideoLink] is deliberately not a plain "what is on the clipboard" call. A link stays
 * on the clipboard for as long as the user keeps it there, so every entry point that could
 * re-open it has to go through the same once-only bookkeeping here, otherwise returning from the
 * player would navigate straight back into it. The bookkeeping is process-scoped, which is what
 * the behaviour should be: a link is picked up once per Flow session, and picking it up again
 * means the user copied it again.
 */
object ClipboardVideoLinkReader {
    private const val TAG = "ClipboardVideoLinkReader"
    private const val MAX_CLIP_TEXT_LENGTH = 2_000

    private val handledTextLock = Any()

    /** Last clipboard text this process already acted on, so a stale link cannot re-open itself. */
    private var lastHandledText: String? = null

    /**
     * @return the video the clipboard points at, or null when there is nothing new to open.
     *   The clip is marked handled as soon as it is a link, whether or not the caller plays it.
     */
    fun consumeVideoLink(context: Context): ParsedVideoLink? {
        val text = readPrimaryClipText(context) ?: return null
        val parsed = YouTubeLinkParser.parseVideoLink(text) ?: return null
        synchronized(handledTextLock) {
            if (lastHandledText == text) return null
            lastHandledText = text
        }
        Log.i(TAG, "Clipboard link resolved to ${parsed.videoId} (short=${parsed.isShort})")
        return parsed
    }

    private fun readPrimaryClipText(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null || !clipboard.hasPrimaryClip()) return null
        val clip: ClipData = clipboard.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf {
            it.isNotBlank() && it.length <= MAX_CLIP_TEXT_LENGTH
        }
    }
}
