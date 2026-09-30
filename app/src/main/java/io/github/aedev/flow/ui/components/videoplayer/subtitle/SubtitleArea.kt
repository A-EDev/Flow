package io.github.aedev.flow.ui.components.videoplayer.subtitle

/** The part of the player, in dp, where the picture is drawn and captions are laid out. */
internal data class SubtitleArea(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * Where the picture sits in a [width] by [height] player. Only the fit mode leaves bars; zoom crops
 * the picture past the edges and fill stretches it, so both use the whole player, which keeps
 * captions on screen.
 *
 * @param resizeMode the player's mode: 0 fit, 1 fill, 2 zoom.
 */
internal fun subtitleArea(
    width: Float,
    height: Float,
    videoAspectRatio: Float?,
    resizeMode: Int,
): SubtitleArea {
    val whole = SubtitleArea(0f, 0f, width, height)
    if (resizeMode != RESIZE_MODE_FIT || videoAspectRatio == null || videoAspectRatio <= 0f || width <= 0f || height <= 0f) return whole
    return if (videoAspectRatio > width / height) {
        val pictureHeight = width / videoAspectRatio
        SubtitleArea(0f, (height - pictureHeight) / 2f, width, pictureHeight)
    } else {
        val pictureWidth = height * videoAspectRatio
        SubtitleArea((width - pictureWidth) / 2f, 0f, pictureWidth, height)
    }
}

private const val RESIZE_MODE_FIT = 0
