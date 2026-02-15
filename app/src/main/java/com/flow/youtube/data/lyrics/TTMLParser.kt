// ============================================================================
// THIS IMPLEMENTATION WAS INSPIRED BY METROLIST
// ============================================================================

package com.flow.youtube.data.lyrics

import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parser for TTML (Timed Text Markup Language) lyrics format.
 * Extracts text, timestamps, and word-level timings from TTML documents.
 * Adapted from Metrolist's TTMLParser.
 */
object TTMLParser {

    data class TTMLLine(
        val text: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val words: List<TTMLWord>? = null,
        val agent: String? = null,
        val isBackground: Boolean = false
    )

    data class TTMLWord(
        val text: String,
        val startTimeMs: Long,
        val endTimeMs: Long
    )

    /**
     * Parse TTML XML into structured TTMLLine objects with word-level timing.
     */
    fun parseTTMLToLines(ttml: String): List<TTMLLine> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(ByteArrayInputStream(ttml.toByteArray(Charsets.UTF_8)))

        val result = mutableListOf<TTMLLine>()

        val paragraphs = doc.getElementsByTagNameNS("*", "p")
        if (paragraphs.length == 0) return result

        val agents = mutableMapOf<String, String>()
        val divElements = doc.getElementsByTagNameNS("*", "div")
        for (i in 0 until divElements.length) {
            val div = divElements.item(i) as? Element ?: continue
            val agentAttr = div.getAttribute("ttm:agent") ?: div.getAttribute("agent")
            if (agentAttr.isNotEmpty()) {
                agents[agentAttr] = agentAttr
            }
        }

        for (i in 0 until paragraphs.length) {
            val p = paragraphs.item(i) as? Element ?: continue

            val beginStr = p.getAttribute("begin") ?: continue
            val endStr = p.getAttribute("end") ?: p.getAttribute("dur") ?: continue

            val startMs = parseTimeToMs(beginStr) ?: continue
            val endMs = if (p.hasAttribute("end")) {
                parseTimeToMs(endStr) ?: continue
            } else {
                startMs + (parseTimeToMs(endStr) ?: continue)
            }

            val agent = p.getAttribute("ttm:agent")?.takeIf { it.isNotEmpty() }
                ?: p.getAttribute("agent")?.takeIf { it.isNotEmpty() }

            val role = p.getAttribute("ttm:role")?.takeIf { it.isNotEmpty() }
                ?: p.getAttribute("role")?.takeIf { it.isNotEmpty() }
            val isBackground = role?.contains("x-bg", ignoreCase = true) == true

            val words = mutableListOf<TTMLWord>()
            val spans = p.getElementsByTagNameNS("*", "span")

            if (spans.length > 0) {
                for (j in 0 until spans.length) {
                    val span = spans.item(j) as? Element ?: continue
                    val spanText = span.textContent?.trim() ?: continue
                    if (spanText.isEmpty()) continue

                    val spanBegin = span.getAttribute("begin")?.let { parseTimeToMs(it) } ?: startMs
                    val spanEnd = span.getAttribute("end")?.let { parseTimeToMs(it) } ?: endMs

                    words.add(TTMLWord(spanText, spanBegin, spanEnd))
                }
            }

            val fullText = if (words.isNotEmpty()) {
                words.joinToString(" ") { it.text }
            } else {
                p.textContent?.trim() ?: continue
            }

            if (fullText.isNotEmpty()) {
                result.add(
                    TTMLLine(
                        text = fullText,
                        startTimeMs = startMs,
                        endTimeMs = endMs,
                        words = words.takeIf { it.isNotEmpty() },
                        agent = agent,
                        isBackground = isBackground
                    )
                )
            }
        }

        return result.sortedBy { it.startTimeMs }
    }

    /**
     * Parse TTML time string to milliseconds.
     * Supports formats: "HH:MM:SS.mmm", "MM:SS.mmm", "SS.mmm", "123456ms", "123.456s"
     */
    private fun parseTimeToMs(time: String): Long? {
        if (time.isEmpty()) return null

        if (time.endsWith("ms")) {
            return time.removeSuffix("ms").toLongOrNull()
        }

        if (time.endsWith("s") && !time.contains(":")) {
            return time.removeSuffix("s").toDoubleOrNull()?.let { (it * 1000).toLong() }
        }

        val parts = time.split(":")
        return try {
            when (parts.size) {
                3 -> {
                    val hours = parts[0].toLong()
                    val minutes = parts[1].toLong()
                    val secParts = parts[2].split(".")
                    val seconds = secParts[0].toLong()
                    val millis = if (secParts.size > 1) {
                        val fracStr = secParts[1].take(3).padEnd(3, '0')
                        fracStr.toLong()
                    } else 0L
                    hours * 3600000 + minutes * 60000 + seconds * 1000 + millis
                }
                2 -> {
                    val minutes = parts[0].toLong()
                    val secParts = parts[1].split(".")
                    val seconds = secParts[0].toLong()
                    val millis = if (secParts.size > 1) {
                        val fracStr = secParts[1].take(3).padEnd(3, '0')
                        fracStr.toLong()
                    } else 0L
                    minutes * 60000 + seconds * 1000 + millis
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}

