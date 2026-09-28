package io.github.aedev.flow.widget.nowplaying

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** The Now Playing layouts, one per size the widget declares. */
internal enum class NowPlayingLayout(
    val size: DpSize,
) {
    SMALL(DpSize(110.dp, 48.dp)),
    STRIP(DpSize(220.dp, 48.dp)),
    SQUARE(DpSize(110.dp, 110.dp)),
    CARD(DpSize(220.dp, 110.dp)),
    POSTER(DpSize(180.dp, 220.dp)),
    ;

    companion object {
        val sizes: Set<DpSize> = entries.mapTo(LinkedHashSet()) { it.size }

        /** The layout Glance picks for a widget of [size]: the largest declared size that fits. */
        fun forSize(size: DpSize): NowPlayingLayout =
            entries
                .filter { it.size.width <= size.width && it.size.height <= size.height }
                .maxByOrNull { it.size.width.value * it.size.height.value }
                ?: SMALL

        /** The artwork edge, in dp, that the largest layout among [sizes] draws. */
        fun artworkDpFor(sizes: List<DpSize>): Float =
            sizes
                .maxOfOrNull { size ->
                    when (forSize(size)) {
                        POSTER -> min(size.width.value - POSTER_INSET_DP, size.height.value - POSTER_CHROME_DP)
                        CARD -> CARD_ART_DP
                        SQUARE -> SQUARE_ART_DP
                        STRIP -> STRIP_ART_DP
                        SMALL -> 0f
                    }
                }?.coerceIn(STRIP_ART_DP, MAX_ART_DP)
                ?: CARD_ART_DP

        const val STRIP_ART_DP = 52f
        const val SQUARE_ART_DP = 56f
        const val CARD_ART_DP = 64f
        private const val POSTER_INSET_DP = 24f
        private const val POSTER_CHROME_DP = 140f

        // Past this the bitmap costs more of the RemoteViews budget than the launcher can show.
        private const val MAX_ART_DP = 256f
    }
}
