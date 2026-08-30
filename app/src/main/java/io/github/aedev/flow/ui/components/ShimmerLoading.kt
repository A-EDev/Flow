package io.github.aedev.flow.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion

private const val SKELETON_PULSE_MILLIS = 900
private const val SKELETON_MIN_ALPHA = 0.55f

@Composable
fun Modifier.shimmerEffect(
    shape: Shape = MaterialTheme.shapes.small,
    durationMillis: Int = SKELETON_PULSE_MILLIS,
    delayMillis: Int = 0,
): Modifier {
    val reduceMotion = rememberFlowReduceMotion()
    val boneColor = MaterialTheme.colorScheme.surfaceContainerHighest

    if (reduceMotion) {
        return this
            .clip(shape)
            .background(boneColor, shape)
    }

    val transition = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by
        transition.animateFloat(
            initialValue = SKELETON_MIN_ALPHA,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = durationMillis.coerceAtLeast(1),
                            delayMillis = delayMillis.coerceAtLeast(0),
                            easing = FlowMotion.EnterEasing,
                        ),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "skeletonAlpha",
        )

    return this
        .graphicsLayer { this.alpha = alpha }
        .clip(shape)
        .background(boneColor, shape)
}

@Composable
fun ShimmerBone(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    delayMillis: Int = 0,
) {
    Box(
        modifier = modifier.shimmerEffect(shape = shape, delayMillis = delayMillis),
    )
}

@Composable
fun ShimmerVideoCardFullWidth(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        ShimmerBone(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            shape = RectangleShape,
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShimmerBone(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                delayMillis = 80,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShimmerBone(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.92f)
                            .height(14.dp),
                    delayMillis = 120,
                )
                ShimmerBone(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.65f)
                            .height(14.dp),
                    delayMillis = 160,
                )
                Spacer(Modifier.height(2.dp))
                ShimmerBone(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.50f)
                            .height(11.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    delayMillis = 200,
                )
            }

            ShimmerBone(
                modifier = Modifier.size(20.dp),
                shape = CircleShape,
                delayMillis = 220,
            )
        }
    }
}

@Composable
fun ShimmerGridVideoCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShimmerBone(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            shape = MaterialTheme.shapes.medium,
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ShimmerBone(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                delayMillis = 40,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ShimmerBone(
                    modifier = Modifier.fillMaxWidth(0.95f).height(12.dp),
                    delayMillis = 80,
                )
                ShimmerBone(
                    modifier = Modifier.fillMaxWidth(0.7f).height(12.dp),
                    delayMillis = 120,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
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
        Box {
            ShimmerBone(
                modifier =
                    Modifier
                        .width(160.dp)
                        .aspectRatio(16f / 9f),
                shape = MaterialTheme.shapes.medium,
            )
            ShimmerBone(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .width(36.dp)
                        .height(16.dp),
                shape = MaterialTheme.shapes.extraSmall,
                delayMillis = 150,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ShimmerBone(
                modifier = Modifier.fillMaxWidth().height(13.dp),
                delayMillis = 80,
            )
            ShimmerBone(
                modifier = Modifier.fillMaxWidth(0.75f).height(13.dp),
                delayMillis = 120,
            )
            Spacer(Modifier.height(4.dp))
            ShimmerBone(
                modifier = Modifier.fillMaxWidth(0.55f).height(11.dp),
                shape = MaterialTheme.shapes.extraSmall,
                delayMillis = 160,
            )
            ShimmerBone(
                modifier = Modifier.fillMaxWidth(0.40f).height(11.dp),
                shape = MaterialTheme.shapes.extraSmall,
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
        ShimmerBone(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(thumbnailAspectRatio),
            shape = MaterialTheme.shapes.medium,
        )
        ShimmerBone(
            modifier = Modifier.fillMaxWidth(0.85f).height(13.dp),
            delayMillis = 80,
        )
        ShimmerBone(
            modifier = Modifier.fillMaxWidth(0.55f).height(11.dp),
            shape = MaterialTheme.shapes.extraSmall,
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
            modifier = Modifier.width(130.dp).height(18.dp),
            shape = MaterialTheme.shapes.small,
        )
        ShimmerBone(
            modifier = Modifier.width(50.dp).height(14.dp),
            shape = MaterialTheme.shapes.extraSmall,
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
                shape = MaterialTheme.shapes.extraLarge,
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
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
fun MusicScreenShimmerLoading(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        ShimmerChipRow(chipCount = 5)
        Spacer(Modifier.height(8.dp))
        ShimmerSectionTitle()

        repeat(4) { index ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShimmerBone(
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    delayMillis = index * 40,
                )
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
                        shape = MaterialTheme.shapes.extraSmall,
                        delayMillis = 100 + index * 40,
                    )
                }
                ShimmerBone(
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    delayMillis = 120 + index * 40,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        ShimmerSectionTitle()
        ShimmerAlbumRow(staggerMillis = 60)

        Spacer(Modifier.height(16.dp))
        ShimmerSectionTitle()
        ShimmerAlbumRow(staggerMillis = 50)
    }
}

@Composable
private fun ShimmerAlbumRow(staggerMillis: Int) {
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
                    shape = MaterialTheme.shapes.medium,
                    delayMillis = index * staggerMillis,
                )
                ShimmerBone(
                    modifier = Modifier.fillMaxWidth(0.90f).height(12.dp),
                    delayMillis = 40 + index * staggerMillis,
                )
                ShimmerBone(
                    modifier = Modifier.fillMaxWidth(0.65f).height(10.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    delayMillis = 80 + index * staggerMillis,
                )
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
