package io.github.aedev.flow.ui.screens.settings.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.recommendation.FlowPersona

/**
 * The entry card to the Flow persona dashboard, lifted unchanged from the old settings screen. Its
 * redesign (and the gradient it still draws) belongs to the persona pass, which is out of scope for
 * the settings revamp.
 */
@Composable
internal fun PersonaEntryCard(
    persona: FlowPersona?,
    onOpen: () -> Unit,
    onReset: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp),
        ) {
            // 1. Background Layer (Gradient)
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(
                            brush =
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primaryContainer,
                                        ),
                                ),
                        ),
            )
            // 2. Background Decor (Abstract Shapes)
            Canvas(modifier = Modifier.matchParentSize()) {
                // Top Right Circle
                drawCircle(
                    color = Color.White.copy(alpha = 0.1f),
                    radius = size.width * 0.5f,
                    center = Offset(size.width, 0f),
                )
                // Bottom Left Blob
                drawCircle(
                    color = Color.Black.copy(alpha = 0.05f),
                    radius = size.width * 0.3f,
                    center = Offset(0f, size.height),
                )
            }

            // 2. Huge Emoji Icon (Watermark style)
            if (persona != null) {
                Text(
                    text = persona.icon, // e.g., 🤿 or 🧭
                    fontSize = 120.sp,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 20.dp, y = 20.dp)
                            .alpha(0.15f),
                )
            }

            // 4. Main Content
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    // Badge
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_active_learning),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }

                    // Reset Button (Subtle)
                    IconButton(
                        onClick = onReset,
                        modifier =
                            Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.2f), CircleShape),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.settings_reset_everything),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                // Persona Info
                if (persona != null) {
                    Column {
                        Text(
                            text = stringResource(persona.titleRes),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(persona.descriptionRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    // Loading State
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.settings_analyzing_interactions),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

                // Bottom CTA
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_view_analytics),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
