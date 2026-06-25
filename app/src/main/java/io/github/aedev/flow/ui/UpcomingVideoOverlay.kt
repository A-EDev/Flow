package io.github.aedev.flow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
            .padding(horizontal = 24.dp)
            .widthIn(max = 420.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.78f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(42.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Text(
                text = stringResource(R.string.upcoming_video_overlay_title),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f),
                textAlign = TextAlign.Center
            )
            Text(
                text = releaseTimeMs?.let { formatCountdown(it - nowMs) }
                    ?: stringResource(R.string.premiere_soon),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (releaseTimeMs != null) {
                FilledTonalButton(onClick = onToggleReminder) {
                    Icon(
                        imageVector = if (isReminderSet) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (isReminderSet) R.string.upcoming_video_reminder_enabled
                            else R.string.upcoming_video_reminder_action
                        )
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
