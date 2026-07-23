package io.github.aedev.flow.ui.tv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.components.ShimmerBone
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

/** TV-sized skeleton for a shelf of video cards, reusing the app's shimmer system. */
@Composable
fun TvShimmerRow(
    modifier: Modifier = Modifier,
    cardCount: Int = 5,
    showHeader: Boolean = true,
) {
    val dimens = LocalTvDimens.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (showHeader) {
            ShimmerBone(
                modifier = Modifier
                    .padding(horizontal = dimens.overscanHorizontal)
                    .width(220.dp)
                    .height(26.dp),
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = dimens.overscanHorizontal, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
        ) {
            repeat(cardCount) { index ->
                Column(
                    modifier = Modifier.width(dimens.videoCardWidth),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ShimmerBone(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        shape = MaterialTheme.shapes.medium,
                        delayMillis = index * 80,
                    )
                    ShimmerBone(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(18.dp),
                        delayMillis = index * 80,
                    )
                    ShimmerBone(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(14.dp),
                        delayMillis = index * 80,
                    )
                }
            }
        }
    }
}
