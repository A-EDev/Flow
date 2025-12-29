package com.flow.youtube.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.core.text.HtmlCompat

/**
 * Shared utility to format text with:
 * - HTML entity decoding (&apos;, &quot;, etc.)
 * - Line break handling (<br> to \n)
 * - Clickable URLs
 * - Clickable Timestamps (0:00)
 * - Clickable Hashtags (#hashtag)
 */
@Composable
fun formatRichText(
    text: String,
    primaryColor: Color,
    textColor: Color
): AnnotatedString {
    // 1. Pre-process HTML tags and entities
    // Use LEGACY mode to preserve more structure, and replace <br> beforehand
    val processedHtml = text.replace("(?i)<br\\s*/?>".toRegex(), "\n")
    val spanned = HtmlCompat.fromHtml(processedHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
    val plainText = spanned.toString().trim()

    return buildAnnotatedString {
        var currentIndex = 0
        
        // Regex patterns
        val urlPattern = Regex("""(https?://[^\s]+)""")
        val timestampPattern = Regex("""(\d{1,2}:)?\d{1,2}:\d{2}""")
        val hashtagPattern = Regex("""#\w+""")
        
        // Find all matches and sort by position
        data class Match(val start: Int, val end: Int, val type: String, val text: String)
        val matches = mutableListOf<Match>()
        
        urlPattern.findAll(plainText).forEach { 
            matches.add(Match(it.range.first, it.range.last + 1, "url", it.value))
        }
        timestampPattern.findAll(plainText).forEach { 
            matches.add(Match(it.range.first, it.range.last + 1, "timestamp", it.value))
        }
        hashtagPattern.findAll(plainText).forEach { 
            matches.add(Match(it.range.first, it.range.last + 1, "hashtag", it.value))
        }
        
        matches.sortBy { it.start }
        
        // Build the annotated string
        for (match in matches) {
            // Add text before match
            if (match.start > currentIndex) {
                append(plainText.substring(currentIndex, match.start))
            }
            
            // Skip if overlapping (e.g. hashtag inside URL)
            if (match.start < currentIndex) continue
            
            // Add styled match
            when (match.type) {
                "url" -> {
                    pushStringAnnotation(tag = "URL", annotation = match.text)
                    withStyle(SpanStyle(
                        color = primaryColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium
                    )) {
                        // Truncate long URLs for display
                        val displayUrl = if (match.text.length > 40) {
                            match.text.take(37) + "..."
                        } else {
                            match.text
                        }
                        append(displayUrl)
                    }
                    pop()
                }
                "timestamp" -> {
                    pushStringAnnotation(tag = "TIMESTAMP", annotation = match.text)
                    withStyle(SpanStyle(
                        color = primaryColor,
                        fontWeight = FontWeight.Bold
                    )) {
                        append(match.text)
                    }
                    pop()
                }
                "hashtag" -> {
                    pushStringAnnotation(tag = "HASHTAG", annotation = match.text)
                    withStyle(SpanStyle(
                        color = primaryColor,
                        fontWeight = FontWeight.Bold
                    )) {
                        append(match.text)
                    }
                    pop()
                }
            }
            
            currentIndex = match.end
        }
        
        // Add remaining text
        if (currentIndex < plainText.length) {
            append(plainText.substring(currentIndex))
        }
    }
}
