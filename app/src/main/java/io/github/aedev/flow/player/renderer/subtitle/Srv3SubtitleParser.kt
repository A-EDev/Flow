package io.github.aedev.flow.player.renderer.subtitle

import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.media3.common.Format
import androidx.media3.common.text.Cue
import androidx.media3.common.util.Consumer
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser

/**
 * Decodes YouTube's srv3 caption XML (`<timedtext format="3">`) into styled, positioned [Cue]s,
 * preserving the bold/italic/underline/color spans and window placement that this app's previous
 * `fmt=vtt`-only pipeline discarded.
 */
class Srv3SubtitleParser : SubtitleParser {
    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>,
    ) {
        val xml = String(data, offset, length, Charsets.UTF_8)
        val paragraphs = runCatching { parseSrv3Document(xml) }.getOrDefault(emptyList())
        paragraphs.forEach { output.accept(it.toCuesWithTiming()) }
    }

    override fun getCueReplacementBehavior(): Int = Format.CUE_REPLACEMENT_BEHAVIOR_MERGE

    companion object {
        /** MIME type this parser is registered against; never advertised by YouTube itself. */
        const val MIME_TYPE = "application/x-youtube-srv3"
    }
}

private fun Srv3Paragraph.toCuesWithTiming(): CuesWithTiming {
    val builder = SpannableStringBuilder()
    for (run in runs) {
        val start = builder.length
        builder.append(run.text)
        run.pen.applySpansTo(builder, start, builder.length)
    }

    val text = builder.trimmedKeepingSpans()
    val startTimeUs = startMs * 1000L
    val durationUs = durationMs.coerceAtLeast(0L) * 1000L
    if (text.isBlank()) return CuesWithTiming(emptyList(), startTimeUs, durationUs)

    val cue = Cue.Builder().setText(text).applyWindow(window).build()
    return CuesWithTiming(listOf(cue), startTimeUs, durationUs)
}

private fun Srv3Pen.applySpansTo(
    builder: SpannableStringBuilder,
    start: Int,
    end: Int,
) {
    if (start >= end) return
    val style =
        when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> null
        }
    style?.let { builder.setSpan(StyleSpan(it), start, end, Spannable.SPAN_INCLUSIVE_EXCLUSIVE) }
    if (underline) builder.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
    foregroundColor?.toColorIntOrNull(foregroundOpacity)?.let {
        builder.setSpan(ForegroundColorSpan(it), start, end, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
    }
    backgroundColor?.toColorIntOrNull(backgroundOpacity)?.let {
        builder.setSpan(BackgroundColorSpan(it), start, end, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
    }
}

/** YouTube's `fo`/`bo` pen opacity is on a 0-254 scale, not the usual 0-255 alpha channel. */
private fun String.toColorIntOrNull(opacity: Int?): Int? =
    try {
        val rgb = Color.parseColor(this) and 0x00FFFFFF
        val alpha = (opacity ?: MAX_PEN_OPACITY).coerceIn(0, MAX_PEN_OPACITY) * 255 / MAX_PEN_OPACITY
        (alpha shl 24) or rgb
    } catch (e: IllegalArgumentException) {
        null
    }

private const val MAX_PEN_OPACITY = 254

private fun Cue.Builder.applyWindow(window: Srv3Window): Cue.Builder {
    val anchor = window.anchorPoint.coerceIn(0, 8)
    val row = anchor / 3 // 0 = top, 1 = middle, 2 = bottom
    val column = anchor % 3 // 0 = left, 1 = center, 2 = right

    val lineAnchor =
        when (row) {
            0 -> Cue.ANCHOR_TYPE_START
            1 -> Cue.ANCHOR_TYPE_MIDDLE
            else -> Cue.ANCHOR_TYPE_END
        }
    val positionAnchor =
        when (column) {
            0 -> Cue.ANCHOR_TYPE_START
            1 -> Cue.ANCHOR_TYPE_MIDDLE
            else -> Cue.ANCHOR_TYPE_END
        }
    val textAlignment =
        when (window.justify) {
            Srv3Window.JUSTIFY_LEFT -> Layout.Alignment.ALIGN_NORMAL
            Srv3Window.JUSTIFY_RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_CENTER
        }

    return setLine(window.verticalPercent.coerceIn(0f, 100f) / 100f, Cue.LINE_TYPE_FRACTION)
        .setLineAnchor(lineAnchor)
        .setPosition(window.horizontalPercent.coerceIn(0f, 100f) / 100f)
        .setPositionAnchor(positionAnchor)
        .setTextAlignment(textAlignment)
}

/** Trims whitespace from both ends while preserving any spans set on the interior range. */
private fun CharSequence.trimmedKeepingSpans(): CharSequence {
    var start = 0
    var end = length
    while (start < end && this[start].isWhitespace()) start++
    while (end > start && this[end - 1].isWhitespace()) end--
    return subSequence(start, end)
}
