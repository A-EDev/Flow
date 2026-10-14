package io.github.aedev.flow.ui.screens.player

/** What the player knows about a video when it picks a starting speed. */
internal data class PlaybackSpeedContext(
    val videoId: String?,
    val channelId: String?,
    val musicVideoType: String? = null,
    val knownMusic: Boolean = false,
) {
    companion object {
        val Unknown = PlaybackSpeedContext(videoId = null, channelId = null)
    }
}

internal sealed interface SpeedDecision {
    /** Applies to this video only; the speed it replaced comes back on the next one. */
    data class Override(
        val speed: Float,
    ) : SpeedDecision

    data class Remembered(
        val speed: Float,
    ) : SpeedDecision

    data object Keep : SpeedDecision
}

internal object PlaybackSpeedPolicy {
    private const val MUSIC_CATEGORY = "Music"

    fun decide(
        isMusic: Boolean,
        channelSpeed: Float?,
        musicAtNormalSpeed: Boolean,
        rememberSpeed: Boolean,
        rememberedSpeed: Float,
    ): SpeedDecision =
        when {
            channelSpeed != null -> SpeedDecision.Override(channelSpeed)
            isMusic && musicAtNormalSpeed -> SpeedDecision.Override(1f)
            rememberSpeed -> SpeedDecision.Remembered(rememberedSpeed)
            else -> SpeedDecision.Keep
        }

    /** Categories come from an English-locale request, so the name is stable. */
    fun isMusic(
        musicVideoType: String?,
        category: String?,
        openedAsMusic: Boolean,
    ): Boolean = openedAsMusic || !musicVideoType.isNullOrBlank() || category.equals(MUSIC_CATEGORY, ignoreCase = true)
}
