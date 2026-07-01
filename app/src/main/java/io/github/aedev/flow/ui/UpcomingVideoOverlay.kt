package io.github.aedev.flow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun UpcomingVideoOverlay(
    title: String,
    releaseTimeMs: Long?,
    isReminderSet: Boolean,
    onToggleReminder: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nowMs by remember(releaseTimeMs) { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(releaseTimeMs) {
        if (releaseTimeMs == null) return@LaunchedEffect
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .widthIn(max = 360.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.8f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(
                        if (releaseTimeMs != null) R.string.upcoming_video_starts_in
                        else R.string.premiere_soon
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
                )
            }

            if (releaseTimeMs != null) {
                Text(
                    text = formatCountdown(releaseTimeMs - nowMs),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (releaseTimeMs != null) {
                FilledTonalButton(
                    onClick = onToggleReminder,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Icon(
                        imageVector = if (isReminderSet) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        text = stringResource(
                            if (isReminderSet) R.string.upcoming_video_reminder_enabled
                            else R.string.upcoming_video_reminder_action
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun formatCountdown(remainingMs: Long): String {
    if (remainingMs <= 0L) return "00:00"
    val totalSeconds = remainingMs / 1000L
    val days = totalSeconds / 86_400L
    val hours = (totalSeconds % 86_400L) / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        days > 0L -> String.format(Locale.US, "%dd %02dh %02dm", days, hours, minutes)
        hours > 0L -> String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        else -> String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
