package com.flow.youtube.ui.components

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
import androidx.compose.ui.composed
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

fun Modifier.shimmerEffect(
    shape: Shape = RoundedCornerShape(8.dp),
    durationMillis: Int = 1200,
    delayMillis: Int = 0
): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = durationMillis,
                delayMillis = delayMillis,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_progress"
    )

    val surfaceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val highlightColor = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp).copy(alpha = 0.15f)

    val shimmerColors = listOf(
        surfaceColor,
        surfaceColor,
        highlightColor.copy(alpha = 0.9f),
        highlightColor,
        highlightColor.copy(alpha = 0.9f),
        surfaceColor,
        surfaceColor
    )

    val diagonal = max(size.width.toFloat(), size.height.toFloat()) * 1.5f
    val startOffset = diagonal * progress
    val endOffset = startOffset + diagonal * 0.6f

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(startOffset, startOffset * 0.5f),
        end = Offset(endOffset, endOffset * 0.5f)
    )

    this
        .onGloballyPositioned { coordinates ->
            size = coordinates.size
        }
        .clip(shape)
        .background(brush, shape)
}

@Composable
fun ShimmerBone(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    delayMillis: Int = 0
) {
    Box(
        modifier = modifier
            .shimmerEffect(shape = shape, delayMillis = delayMillis)
    )
}

@Composable
fun ShimmerVideoCardFullWidth(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Thumbnail
        ShimmerBone(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = RoundedCornerShape(0.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Channel avatar
            ShimmerBone(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                delayMillis = 80
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title line 1
                ShimmerBone(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(14.dp),
                    delayMillis = 120
                )

                // Title line 2
                ShimmerBone(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(14.dp),
                    delayMillis = 160
                )

                Spacer(Modifier.height(2.dp))

                // Channel name + metadata
                ShimmerBone(
                    modifier = Modifier
                        .fillMaxWidth(0.50f)
                        .height(11.dp),
                    shape = RoundedCornerShape(4.dp),
                    delayMillis = 200
                )
            }

            // Overflow menu dot
            ShimmerBone(
                modifier = Modifier.size(20.dp),
                shape = CircleShape,
                delayMillis = 220
            )
        }
    }
}
/**
 * Grid-style shimmer card matching original video card structure.
 */
@Composable
fun ShimmerGridVideoCard(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Thumbnail
        ShimmerBone(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Channel avatar
            ShimmerBone(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                delayMillis = 40
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Title
                ShimmerBone(
                    modifier = Modifier.fillMaxWidth(0.95f).height(12.dp),
                    delayMillis = 80
                )
                ShimmerBone(
                    modifier = Modifier.fillMaxWidth(0.7f).height(12.dp),
                    delayMillis = 120
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // Metadata
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ShimmerBone(
                        modifier = Modifier.width(60.dp).height(10.dp),
                        delayMillis = 160
                    )
                    ShimmerBone(
                        modifier = Modifier.width(40.dp).height(10.dp),
                        delayMillis = 200
                    )
                }
            }
        }
    }
}
@Composable
fun ShimmerVideoCardHorizontal(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Thumbnail with duration badge placeholder
        Box {
            ShimmerBone(
                modifier = Modifier
                    .width(160.dp)
                    .aspectRatio(16f / 9f),
                shape = RoundedCornerShape(8.dp)
            )

            // Duration badge skeleton
            ShimmerBone(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .width(36.dp)
                    .height(16.dp),
                shape = RoundedCornerShape(4.dp),
                delayMillis = 150
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Title line 1
            ShimmerBone(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(13.dp),
                delayMillis = 80
            )

            // Title line 2
            ShimmerBone(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(13.dp),
                delayMillis = 120
            )

            Spacer(Modifier.height(4.dp))

            // Channel name
            ShimmerBone(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(11.dp),
                shape = RoundedCornerShape(4.dp),
                delayMillis = 160
            )

            // View count + date
            ShimmerBone(
                modifier = Modifier
                    .fillMaxWidth(0.40f)
                    .height(11.dp),
                shape = RoundedCornerShape(4.dp),
                delayMillis = 200
            )
        }
    }
}

