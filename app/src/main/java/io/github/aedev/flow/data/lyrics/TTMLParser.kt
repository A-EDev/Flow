package io.github.aedev.flow.data.lyrics

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

object TTMLParser {

    data class ParsedLine(
        val startTimeMs: Long,
        val endTimeMs: Long,
        val syllables: List<ParsedSyllable>,
        val isBackground: Boolean = false // e.g. "role=x-bg"
    )

    data class ParsedSyllable(
        val text: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val hasTrailingSpace: Boolean
    )

    fun toLRC(lines: List<ParsedLine>): String {
        return buildString {
            lines.forEach { line ->
                val finalWords = mutableListOf<ParsedSyllable>()
                var currentWordText = ""
                var currentWordStart = -1L
                var currentWordEnd = -1L

                line.syllables.forEach { syl ->
                    if (currentWordStart == -1L) {
                        currentWordStart = syl.startTimeMs
                    }
                    currentWordText += syl.text
                    currentWordEnd = syl.endTimeMs

                    if (syl.hasTrailingSpace) {
                        finalWords.add(
                            ParsedSyllable(
                                currentWordText,
                                currentWordStart,
                                currentWordEnd,
                                true
                            )
                        )
                        currentWordText = ""
                        currentWordStart = -1L
                    }
                }
                
                if (currentWordText.isNotEmpty()) {
                    finalWords.add(
                        ParsedSyllable(
                            currentWordText,
                            currentWordStart,
                            currentWordEnd,
                            false
                        )
                    )
                }

                val fullLineText = finalWords.joinToString(separator = " ") { it.text }.trim()
                if (fullLineText.isEmpty()) return@forEach

                val mm = line.startTimeMs / 60000
                val ss = (line.startTimeMs % 60000) / 1000
                val ms = line.startTimeMs % 1000
                val lineTimeStr = String.format("[%02d:%02d.%03d]", mm, ss, ms)
                
                appendLine("$lineTimeStr $fullLineText")

                val wordsStr = finalWords.joinToString("|") { w ->
                    val wStartSec = w.startTimeMs / 1000.0
                    val wEndSec = w.endTimeMs / 1000.0
                    "${w.text}:$wStartSec:$wEndSec"
                }
                appendLine("<$wordsStr>")
            }
        }
    }

    fun parseTTML(xmlData: String): List<ParsedLine> {
        val result = mutableListOf<ParsedLine>()
        if (xmlData.isBlank()) return result

        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlData))

            var eventType = parser.eventType
            var inBody = false
            var inDiv = false

            var currentLine: MutableList<ParsedSyllable>? = null
            var currentLineStart = -1L
            var currentLineEnd = -1L
            var currentLineIsBackground = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "body" -> inBody = true
                            "div" -> {
                                if (inBody) inDiv = true
                            }
                            "p" -> {
                                if (inBody && inDiv) {
                                    currentLine = mutableListOf()
                                    val bgnStr = parser.getAttributeValue(null, "begin")
                                    val endStr = parser.getAttributeValue(null, "end")
                                    val roleStr = parser.getAttributeValue(null, "role")

                                    currentLineStart = parseTime(bgnStr)
                                    currentLineEnd = parseTime(endStr)
                                    currentLineIsBackground = (roleStr == "x-bg")
                                }
                            }
                            "span" -> {
                                if (currentLine != null) {
                                    val bgnStr = parser.getAttributeValue(null, "begin")
                                    val endStr = parser.getAttributeValue(null, "end")
                                    val text = parser.nextText() // typically <span ...>text</span>

                                    var hasSpace = false
                                    var cleanText = text
                                    if (text.endsWith(" ")) {
                                        hasSpace = true
                                        cleanText = text.dropLast(1)
                                    }

                                    if (cleanText.isNotEmpty() || hasSpace) {
                                        val sStart = parseTime(bgnStr)
                                        val sEnd = parseTime(endStr)
                                        currentLine.add(
                                            ParsedSyllable(
                                                cleanText,
                                                sStart,
                                                sEnd,
                                                hasTrailingSpace = hasSpace
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                         when (parser.name) {
                            "body" -> inBody = false
                            "div" -> inDiv = false
                            "p" -> {
                                if (currentLine != null) {
                                    if (currentLine.isNotEmpty()) {
                                        result.add(
                                            ParsedLine(
                                                startTimeMs = currentLineStart,
                                                endTimeMs = currentLineEnd,
                                                syllables = currentLine,
                                                isBackground = currentLineIsBackground
                                            )
                                        )
                                    }
                                    currentLine = null
                                }
                            }
                         }
                    }
                }
                eventType = parser.next()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun parseTime(timeStr: String?): Long {
        if (timeStr.isNullOrBlank()) return 0L
        try {
            if (timeStr.endsWith("ms")) {
                return timeStr.replace("ms", "").toLongOrNull() ?: 0L
            }
            if (timeStr.endsWith("s")) {
                val secStr = timeStr.replace("s", "")
                val secDouble = secStr.toDoubleOrNull() ?: 0.0
                return (secDouble * 1000).toLong()
            }
            if (timeStr.contains(":")) {
                val parts = timeStr.trim().split(":")
                if (parts.size == 3) {
                    val h = parts[0].toLongOrNull() ?: 0L
                    val m = parts[1].toLongOrNull() ?: 0L
                    
                    val sParts = parts[2].split(".")
                    val s = sParts[0].toLongOrNull() ?: 0L
                    var ms = 0L
                    if (sParts.size > 1) {
                         var msStr = sParts[1]
                         if (msStr.length > 3) msStr = msStr.substring(0, 3)
                         while (msStr.length < 3) msStr += "0"
                         ms = msStr.toLongOrNull() ?: 0L
                    }
                    return (h * 3600000) + (m * 60000) + (s * 1000) + ms
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0L
    }
}
