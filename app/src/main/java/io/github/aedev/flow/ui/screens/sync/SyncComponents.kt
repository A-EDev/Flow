package io.github.aedev.flow.ui.screens.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Shared Material 3 building blocks for the sync flow. Every step is a header plus a body plus a
// bottom action row, so the flow reads as one screen changing rather than eight unrelated ones.

/**
 * The heading for a step: an icon in a tonal circle, a title, and one supporting line.
 * Centered, because each step is a single-focus, full-width task.
 */
@Composable
internal fun SyncStepHeader(
    icon: ImageVector,
    /** Omitted when the top app bar already carries this step's title, to avoid saying it twice. */
    title: String? = null,
    body: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(color = containerColor, contentColor = contentColor, shape = CircleShape) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(16.dp).size(28.dp))
        }
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        }
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A tappable card offering one route through the flow: leading icon, title, and the sentence that
 * explains when to pick it. Replaces the stacked bare buttons, which gave the user no way to tell
 * the two options apart.
 */
@Composable
internal fun SyncOptionCard(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(icon, contentDescription = null) },
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        )
    }
}

/** A read-only fact about the session (network address, backup note) in a low-emphasis row. */
@Composable
internal fun SyncInfoRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The step's actions. One action fills the width; two share it evenly with the confirming action on
 * the trailing side, so the decisive button always sits where the thumb expects it.
 */
@Composable
internal fun SyncActionRow(
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (dismissLabel != null && onDismiss != null) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(dismissLabel)
            }
        }
        Button(onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.weight(1f)) {
            Text(confirmLabel)
        }
    }
}

/** Frames arbitrary content in the flow's standard grouping container. */
@Composable
internal fun SyncCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Box(Modifier.fillMaxWidth()) { content() }
    }
}