@Composable
fun ShimmerVideoCard(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.width(180.dp)) {
        // Thumbnail
        Box {
            ShimmerBone(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                shape = RoundedCornerShape(12.dp)
            )

            // Duration badge
            ShimmerBone(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .width(32.dp)
                    .height(14.dp),
                shape = RoundedCornerShape(3.dp),
                delayMillis = 100
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Channel avatar
            ShimmerBone(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                delayMillis = 80
            )

            Column(modifier = Modifier.weight(1f)) {
                // Title
                ShimmerBone(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                    delayMillis = 120
                )

                Spacer(Modifier.height(6.dp))

                ShimmerBone(
                    modifier = Modifier
                        .fillMaxWidth(0.70f)
                        .height(12.dp),
                    delayMillis = 160
                )

                Spacer(Modifier.height(8.dp))

                // Channel name
                ShimmerBone(
                    modifier = Modifier
                        .fillMaxWidth(0.50f)
                        .height(10.dp),
                    shape = RoundedCornerShape(3.dp),
                    delayMillis = 200
                )
            }
        }
    }
}

@Composable
fun ShimmerListItem(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Leading image/icon
        ShimmerBone(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(8.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title
            ShimmerBone(
                modifier = Modifier
                    .fillMaxWidth(0.80f)
                    .height(13.dp),
                delayMillis = 60
            )

            // Subtitle
            ShimmerBone(
                modifier = Modifier
                    .fillMaxWidth(0.50f)
                    .height(11.dp),
                shape = RoundedCornerShape(4.dp),
                delayMillis = 120
            )
        }

        // Trailing action
        ShimmerBone(
            modifier = Modifier.size(20.dp),
            shape = CircleShape,
            delayMillis = 180
        )
    }
}

@Composable
fun ShimmerGridItem(
    modifier: Modifier = Modifier,
    thumbnailAspectRatio: Float = 1f
) {
    Column(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Thumbnail
        ShimmerBone(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(thumbnailAspectRatio),
            shape = RoundedCornerShape(12.dp)
        )

        // Title
        ShimmerBone(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(13.dp),
            delayMillis = 80
        )

        // Subtitle
        ShimmerBone(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(11.dp),
            shape = RoundedCornerShape(4.dp),
            delayMillis = 140
        )
    }
}

@Composable
fun ShimmerMusicTrack(
    modifier: Modifier = Modifier,
    showTrackNumber: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track number
        if (showTrackNumber) {
            ShimmerBone(
                modifier = Modifier
                    .width(20.dp)
                    .height(12.dp),
                shape = RoundedCornerShape(3.dp)
            )
        }

        // Album art
        ShimmerBone(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(6.dp),
            delayMillis = 40
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Song title
            ShimmerBone(
                modifier = Modifier
                    .fillMaxWidth(0.70f)
                    .height(13.dp),
                delayMillis = 80
            )

            // Artist name
            ShimmerBone(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(11.dp),
                shape = RoundedCornerShape(4.dp),
                delayMillis = 120
            )
        }

        // Duration
        ShimmerBone(
            modifier = Modifier
                .width(32.dp)
                .height(11.dp),
            shape = RoundedCornerShape(4.dp),
            delayMillis = 160
        )

        // More button
        ShimmerBone(
            modifier = Modifier.size(20.dp),
            shape = CircleShape,
            delayMillis = 200
        )
    }
}

@Composable
fun ShimmerAlbumCard(
    modifier: Modifier = Modifier,
    size: Dp = 140.dp
) {
    Column(
        modifier = modifier.width(size),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Album art
        ShimmerBone(
            modifier = Modifier
                .size(size)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        // Album name
        ShimmerBone(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(12.dp),
            delayMillis = 80
        )

        // Artist
        ShimmerBone(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(10.dp),
            shape = RoundedCornerShape(3.dp),
            delayMillis = 140
        )
    }
}

@Composable
fun ShimmerSectionTitle(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBone(
            modifier = Modifier
                .width(130.dp)
                .height(18.dp),
            shape = RoundedCornerShape(6.dp)
        )

        ShimmerBone(
            modifier = Modifier
                .width(50.dp)
                .height(14.dp),
            shape = RoundedCornerShape(4.dp),
            delayMillis = 100
        )
    }
}

