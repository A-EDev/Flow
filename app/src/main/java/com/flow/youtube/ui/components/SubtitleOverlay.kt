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
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SubtitleCue(
    val startTime: Long,
    val endTime: Long,
    val text: String
)

data class SubtitleStyle(
    val fontSize: Float = 18f,
    val textColor: Color = Color.White,
    val backgroundColor: Color = Color.Black.copy(alpha = 0.6f),
    val isBold: Boolean = true
)

@Composable
fun SubtitleOverlay(
    currentPosition: Long,
    subtitles: List<SubtitleCue>,
    enabled: Boolean,
    style: SubtitleStyle = SubtitleStyle(),
    modifier: Modifier = Modifier
) {
    if (!enabled || subtitles.isEmpty()) return
    
    // Find ALL active subtitles for the current position (fixes the "partial sentence" issue)
    val activeCues = remember(currentPosition, subtitles) {
        if (subtitles.isEmpty()) return@remember emptyList<SubtitleCue>()
        
        // Find all cues that contain the current position
        // Since cues are usually ordered by startTime, we can optimize slightly, 
        // but simple filter is safest for overlapping YouTube formats.
        subtitles.filter { currentPosition in it.startTime..it.endTime }
    }
    
    // Merge texts of active cues, removing duplicates/overlaps
    val displayState = remember(activeCues) {
        if (activeCues.isEmpty()) null
        else {
            // Deduplicate and join lines
            activeCues.map { it.text.trim() }
                .distinct()
                .joinToString("\n")
        }
    }
    
    AnimatedVisibility(
        visible = displayState != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 48.dp, start = 16.dp, end = 16.dp)
    ) {
        if (displayState != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = style.backgroundColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = displayState,
                        color = style.textColor,
                        fontSize = style.fontSize.sp,
                        fontWeight = if (style.isBold) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
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
 * Fetch and parse subtitles from URL with improved error handling and logging
 */
suspend fun fetchSubtitles(url: String): List<SubtitleCue> {
    return withContext(Dispatchers.IO) {
        try {
            Log.d("SubtitleOverlay", "Fetching subtitles from: $url")
            
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                setRequestProperty("Accept", "*/*")
            }
            
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.e("SubtitleOverlay", "Failed to fetch subtitles: HTTP $responseCode")
                return@withContext emptyList()
            }
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d("SubtitleOverlay", "Subtitle response length: ${response.length}")
            
            if (response.length < 100) {
                Log.d("SubtitleOverlay", "Raw response snippet: $response")
            }
            
            // Detect format and parse
            val cues = when {
                response.contains("WEBVTT", ignoreCase = true) -> {
                    Log.d("SubtitleOverlay", "Detected VTT format")
                    parseVTT(response)
                }
                response.contains("-->") -> {
                    Log.d("SubtitleOverlay", "Detected SRT format")
                    parseSRT(response)
                }
                response.contains("<tt", ignoreCase = true) || response.contains("<transcript", ignoreCase = true) -> {
                    Log.d("SubtitleOverlay", "Detected TTML/XML format")
                    parseTTML(response)
                }
                else -> {
                    Log.w("SubtitleOverlay", "Unknown subtitle format")
                    emptyList()
                }
            }
            
            Log.d("SubtitleOverlay", "Successfully parsed ${cues.size} subtitle cues")
            cues
        } catch (e: Exception) {
            Log.e("SubtitleOverlay", "Error fetching/parsing subtitles", e)
            emptyList()
        }
    }
}

/**
 * Basic TTML/XML parser for YouTube subtitles
 * Handles both <text> (YouTube timed text) and <p> (TTML) tags
 */
fun parseTTML(content: String): List<SubtitleCue> {
    val cues = mutableListOf<SubtitleCue>()
    try {
        // 1. Try YouTube Timed Text format (<text start="3.4" dur="2.1">Hello</text>)
        val textRegex = "<text start=\"([0-9.]+)\" dur=\"([0-9.]+)\"[^>]*>(.*?)</text>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val textMatches = textRegex.findAll(content).toList()
        
        if (textMatches.isNotEmpty()) {
            textMatches.forEach { match ->
                val (startSec, durSec, text) = match.destructured
                val startTime = (startSec.toDouble() * 1000).toLong()
                val duration = (durSec.toDouble() * 1000).toLong()
                val endTime = startTime + duration
                
                val decodedText = decodeHtml(text)
                if (decodedText.isNotBlank()) {
                    cues.add(SubtitleCue(startTime, endTime, decodedText))
                }
            }
            return cues
        }

        // 2. Try TTML format (<p begin="00:00:03.400" end="00:00:05.500">Hello</p>)
        val pRegex = "<p[^>]+begin=\"([^\"]+)\"[^>]+end=\"([^\"]+)\"[^>]*>(.*?)</p>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val pMatches = pRegex.findAll(content).toList()
        
        if (pMatches.isNotEmpty()) {
            pMatches.forEach { match ->
                val (begin, end, text) = match.destructured
                val startTime = parseTime(begin)
                val endTime = parseTime(end)
                
                val decodedText = decodeHtml(text)
                if (decodedText.isNotBlank()) {
                    cues.add(SubtitleCue(startTime, endTime, decodedText))
                }
            }
            return cues
        }
        
    } catch (e: Exception) {
        Log.e("SubtitleOverlay", "Error parsing TTML/XML", e)
    }
    return cues
}

private fun decodeHtml(text: String): String {
    return text
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("<br />", "\n")
        .replace("<br>", "\n")
        .replace("<[^>]*>".toRegex(), "") // Remove any remaining XML tags
        .trim()
}
