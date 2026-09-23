package io.github.aedev.flow.ui.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private val FootnotePadding = 16.dp
private val FootnoteTopPadding = 6.dp
private val NoticePadding = 16.dp
private val NoticeSpacing = 12.dp
private val NoticeIconSize = 20.dp
private val NoticeTopPadding = 8.dp
private val NoticeLineSpacing = 2.dp

/** Explanatory text under a group, lined up with the row titles above it. */
@Composable
fun SettingsFootnote(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = FootnotePadding, end = FootnotePadding, top = FootnoteTopPadding),
    )
}

/**
 * A standalone note on a page: what a setting costs, a restart it needs, a risk it carries. Drawn on
 * a neutral container so it reads as information, not as a control. [isWarning] switches it to the
 * error container for the few notes that describe something that can go wrong.
 */
@Composable
fun SettingsNotice(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    title: String? = null,
    isWarning: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().padding(top = NoticeTopPadding),
        shape = MaterialTheme.shapes.large,
        color = if (isWarning) colors.errorContainer else colors.surfaceContainer,
        contentColor = if (isWarning) colors.onErrorContainer else colors.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(NoticePadding),
            horizontalArrangement = Arrangement.spacedBy(NoticeSpacing),
            verticalAlignment = if (title == null) Alignment.CenterVertically else Alignment.Top,
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(NoticeIconSize))
            }
            Column(verticalArrangement = Arrangement.spacedBy(NoticeLineSpacing)) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isWarning) colors.onErrorContainer else colors.onSurface,
                    )
                }
                Text(text = text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
