package io.github.aedev.flow.data.lyrics

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

object TTMLParser {
    private const val TTML_PARAMETER_NS = "http://www.w3.org/ns/ttml#parameter"
    private const val TTML_METADATA_NS = "http://www.w3.org/ns/ttml#metadata"

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

    private data class DomSpan(
        val text: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val hasTrailingSpace: Boolean
    )

    /**
     * Robust TTML parser based on Metrolist's implementation.
     *
     * Keeps word timing, background lines, agent tags, global offsets, nested spans,
     * and tracks whose <p> timing is only present on child spans.
     */
    fun parseTTMLToLyricsEntries(xmlData: String): List<LyricsEntry> {
        if (xmlData.isBlank()) return emptyList()

        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                trySetFeature("http://xml.org/sax/features/external-general-entities", false)
                trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
                trySetFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                try {
                    isExpandEntityReferences = false
                } catch (_: Exception) {
                }
            }
            val doc = factory.newDocumentBuilder().parse(xmlData.byteInputStream())
            val root = doc.documentElement ?: return emptyList()
            val offsetMs = readGlobalOffsetMs(root)
            val result = mutableListOf<LyricsEntry>()
            findChild(root, "body")?.let { walkDom(it, result, offsetMs, null) }
            result.sorted()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun DocumentBuilderFactory.trySetFeature(name: String, enabled: Boolean) {
        try {
            setFeature(name, enabled)
        } catch (_: Exception) {
        }
    }

    private fun readGlobalOffsetMs(root: Element): Long {
        val audio = findChild(findChild(root, "head"), "metadata")
            ?.let { findChild(it, "audio") }
        return audio?.getAttribute("lyricOffset")?.toDoubleOrNull()?.let { (it * 1000).toLong() }
            ?: 0L
    }

    private fun walkDom(element: Element, result: MutableList<LyricsEntry>, offsetMs: Long, parentAgent: String?) {
        val name = element.localName ?: element.nodeName.substringAfterLast(':')
        var agent = parentAgent
        when (name) {
            "div" -> element.ttmlAttr("agent").takeIf { it.isNotBlank() }?.let { agent = it }
            "p" -> {
                parseParagraph(element, result, offsetMs, agent)
                return
            }
        }

        var child = element.firstChild
        while (child != null) {
            if (child is Element) walkDom(child, result, offsetMs, agent)
            child = child.nextSibling
        }
    }

    private fun parseParagraph(p: Element, result: MutableList<LyricsEntry>, offsetMs: Long, divAgent: String?) {
        val beginAttr = p.timingAttr("begin").ifEmpty { findFirstSpanBegin(p).orEmpty() }
        if (beginAttr.isBlank()) return

        val startMs = parseTime(beginAttr) + offsetMs
        val agent = p.ttmlAttr("agent").ifEmpty { divAgent }
        val isBackground = p.ttmlAttr("role") == "x-bg"
        val spans = mutableListOf<DomSpan>()
        val backgroundLines = mutableListOf<LyricsEntry>()
        val translations = mutableListOf<String>()

        var child = p.firstChild
        while (child != null) {
            if (child is Element) {
                val name = child.localName ?: child.nodeName.substringAfterLast(':')
                if (name == "span") {
                    when (child.ttmlAttr("role")) {
                        "x-bg" -> {
                            if (isBackground) {
                                parseWordSpan(child, offsetMs, spans, child)
                            } else {
                                parseBackgroundSpan(child, startMs, offsetMs)?.let { backgroundLines += it }
                            }
                        }
                        "x-translation", "x-roman" -> child.textContent.orEmpty().trim()
                            .takeIf { it.isNotBlank() }
                            ?.let { translations += it }
                        else -> parseWordSpan(child, offsetMs, spans, child)
                    }
                }
            }
            child = child.nextSibling
        }

        val words = mergeSpansIntoWords(spans)
        val text = if (words.isNotEmpty()) {
            buildLineText(words)
        } else {
            directText(p).trim()
        }

        if (text.isNotBlank()) {
            result += LyricsEntry(
                time = startMs,
                text = text,
                words = words.takeIf { it.isNotEmpty() },
                agent = agent,
                isBackground = isBackground,
                translation = translations.joinToString("\n").ifBlank { null }
            )
        }
        result += backgroundLines
    }

