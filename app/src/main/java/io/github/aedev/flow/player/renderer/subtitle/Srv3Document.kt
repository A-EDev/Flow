package io.github.aedev.flow.player.renderer.subtitle

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/** A single character/foreground style declared by a YouTube srv3 `<pen>` element. */
internal data class Srv3Pen(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val foregroundColor: String? = null,
    val foregroundOpacity: Int? = null,
    val backgroundColor: String? = null,
    val backgroundOpacity: Int? = null,
)

/** A caption window declared by a YouTube srv3 `<wp>`/`<ws>` pair, referenced by id from a `<p>`. */
internal data class Srv3Window(
    val anchorPoint: Int = DEFAULT_ANCHOR_POINT,
    val horizontalPercent: Float = 50f,
    val verticalPercent: Float = 100f,
    val justify: Int = JUSTIFY_CENTER,
    /**
     * Whether the document actually declared a `<wp>` for the paragraph, as opposed to these
     * defaults. Cues without one must leave [androidx.media3.common.text.Cue.line] unset:
     * SubtitleView only applies its bottom-padding fraction - and so the user's subtitle position
     * setting - to cues that carry no explicit line.
     */
    val positioned: Boolean = false,
) {
    companion object {
        /** Numpad-style 0-8 grid, row-major: row = anchorPoint / 3, column = anchorPoint % 3. */
        const val DEFAULT_ANCHOR_POINT = 7 // bottom-center
        const val JUSTIFY_LEFT = 0
        const val JUSTIFY_RIGHT = 1
        const val JUSTIFY_CENTER = 2
    }
}

/** One styled run of text within a paragraph, sharing a single [pen]. */
internal data class Srv3Run(val text: String, val pen: Srv3Pen)

/** One caption event: a `<p>` element, with its resolved window and styled runs, in document order. */
internal data class Srv3Paragraph(
    val startMs: Long,
    val durationMs: Long,
    val window: Srv3Window,
    val runs: List<Srv3Run>,
)

private val DEFAULT_PEN = Srv3Pen()
private val DEFAULT_WINDOW = Srv3Window()

/**
 * Parses a YouTube srv3 timedtext document (`<timedtext format="3">`) into paragraphs.
 *
 * Unlike the `vtt`/`srv1` conversions of the same track that YouTube also serves, srv3 carries
 * per-run bold/italic/underline/color and window position - the styling YouTube's own web and app
 * players render, which this app previously discarded by always requesting `fmt=vtt`.
 */
internal fun parseSrv3Document(xml: String): List<Srv3Paragraph> {
    val parser = XmlPullParserFactory.newInstance().newPullParser()
    parser.setInput(StringReader(xml))

    val pens = mutableMapOf<String, Srv3Pen>()
    val windowStyles = mutableMapOf<String, Int>()
    val windowPositions = mutableMapOf<String, Triple<Int, Float, Float>>()
    val paragraphs = mutableListOf<Srv3Paragraph>()

    var currentParagraphPenId: String? = null
    var currentWindowStyleId: String? = null
    var currentWindowPositionId: String? = null
    var currentStartMs = 0L
    var currentDurationMs = 0L
    var currentRuns: MutableList<Srv3Run>? = null
    var currentRunPenId: String? = null
    val currentRunText = StringBuilder()

    fun flushRun() {
        val runs = currentRuns ?: return
        val text = currentRunText.toString()
        currentRunText.setLength(0)
        if (text.isEmpty()) return
        val pen = currentRunPenId?.let { pens[it] } ?: currentParagraphPenId?.let { pens[it] } ?: DEFAULT_PEN
        runs.add(Srv3Run(text.normalizeSrv3Text(), pen))
    }

    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            XmlPullParser.START_TAG ->
                when (parser.name) {
                    "pen" -> {
                        val id = parser.getAttributeValue(null, "id") ?: ""
                        pens[id] = parser.toPen()
                    }
                    "ws" -> {
                        val id = parser.getAttributeValue(null, "id") ?: ""
                        windowStyles[id] =
                            parser.getAttributeValue(null, "ju")?.toIntOrNull() ?: Srv3Window.JUSTIFY_CENTER
                    }
                    "wp" -> {
                        val id = parser.getAttributeValue(null, "id") ?: ""
                        val anchor = parser.getAttributeValue(null, "ap")?.toIntOrNull() ?: Srv3Window.DEFAULT_ANCHOR_POINT
                        val horizontal = parser.getAttributeValue(null, "ah")?.toFloatOrNull() ?: 50f
                        val vertical = parser.getAttributeValue(null, "av")?.toFloatOrNull() ?: 100f
                        windowPositions[id] = Triple(anchor, horizontal, vertical)
                    }
                    "p" -> {
                        currentParagraphPenId = parser.getAttributeValue(null, "p")
                        currentWindowStyleId = parser.getAttributeValue(null, "ws")
                        currentWindowPositionId = parser.getAttributeValue(null, "wp")
                        currentStartMs = parser.getAttributeValue(null, "t")?.toLongOrNull() ?: 0L
                        currentDurationMs = parser.getAttributeValue(null, "d")?.toLongOrNull() ?: 0L
                        currentRuns = mutableListOf()
                        currentRunPenId = null
                    }
                    "s" -> {
                        flushRun()
                        currentRunPenId = parser.getAttributeValue(null, "p")
                    }
                }
            XmlPullParser.TEXT -> if (currentRuns != null) currentRunText.append(parser.text)
            XmlPullParser.END_TAG ->
                when (parser.name) {
                    "s" -> {
                        flushRun()
                        currentRunPenId = currentParagraphPenId
                    }
                    "p" -> {
                        flushRun()
                        val runs = currentRuns
                        if (!runs.isNullOrEmpty()) {
                            val position = currentWindowPositionId?.let { windowPositions[it] }
                            val justify = currentWindowStyleId?.let { windowStyles[it] }
                            paragraphs.add(
                                Srv3Paragraph(
                                    startMs = currentStartMs,
                                    durationMs = currentDurationMs,
                                    window =
                                        Srv3Window(
                                            anchorPoint = position?.first ?: DEFAULT_WINDOW.anchorPoint,
                                            horizontalPercent = position?.second ?: DEFAULT_WINDOW.horizontalPercent,
                                            verticalPercent = position?.third ?: DEFAULT_WINDOW.verticalPercent,
                                            justify = justify ?: DEFAULT_WINDOW.justify,
                                            positioned = position != null,
                                        ),
                                    runs = runs,
                                ),
                            )
                        }
                        currentRuns = null
                    }
                }
        }
        eventType = parser.next()
    }

    return paragraphs
}

private fun XmlPullParser.toPen(): Srv3Pen =
    Srv3Pen(
        bold = getAttributeValue(null, "b") == "1",
        italic = getAttributeValue(null, "i") == "1",
        underline = getAttributeValue(null, "u") == "1",
        foregroundColor = getAttributeValue(null, "fc"),
        foregroundOpacity = getAttributeValue(null, "fo")?.toIntOrNull(),
        backgroundColor = getAttributeValue(null, "bc"),
        backgroundOpacity = getAttributeValue(null, "bo")?.toIntOrNull(),
    )

/** YouTube encodes explicit line breaks as a literal two-character backslash-n escape, not a real newline. */
private fun String.normalizeSrv3Text(): String = replace(LITERAL_NEWLINE_ESCAPE, "\n").replace(NON_BREAKING_SPACE_CHAR, ' ')

private const val LITERAL_NEWLINE_ESCAPE = "\\n"
private const val NON_BREAKING_SPACE_CHAR = '\u00A0'
