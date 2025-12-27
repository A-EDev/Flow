package com.flow.youtube.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SubtitleCue(
    val startTime: Long,
    val endTime: Long,
    val text: String
)

@Composable
fun SubtitleOverlay(
    currentPosition: Long,
    subtitles: List<SubtitleCue>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (!enabled || subtitles.isEmpty()) return
    
    // Find the current subtitle based on playback position
    val currentSubtitle = remember(currentPosition) {
        subtitles.find { cue ->
            currentPosition in cue.startTime..cue.endTime
        }
    }
    
    AnimatedVisibility(
        visible = currentSubtitle != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        currentSubtitle?.let { cue ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = cue.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black,
                            offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                            blurRadius = 4f
                        )
                    ),
                    modifier = Modifier
                        .background(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Parse SRT subtitle format
 * Example:
 * 1
 * 00:00:00,000 --> 00:00:02,000
 * Hello World
 */
fun parseSRT(srtContent: String): List<SubtitleCue> {
    val cues = mutableListOf<SubtitleCue>()
    val blocks = srtContent.trim().split("\n\n")
    
    for (block in blocks) {
        val lines = block.trim().split("\n")
        if (lines.size < 3) continue
        
        try {
            // Parse time line (line 1)
            val timeLine = lines[1]
            val times = timeLine.split(" --> ")
            if (times.size != 2) continue
            
            val startTime = parseTime(times[0].trim())
            val endTime = parseTime(times[1].trim())
            
            // Parse text (line 2+)
            val text = lines.drop(2).joinToString("\n")
            
            cues.add(SubtitleCue(startTime, endTime, text))
        } catch (e: Exception) {
            // Skip malformed cues
            continue
        }
    }
    
    return cues
}

/**
 * Parse VTT subtitle format
 * Example:
 * WEBVTT
 * 
 * 00:00:00.000 --> 00:00:02.000
 * Hello World
 */
fun parseVTT(vttContent: String): List<SubtitleCue> {
    val cues = mutableListOf<SubtitleCue>()
    val lines = vttContent.trim().split("\n")
    
    var i = 0
    // Skip WEBVTT header and metadata
    while (i < lines.size && !lines[i].contains("-->")) {
        i++
    }
    
    while (i < lines.size) {
        val line = lines[i].trim()
        
        if (line.contains("-->")) {
            try {
                val times = line.split(" --> ")
                if (times.size != 2) {
                    i++
                    continue
                }
                
                val startTime = parseTime(times[0].trim())
                val endTime = parseTime(times[1].trim())
                
                // Collect text lines until empty line
                val textLines = mutableListOf<String>()
                i++
                while (i < lines.size && lines[i].trim().isNotEmpty() && !lines[i].contains("-->")) {
                    textLines.add(lines[i].trim())
                    i++
                }
                
                val text = textLines.joinToString("\n")
                    .replace("<[^>]*>".toRegex(), "") // Remove HTML tags
                    .trim()
                
                if (text.isNotEmpty()) {
                    cues.add(SubtitleCue(startTime, endTime, text))
                }
            } catch (e: Exception) {
                i++
                continue
            }
        } else {
            i++
        }
    }
    
    return cues
}

/**
 * Parse time string to milliseconds
 * Supports formats:
 * - HH:MM:SS,mmm (SRT)
 * - HH:MM:SS.mmm (VTT)
 */
private fun parseTime(timeString: String): Long {
    val cleaned = timeString.replace(",", ".")
    val parts = cleaned.split(":")
    
    return when (parts.size) {
        3 -> {
            val hours = parts[0].toLongOrNull() ?: 0
            val minutes = parts[1].toLongOrNull() ?: 0
            val secondsParts = parts[2].split(".")
            val seconds = secondsParts[0].toLongOrNull() ?: 0
            val millis = if (secondsParts.size > 1) {
                secondsParts[1].padEnd(3, '0').take(3).toLongOrNull() ?: 0
            } else {
                0
            }
            
            (hours * 3600000) + (minutes * 60000) + (seconds * 1000) + millis
        }
        2 -> {
            val minutes = parts[0].toLongOrNull() ?: 0
            val secondsParts = parts[1].split(".")
            val seconds = secondsParts[0].toLongOrNull() ?: 0
            val millis = if (secondsParts.size > 1) {
                secondsParts[1].padEnd(3, '0').take(3).toLongOrNull() ?: 0
            } else {
                0
            }
            
            (minutes * 60000) + (seconds * 1000) + millis
        }
        else -> 0
    }
}

/**
 * Fetch and parse subtitles from URL
 */
suspend fun fetchSubtitles(url: String): List<SubtitleCue> {
    return withContext(Dispatchers.IO) {
        try {
            // Download subtitle content
            val response = java.net.URL(url).readText()
            
            // Detect format and parse
            when {
                response.contains("WEBVTT") -> parseVTT(response)
                response.contains("-->") -> parseSRT(response)
                response.contains("<tt") || response.contains("<transcript") -> parseTTML(response) // Add TTML support
                else -> emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

/**
 * Basic TTML/XML parser for YouTube subtitles
 */
fun parseTTML(content: String): List<SubtitleCue> {
    val cues = mutableListOf<SubtitleCue>()
    try {
        // Simple regex-based parsing for <p t="start_ms" d="duration_ms">Text</p>
        // or <text start="3.4" dur="2.1">Hello</text>
        
        // Regex for: <text start="3.4" dur="2.1">Hello</text> (Common in YouTube XML)
        val regex = "<text start=\"([0-9.]+)\" dur=\"([0-9.]+)\"[^>]*>(.*?)</text>".toRegex()
        
        regex.findAll(content).forEach { match ->
            val (startSec, durSec, text) = match.destructured
            val startTime = (startSec.toDouble() * 1000).toLong()
            val duration = (durSec.toDouble() * 1000).toLong()
            val endTime = startTime + duration
            
            // Decode HTML entities
            val decodedText = text
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("<br />", "\n")
                .replace("<br>", "\n")
            
            if (decodedText.isNotBlank()) {
                cues.add(SubtitleCue(startTime, endTime, decodedText))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return cues
}