@Composable
fun ShimmerChipRow(
    modifier: Modifier = Modifier,
    chipCount: Int = 5
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(chipCount) { index ->
            ShimmerBone(
                modifier = Modifier
                    .width((60 + (index * 12) % 40).dp)
                    .height(32.dp),
                shape = RoundedCornerShape(16.dp),
                delayMillis = index * 60
            )
        }
    }
}

@Composable
fun ShimmerMoodButton(
    modifier: Modifier = Modifier
) {
    ShimmerBone(
        modifier = modifier
            .height(48.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun ShimmerChannelHeader(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Banner
        ShimmerBone(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            shape = RoundedCornerShape(0.dp)
        )

        Spacer(Modifier.height(12.dp))

        // Avatar
        ShimmerBone(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            delayMillis = 80
        )

        Spacer(Modifier.height(12.dp))

        // Channel name
        ShimmerBone(
            modifier = Modifier
                .width(160.dp)
                .height(18.dp),
            delayMillis = 140
        )

        Spacer(Modifier.height(8.dp))

        // Subscriber count + video count
        ShimmerBone(
            modifier = Modifier
                .width(200.dp)
                .height(12.dp),
            shape = RoundedCornerShape(4.dp),
            delayMillis = 200
        )

        Spacer(Modifier.height(12.dp))

        // Subscribe button
        ShimmerBone(
            modifier = Modifier
                .width(120.dp)
                .height(36.dp),
            shape = RoundedCornerShape(18.dp),
            delayMillis = 260
        )
    }
}

@Composable
fun ShimmerComment(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // User avatar
        ShimmerBone(
            modifier = Modifier.size(32.dp),
            shape = CircleShape
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Username + timestamp
            ShimmerBone(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(11.dp),
                shape = RoundedCornerShape(3.dp),
                delayMillis = 60
            )

            // Comment text line 1
            ShimmerBone(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .height(12.dp),
                delayMillis = 100
            )

            // Comment text line 2
            ShimmerBone(
                modifier = Modifier
                    .fillMaxWidth(0.70f)
                    .height(12.dp),
                delayMillis = 140
            )

            Spacer(Modifier.height(4.dp))

            // Like/reply buttons
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ShimmerBone(
                    modifier = Modifier
                        .width(40.dp)
                        .height(10.dp),
                    shape = RoundedCornerShape(3.dp),
                    delayMillis = 180
                )
                ShimmerBone(
                    modifier = Modifier
                        .width(40.dp)
                        .height(10.dp),
                    shape = RoundedCornerShape(3.dp),
                    delayMillis = 220
                )
            }
        }
    }
}

@Composable
fun HomeShimmerLoading(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Filter chips
        ShimmerChipRow()

        Spacer(Modifier.height(8.dp))

        // Section 1: Carousel
        ShimmerSectionTitle()

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) { index ->
                ShimmerVideoCard(
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Section 2: Full width videos
        ShimmerSectionTitle()

        repeat(3) { index ->
            ShimmerVideoCardFullWidth()
            if (index < 2) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun SearchShimmerLoading(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Filter chips
        ShimmerChipRow(chipCount = 4)

        Spacer(Modifier.height(8.dp))

        repeat(6) { index ->
            ShimmerVideoCardHorizontal()
        }
    }
}

@Composable
fun MusicLibraryShimmerLoading(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Shelf title
        ShimmerSectionTitle()

        // Album row
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                ShimmerAlbumCard(
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Track list title
        ShimmerSectionTitle()

        // Track list
        repeat(8) {
            ShimmerMusicTrack()
        }
    }
}

@Composable
fun PlayerRelatedShimmerLoading(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        ShimmerSectionTitle()

        repeat(5) {
            ShimmerVideoCardHorizontal()
        }
    }
}

@Composable
fun ChannelShimmerLoading(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        ShimmerChannelHeader()

        Spacer(Modifier.height(16.dp))

        // Tab bar
        ShimmerChipRow(chipCount = 4)

        Spacer(Modifier.height(8.dp))

        // Video grid
        repeat(4) {
            ShimmerVideoCardFullWidth()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun CommentsShimmerLoading(
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Comment count header
        ShimmerBone(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .width(100.dp)
                .height(14.dp)
        )

        repeat(6) {
            ShimmerComment()
        }
    }
}

@Composable
fun ShimmerHost(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier,
        content = content
    )
}