    private fun parseBackgroundSpan(span: Element, parentStartMs: Long, offsetMs: Long): LyricsEntry? {
        val startMs = span.timingAttr("begin")
            .takeIf { it.isNotBlank() }
            ?.let { parseTime(it) + offsetMs }
            ?: parentStartMs
        val spans = mutableListOf<DomSpan>()
        val translations = mutableListOf<String>()

        var hasChildSpans = false
        var child = span.firstChild
        while (child != null) {
            if (child is Element) {
                val name = child.localName ?: child.nodeName.substringAfterLast(':')
                if (name == "span") {
                    hasChildSpans = true
                    val role = child.ttmlAttr("role")
                    if (role != "x-translation" && role != "x-roman") {
                        parseWordSpan(child, offsetMs, spans, child)
                    } else {
                        child.textContent.orEmpty().trim()
                            .takeIf { it.isNotBlank() }
                            ?.let { translations += it }
                    }
                }
            }
            child = child.nextSibling
        }

        val words = mergeSpansIntoWords(spans)
        val text = when {
            words.isNotEmpty() -> buildLineText(words)
            !hasChildSpans -> span.textContent.orEmpty().trim()
            else -> directText(span).trim()
        }
        if (text.isBlank()) return null

        return LyricsEntry(
            time = startMs,
            text = text,
            words = words.takeIf { it.isNotEmpty() },
            agent = "bg",
            isBackground = true,
            translation = translations.joinToString("\n").ifBlank { null }
        )
    }

    private fun parseWordSpan(span: Element, offsetMs: Long, spans: MutableList<DomSpan>, node: Node) {
        val begin = span.timingAttr("begin")
        val end = span.timingAttr("end")
        val text = span.textContent.orEmpty()
        if (begin.isBlank() || end.isBlank()) return

        val next = node.nextSibling
        val hasTrailingSpace = (text.lastOrNull()?.isWhitespace() == true) ||
            (next?.nodeType == Node.TEXT_NODE && next.textContent?.firstOrNull()?.isWhitespace() == true)
        spans += DomSpan(
            text = text,
            startTimeMs = parseTime(begin) + offsetMs,
            endTimeMs = parseTime(end) + offsetMs,
            hasTrailingSpace = hasTrailingSpace
        )
    }

    private fun mergeSpansIntoWords(spans: List<DomSpan>): List<WordTimestamp> {
        if (spans.isEmpty()) return emptyList()

        val words = mutableListOf<WordTimestamp>()
        var text = StringBuilder(spans.first().text)
        var start = spans.first().startTimeMs
        var end = spans.first().endTimeMs

        for (i in 1 until spans.size) {
            val previous = spans[i - 1]
            val current = spans[i]
            if (previous.hasTrailingSpace && !previous.text.endsWith('-')) {
                text.toString().trim().takeIf { it.isNotEmpty() }?.let {
                    words += WordTimestamp(it, start, end)
                }
                text = StringBuilder(current.text)
                start = current.startTimeMs
            } else {
                text.append(current.text)
            }
            end = current.endTimeMs
        }

        text.toString().trim().takeIf { it.isNotEmpty() }?.let {
            words += WordTimestamp(it, start, end)
        }
        return words
    }

    private fun buildLineText(words: List<WordTimestamp>): String = buildString {
        words.forEachIndexed { index, word ->
            append(word.text)
            if (!word.text.endsWith('-') && index < words.lastIndex) append(' ')
        }
    }.trim()

    private fun directText(element: Element): String {
        val sb = StringBuilder()
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.TEXT_NODE) {
                sb.append(child.textContent)
            } else if (child is Element) {
                val name = child.localName ?: child.nodeName.substringAfterLast(':')
                val role = child.ttmlAttr("role")
                if (name == "span" && role != "x-bg" && role != "x-translation" && role != "x-roman") {
                    sb.append(child.textContent)
                }
            }
            child = child.nextSibling
        }
        return sb.toString()
    }

    private fun findFirstSpanBegin(p: Element): String? {
        var child = p.firstChild
        var best: String? = null
        var bestMs = Long.MAX_VALUE
        while (child != null) {
            if (child is Element) {
                val name = child.localName ?: child.nodeName.substringAfterLast(':')
                if (name == "span") {
                    val begin = child.timingAttr("begin")
                    if (begin.isNotBlank()) {
                        val ms = parseTime(begin)
                        if (ms < bestMs) {
                            bestMs = ms
                            best = begin
                        }
                    }
                }
            }
            child = child.nextSibling
        }
        return best
    }

    private fun findChild(parent: Element?, localName: String): Element? {
        var child = parent?.firstChild
        while (child != null) {
            if (child is Element) {
                val name = child.localName ?: child.nodeName.substringAfterLast(':')
                if (name == localName) return child
            }
            child = child.nextSibling
        }
        return null
    }

    private fun Element.ttmlAttr(localName: String): String {
        getAttribute("ttm:$localName").takeIf { it.isNotEmpty() }?.let { return it }
        getAttribute(localName).takeIf { it.isNotEmpty() }?.let { return it }
        return getAttributeNS(TTML_METADATA_NS, localName)
    }

    private fun Element.timingAttr(localName: String): String {
        getAttribute(localName).takeIf { it.isNotEmpty() }?.let { return it }
        return getAttributeNS(TTML_PARAMETER_NS, localName)
    }

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
