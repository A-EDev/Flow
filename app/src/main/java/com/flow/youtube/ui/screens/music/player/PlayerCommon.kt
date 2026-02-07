package com.flow.youtube.ui.screens.music.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class SkipDirection {
    NEXT, PREVIOUS
}

@Composable
fun AnimatedSkipIndicators(
    direction: SkipDirection?,
    onAnimationComplete: () -> Unit
) {
    direction?.let {
        LaunchedEffect(direction) {
            delay(500)
            onAnimationComplete()
        }
        
        val slideIn = remember { Animatable(if (direction == SkipDirection.NEXT) 1f else -1f) }
        
        LaunchedEffect(direction) {
            slideIn.animateTo(
                targetValue = 0f,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = if (direction == SkipDirection.NEXT) 
                Alignment.CenterEnd 
            else 
                Alignment.CenterStart
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (direction == SkipDirection.NEXT)
                            Icons.Filled.SkipNext
                        else
                            Icons.Filled.SkipPrevious,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PillButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    // Deprecated: Used MinimalActionButton in PlayerControls instead (Maybe i will reuse later)
    val buttonBgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val contentColor = MaterialTheme.colorScheme.onSurface
    
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = buttonBgColor,
        onClick = onClick,
        modifier = Modifier.height(40.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).toInt()
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}
