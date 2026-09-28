package io.github.aedev.flow.widget.nowplaying

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** The card's control sizes: three circles-and-square that must fit the text column side by side. */
internal data class CardControls(
    val side: Dp,
    val play: Dp,
    val gap: Dp,
) {
    val width: Dp get() = side * 2 + play + gap * 2
}

/** How the card lays out at one real widget size. [art] is zero when there is no room for it. */
internal data class CardMetrics(
    val inset: Dp,
    val art: Dp,
    val controls: CardControls,
    val showProgress: Boolean,
)

/**
 * The controls get their width first and the artwork takes what is left, since a clipped button is
 * worse than a smaller cover. On narrow phone cells the controls step down before the art shrinks
 * below [MinArt]; below that the art gives way entirely.
 */
internal fun cardMetrics(size: DpSize): CardMetrics {
    val roomy = size.height >= RoomyHeight
    val inset = if (roomy) 12.dp else 8.dp
    val column = size.width - inset * 2 - ArtGap
    val maxArt = minOf(size.height - inset * 2, MaxArt)
    val candidates = if (roomy) listOf(Roomy, Compact, Minimal) else listOf(Minimal)
    val fitting = candidates.firstOrNull { minOf(maxArt, column - it.width) >= MinArt } ?: Minimal
    val art = minOf(maxArt, column - fitting.width)
    return CardMetrics(
        inset = inset,
        art = if (art >= MinArt) art else 0.dp,
        controls = fitting,
        showProgress = roomy,
    )
}

/** The gap between the artwork and the text column. */
internal val ArtGap = 16.dp

private val RoomyHeight = 140.dp
private val MinArt = 64.dp
private val MaxArt = 180.dp
private val Roomy = CardControls(side = 48.dp, play = 56.dp, gap = 12.dp)
private val Compact = CardControls(side = 44.dp, play = 52.dp, gap = 8.dp)
private val Minimal = CardControls(side = 40.dp, play = 48.dp, gap = 6.dp)
