package io.github.aedev.flow.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun Modifier.shimmerEffect(
    shape: Shape = RoundedCornerShape(8.dp),
    durationMillis: Int = 1200,
    delayMillis: Int = 0,
): Modifier {
    var size by remember { mutableStateOf(IntSize.Zero) }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    tween(
                        durationMillis = durationMillis,
                        delayMillis = delayMillis,
                        easing = LinearEasing,
                    ),
                repeatMode = RepeatMode.Restart,
            ),
        label = "shimmer_progress",
    )

    val surfaceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val highlightColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp).copy(alpha = 0.15f)

    val shimmerColors =
        listOf(
            surfaceColor,
            surfaceColor,
            highlightColor.copy(alpha = 0.9f),
            highlightColor,
            highlightColor.copy(alpha = 0.9f),
            surfaceColor,
            surfaceColor,
        )

    val diagonal = max(size.width.toFloat(), size.height.toFloat()) * 1.5f
    val startOffset = diagonal * progress
    val endOffset = startOffset + diagonal * 0.6f

    val brush =
        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(startOffset, startOffset * 0.5f),
            end = Offset(endOffset, endOffset * 0.5f),
        )

    return this
        .onGloballyPositioned { coordinates ->
            size = coordinates.size
        }.clip(shape)
        .background(brush, shape)
}

@Composable
fun ShimmerBone(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    delayMillis: Int = 0,
) {
    Box(
        modifier =
            modifier
                .shimmerEffect(shape = shape, delayMillis = delayMillis),
    )
}

@Composable
fun ShimmerVideoCardFullWidth(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Thumbnail
        ShimmerBone(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            shape = RoundedCornerShape(0.dp),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Channel avatar
            ShimmerBone(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                delayMillis = 80,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Title line 1
                ShimmerBone(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.92f)
                            .height(14.dp),
                    delayMillis = 120,
                )

                // Title line 2
                ShimmerBone(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.65f)
                            .height(14.dp),
                    delayMillis = 160,
                )

                Spacer(Modifier.height(2.dp))

                // Channel name + metadata
                ShimmerBone(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.50f)
                            .height(11.dp),
                    shape = RoundedCornerShape(4.dp),
                    delayMillis = 200,
                )
            }

            // Overflow menu dot
            ShimmerBone(
                modifier = Modifier.size(20.dp),
                shape = CircleShape,
                delayMillis = 220,
            )
        }
    }
}

/**
 * Grid-style shimmer card matching original video card structure.
 */
@Composable
fun ShimmerGridVideoCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Thumbnail
        ShimmerBone(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            shape = RoundedCornerShape(12.dp),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Channel avatar
            ShimmerBone(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                delayMillis = 40,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Title
                ShimmerBone(
                    modifier = Modifier.fillMaxWidth(0.95f).height(12.dp),
                    delayMillis = 80,
                )
                ShimmerBone(
                    modifier = Modifier.fillMaxWidth(0.7f).height(12.dp),
                    delayMillis = 120,
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Metadata
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ShimmerBone(
                        modifier = Modifier.width(60.dp).height(10.dp),
                        delayMillis = 160,
                    )
                    ShimmerBone(
                        modifier = Modifier.width(40.dp).height(10.dp),
                        delayMillis = 200,
                    )
                }
            }
        }
    }
}

@Composable
fun ShimmerVideoCardHorizontal(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Thumbnail with duration badge placeholder
        Box {
            ShimmerBone(
                modifier =
                    Modifier
                        .width(160.dp)
                        .aspectRatio(16f / 9f),
                shape = RoundedCornerShape(8.dp),
            )

            // Duration badge skeleton
            ShimmerBone(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .width(36.dp)
                        .height(16.dp),
                shape = RoundedCornerShape(4.dp),
                delayMillis = 150,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Title line 1
            ShimmerBone(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(13.dp),
                delayMillis = 80,
            )

            // Title line 2
            ShimmerBone(
                modifier =
                    Modifier
                        .fillMaxWidth(0.75f)
                        .height(13.dp),
                delayMillis = 120,
            )

            Spacer(Modifier.height(4.dp))

            // Channel name
            ShimmerBone(
                modifier =
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(11.dp),
                shape = RoundedCornerShape(4.dp),
                delayMillis = 160,
            )

            // View count + date
            ShimmerBone(
                modifier =
                    Modifier
                        .fillMaxWidth(0.40f)
                        .height(11.dp),
                shape = RoundedCornerShape(4.dp),
                delayMillis = 200,
            )
        }
    }
}

