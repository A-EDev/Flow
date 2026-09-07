package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val BadgeHorizontalPadding = 6.dp
private val BadgeVerticalPadding = 2.dp
private val BadgeInset = 2.dp

/**
 * Which group each channel belongs to, resolved once for the whole app.
 *
 * Provided empty unless the badge preference is on, so every avatar can ask without any of them
 * owning a lookup or a collector.
 */
@Immutable
class ChannelGroupLabels(
    private val byChannelId: Map<String, String>,
) {
    fun labelFor(channelId: String?): String? = channelId?.takeIf { it.isNotBlank() }?.let(byChannelId::get)

    companion object {
        val Empty = ChannelGroupLabels(emptyMap())
    }
}

val LocalChannelGroupLabels = staticCompositionLocalOf { ChannelGroupLabels.Empty }

/**
 * The group name over a channel or artist avatar. Renders nothing when the preference is off or
 * the channel is in no group, so call sites need no condition of their own.
 */
@Composable
fun BoxScope.ChannelGroupBadge(
    channelId: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomCenter,
) {
    val label = LocalChannelGroupLabels.current.labelFor(channelId) ?: return

    Surface(
        modifier =
            modifier
                .align(alignment)
                .padding(BadgeInset),
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier.padding(
                    horizontal = BadgeHorizontalPadding,
                    vertical = BadgeVerticalPadding,
                ),
        )
    }
}
