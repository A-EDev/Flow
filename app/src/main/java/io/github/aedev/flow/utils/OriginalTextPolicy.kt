package io.github.aedev.flow.utils

import io.github.aedev.flow.data.model.Video

/**
 * YouTube translates titles and descriptions into the request language on every response except
 * `/player`, which keeps the video's own. One choice decides which a screen keeps, so a title does
 * not switch language while it is on screen (#1230).
 */
object OriginalTextPolicy {
    /** The title a stream load starts the session with: [knownTitle] came from a translated list. */
    fun streamTitle(
        playerTitle: String?,
        knownTitle: String?,
        preferOriginal: Boolean,
    ): String? {
        val original = playerTitle?.takeIf { it.isNotBlank() }
        val known = knownTitle?.takeIf { it.isNotBlank() }
        return if (preferOriginal) original ?: known else known ?: original
    }

    /** The original description, when it is wanted and `/player` sent one. */
    fun streamDescription(
        playerDescription: String?,
        knownDescription: String,
        preferOriginal: Boolean,
    ): String = playerDescription?.takeIf { preferOriginal && it.isNotBlank() } ?: knownDescription

    /** The watch page's translated title and description, kept off a video that shows the original. */
    fun keepOriginalText(
        merged: Video,
        before: Video,
        preferOriginal: Boolean,
    ): Video =
        if (!preferOriginal) {
            merged
        } else {
            merged.copy(
                title = before.title.ifBlank { merged.title },
                description = before.description.ifBlank { merged.description },
            )
        }
}
