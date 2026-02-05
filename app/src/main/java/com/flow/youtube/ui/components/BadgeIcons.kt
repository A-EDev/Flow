package com.flow.youtube.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.offline.Download

object BadgeIcon {
    
    private val BadgeSize = 14.dp
    private val BadgeSpacing = 4.dp

    @Composable
    fun Favorite(
        modifier: Modifier = Modifier
    ) {
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = "Liked",
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier
                .size(BadgeSize)
                .padding(end = BadgeSpacing)
        )
    }

    @Composable
    fun Explicit(
        modifier: Modifier = Modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .padding(end = BadgeSpacing)
                .size(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = "E",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
 
    @Composable
    fun Download(
        state: Int?,
        modifier: Modifier = Modifier
    ) {
        val icon = when (state) {
            Download.STATE_QUEUED -> Icons.Outlined.Schedule
            Download.STATE_DOWNLOADING -> Icons.Outlined.Downloading
            Download.STATE_COMPLETED -> Icons.Outlined.DownloadDone
            else -> return 
        }
        
        val tint = when (state) {
            Download.STATE_COMPLETED -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        
        Icon(
            imageVector = icon,
            contentDescription = when (state) {
                Download.STATE_QUEUED -> "Queued for download"
                Download.STATE_DOWNLOADING -> "Downloading"
                Download.STATE_COMPLETED -> "Downloaded"
                else -> null
            },
            tint = tint,
            modifier = modifier
                .size(BadgeSize)
                .padding(end = BadgeSpacing)
        )
    }

    @Composable
    fun InLibrary(
        modifier: Modifier = Modifier
    ) {
        Icon(
            imageVector = Icons.Outlined.DownloadDone,
            contentDescription = "In library",
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier
                .size(BadgeSize)
                .padding(end = BadgeSpacing)
        )
    }

    @Composable
    fun ChartPosition(
        position: Int,
        change: String? = null,
        modifier: Modifier = Modifier
    ) {
        val color = when {
            position <= 3 -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        
        Text(
            text = "#$position",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = modifier.padding(end = BadgeSpacing)
        )
    }

    @Composable
    fun ChartChange(
        change: String?,
        modifier: Modifier = Modifier
    ) {
        if (change == null) return
        
        val (symbol, color) = when {
            change.contains("UP", ignoreCase = true) || change.startsWith("+") -> "↑" to Color(0xFF4CAF50)
            change.contains("DOWN", ignoreCase = true) || change.startsWith("-") -> "↓" to Color(0xFFF44336)
            else -> "−" to MaterialTheme.colorScheme.onSurfaceVariant
        }
        
        Text(
            text = symbol,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = modifier.padding(end = BadgeSpacing)
        )
    }
}
