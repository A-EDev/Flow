package io.github.aedev.flow.ui.tv.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

/**
 * Transport chrome for the TV player: top metadata band and bottom controls
 * stack, both on solid translucent surface fills (no gradient scrims).
 * Placed inside the player's root Box.
 */
@Composable
fun BoxScope.TvPlayerOverlay(
    visible: Boolean,
    title: String,
    channelName: String,
    isLive: Boolean,
    bottomContent: @Composable ColumnScope.() -> Unit,
) {
    val dimens = LocalTvDimens.current
    val scrimColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.TopCenter),
        enter = fadeIn() + slideInVertically { -it / 3 },
        exit = fadeOut() + slideOutVertically { -it / 3 },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(scrimColor),
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = dimens.overscanHorizontal,
                    vertical = dimens.overscanVertical,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (isLive) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ) {
                            Text(
                                text = stringResource(R.string.status_live),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (channelName.isNotBlank()) {
                    Text(
                        text = channelName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = fadeIn() + slideInVertically { it / 3 },
        exit = fadeOut() + slideOutVertically { it / 3 },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(scrimColor)
                .padding(
                    start = dimens.overscanHorizontal,
                    end = dimens.overscanHorizontal,
                    top = 20.dp,
                    bottom = dimens.overscanVertical,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = bottomContent,
        )
    }
}
