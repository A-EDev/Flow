package com.flow.youtube.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.flow.youtube.R
import com.flow.youtube.utils.UpdateInfo

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit
) {
    val context = LocalContext.current
    
    val displayedChangelog = remember(updateInfo.changelog, updateInfo.version) {
        if (updateInfo.version.contains("1.4.0") || updateInfo.changelog.isBlank()) {
            try {
                val filename = if (updateInfo.version.startsWith("v")) updateInfo.version else "v${updateInfo.version}"
                try {
                    context.assets.open("changelog/$filename.txt").bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    // If specific version file not found, try to find the latest
                    val files = context.assets.list("changelog") ?: emptyArray()
                    val latestFile = files.filter { it.endsWith(".txt") }
                        .sortedWith(compareByDescending { it })
                        .firstOrNull()
                    
                    if (latestFile != null) {
                        context.assets.open("changelog/$latestFile").bufferedReader().use { it.readText() }
                    } else {
                        updateInfo.changelog
                    }
                }
            } catch (e: Exception) {
                updateInfo.changelog
            }
        } else {
            updateInfo.changelog
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss), // Dismiss on scrim click
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable(enabled = false) {}, // Prevent click propagation
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E22)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_notification_logo),
                            contentDescription = "Update",
                            tint = MaterialTheme.colorScheme.primary, // Using primary color as before
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "New Update Available", 
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${updateInfo.version} is now available", 
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = Color.LightGray.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 3. Version Info Box 
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF131316),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            // Header row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RELEASE NOTES", 
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = updateInfo.version,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Changelog content
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 140.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                MarkdownChangelogText(
                                    markdown = displayedChangelog,
                                    textColor = Color.LightGray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 4. Buttons (Vertical Stack - Reference Design)
                    Button(
                        onClick = onUpdate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary, // Original primary color
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudDownload, 
                            contentDescription = null, 
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Update Flow", // Restored original text
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.1f), // Subtle background for secondary
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Text(
                            text = "Maybe later", // Restored original text
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
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
                            fontSize = 14.sp,
                            color = textColor
                        )) {
                            append("• $text") // Treat headers as bullets in small view
                        }
                        append("\n")
                    }
                    // 2. Bullet Points (- or *)
                    trimmed.startsWith("-") || trimmed.startsWith("*") -> {
                        val text = trimmed.substring(1).trim()
                        append(" • ") // Simple bullet
                        parseBold(text, textColor) 
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
        style = MaterialTheme.typography.bodySmall,
        color = textColor.copy(alpha = 0.8f),
        lineHeight = 18.sp
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
            withStyle(SpanStyle(color = baseColor)) {
                append(part)
            }
        }
    }
}