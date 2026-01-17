package com.flow.youtube.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.youtube.R
import com.flow.youtube.utils.UpdateInfo

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(28.dp),
        title = null,
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Hero Icon (Notification Logo)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_notification_logo),
                        contentDescription = "Update",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 2. Headline
                Text(
                    text = "New Update Available",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "${updateInfo.version}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-1).sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 3. Markdown Changelog Container
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Header
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_notification_logo),
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "RELEASE NOTES",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Scrollable Markdown Content
                        Column(
                            modifier = Modifier
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            MarkdownChangelogText(
                                markdown = updateInfo.changelog,
                                textColor = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    Icons.Default.CloudDownload, 
                    contentDescription = null, 
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Update Flow", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Maybe later", 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    )
}

/**
 * A lightweight Markdown parser for Release Notes.
 * Handles Headers (#), Bullet points (- or *), and Bold (**text**).
 */
@Composable
fun MarkdownChangelogText(markdown: String, textColor: Color) {
    val styledText = remember(markdown) {
        buildAnnotatedString {
            val lines = markdown.split("\n")
            
            lines.forEach { line ->
                val trimmed = line.trim()
                
                when {
                    // 1. Headers (### or ## or #)
                    trimmed.startsWith("#") -> {
                        val text = trimmed.trimStart('#').trim()
                        withStyle(SpanStyle(
                            fontWeight = FontWeight.Bold, 
                            fontSize = 16.sp,
                            color = textColor
                        )) {
                            append(text)
                        }
                        append("\n")
                    }
                    // 2. Bullet Points (- or *)
                    trimmed.startsWith("-") || trimmed.startsWith("*") -> {
                        val text = trimmed.substring(1).trim()
                        append("\t•  ") // Add tab and bullet
                        parseBold(text, textColor) // Check for bold inside list item
                        append("\n")
                    }
                    // 3. Normal Text
                    else -> {
                        if (trimmed.isNotEmpty()) {
                            parseBold(trimmed, textColor)
                            append("\n")
                        }
                    }
                }
            }
        }
    }

    Text(
        text = styledText,
        style = MaterialTheme.typography.bodyMedium,
        color = textColor.copy(alpha = 0.9f),
        lineHeight = 22.sp
    )
}

// Helper to parse **bold** text inside a string
fun androidx.compose.ui.text.AnnotatedString.Builder.parseBold(text: String, baseColor: Color) {
    val parts = text.split("**")
    parts.forEachIndexed { index, part ->
        // If index is odd, it was inside **, so make it bold
        if (index % 2 == 1) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                append(part)
            }
        } else {
            withStyle(SpanStyle(color = baseColor.copy(alpha = 0.8f))) {
                append(part)
            }
        }
    }
}