@Composable
fun ShimmerGridItem(
    modifier: Modifier = Modifier,
    thumbnailAspectRatio: Float = 1f,
) {
    Column(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Thumbnail
        ShimmerBone(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(thumbnailAspectRatio),
            shape = RoundedCornerShape(12.dp),
        )

        // Title
        ShimmerBone(
            modifier =
                Modifier
                    .fillMaxWidth(0.85f)
                    .height(13.dp),
            delayMillis = 80,
        )

        // Subtitle
        ShimmerBone(
            modifier =
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(11.dp),
            shape = RoundedCornerShape(4.dp),
            delayMillis = 140,
        )
    }
}

@Composable
fun ShimmerSectionTitle(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerBone(
            modifier =
                Modifier
                    .width(130.dp)
                    .height(18.dp),
            shape = RoundedCornerShape(6.dp),
        )

        ShimmerBone(
            modifier =
                Modifier
                    .width(50.dp)
                    .height(14.dp),
            shape = RoundedCornerShape(4.dp),
            delayMillis = 100,
        )
    }
}

@Composable
fun ShimmerChipRow(
    modifier: Modifier = Modifier,
    chipCount: Int = 5,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(chipCount) { index ->
            ShimmerBone(
                modifier =
                    Modifier
                        .width((60 + (index * 12) % 40).dp)
                        .height(32.dp),
                shape = RoundedCornerShape(16.dp),
                delayMillis = index * 60,
            )
        }
    }
}

@Composable
fun ShimmerMoodButton(modifier: Modifier = Modifier) {
    ShimmerBone(
        modifier =
            modifier
                .height(48.dp)
                .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    )
}

/**
 * Shimmer that mirrors the exact Music screen layout:
 *  - Filter chips row
 *  - "Quick picks" two-column grid (left album art + text + right small album art)
 *  - "Recommended" horizontal card row
 *  - "Recently played" horizontal card row
 */
@Composable
fun MusicScreenShimmerLoading(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        // Filter chips (Workout, Energize, Relax…)
        ShimmerChipRow(chipCount = 5)

        Spacer(Modifier.height(8.dp))

        // ── Quick picks ────────────────────────────────────────────────────
        ShimmerSectionTitle()

        // 4 rows that mimic [left thumb | title+artist | right small thumb]
        repeat(4) { index ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left album art square
                ShimmerBone(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(10.dp),
                    delayMillis = index * 40,
                )

                // Title + artist stacked
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ShimmerBone(
                        modifier = Modifier.fillMaxWidth(0.80f).height(13.dp),
                        delayMillis = 60 + index * 40,
                    )
                    ShimmerBone(
                        modifier = Modifier.fillMaxWidth(0.50f).height(11.dp),
                        shape = RoundedCornerShape(4.dp),
                        delayMillis = 100 + index * 40,
                    )
                }

                // Right small thumbnail
                ShimmerBone(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    delayMillis = 120 + index * 40,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Recommended ────────────────────────────────────────────────────
        ShimmerSectionTitle()

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(3) { index ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Square album art
                    ShimmerBone(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        shape = RoundedCornerShape(12.dp),
                        delayMillis = index * 60,
                    )
                    // Title
                    ShimmerBone(
                        modifier = Modifier.fillMaxWidth(0.90f).height(12.dp),
                        delayMillis = 40 + index * 60,
                    )
                    // Artist
                    ShimmerBone(
                        modifier = Modifier.fillMaxWidth(0.65f).height(10.dp),
                        shape = RoundedCornerShape(4.dp),
                        delayMillis = 80 + index * 60,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Recently played ─────────────────────────────────────────────────
        ShimmerSectionTitle()

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(3) { index ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ShimmerBone(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        shape = RoundedCornerShape(12.dp),
                        delayMillis = index * 50,
                    )
                    ShimmerBone(
                        modifier = Modifier.fillMaxWidth(0.85f).height(12.dp),
                        delayMillis = 40 + index * 50,
                    )
                    ShimmerBone(
                        modifier = Modifier.fillMaxWidth(0.60f).height(10.dp),
                        shape = RoundedCornerShape(4.dp),
                        delayMillis = 80 + index * 50,
                    )
                }
            }
        }
    }
}

@Composable
fun ShimmerHost(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        content = content,
    )
}
