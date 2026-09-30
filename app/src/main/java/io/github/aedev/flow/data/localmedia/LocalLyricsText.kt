package io.github.aedev.flow.data.localmedia

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

private val OffsetTag = Regex("""^\s*\[offset:\s*([+-]?\d+)\s*]\s*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
private val HeaderTag = Regex("""^\s*\[[a-zA-Z#]+:[^\]]*]\s*$""")
private val FallbackCharset: Charset = Charset.forName("windows-1252")

/**
 * The text of a lyrics file: its byte-order mark first, then strict UTF-8, then the legacy code
 * page [detect] names. Old `.lrc` files are often in one, which strict UTF-8 rejects.
 */
internal fun decodeLyricsText(
    bytes: ByteArray,
    detect: (ByteArray) -> Charset?,
): String {
    val bom =
        when {
            bytes.startsWith(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8 to 3
            bytes.startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE to 2
            bytes.startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE to 2
            else -> null
        }
    if (bom != null) return String(bytes, bom.second, bytes.size - bom.second, bom.first)
    return strictUtf8(bytes) ?: String(bytes, detect(bytes) ?: FallbackCharset)
}

/**
 * The code page a non-UTF-8 lyrics file most likely uses, from the device language. Android ships
 * no charset detector, so this is a guess, and a wrong one only garbles letters outside ASCII.
 */
internal fun legacyLyricsCharset(locale: Locale): Charset? =
    when (locale.language) {
        "zh" -> "GB18030"
        "ja" -> "Shift_JIS"
        "ko" -> "EUC-KR"
        "ru", "uk", "be", "bg", "sr", "mk" -> "windows-1251"
        "el" -> "windows-1253"
        "tr" -> "windows-1254"
        "he", "iw" -> "windows-1255"
        "ar", "fa" -> "windows-1256"
        "pl", "cs", "sk", "hu", "sl", "hr", "ro" -> "windows-1250"
        else -> null
    }?.let { runCatching { Charset.forName(it) }.getOrNull() }

/** An `[offset:+500]` tag in milliseconds; positive shows the lines sooner, as Flow's sync offset does. */
internal fun lrcOffsetMs(text: String): Long =
    OffsetTag
        .find(text)
        ?.groupValues
        ?.get(1)
        ?.toLongOrNull() ?: 0L

/** Unsynced lyrics without the `[ar:]`, `[ti:]` and similar header lines. */
internal fun plainLyricsText(text: String): String =
    text
        .lines()
        .filterNot { HeaderTag.matches(it) }
        .joinToString("\n")
        .trim()

/** The sidecar lyrics file of [audioFileName] among [siblings]: `Song.lrc` or `Song.mp3.lrc`, any case. */
internal fun matchingLyricsFile(
    audioFileName: String,
    siblings: List<String>,
): String? {
    val wanted = listOf("${audioFileName.substringBeforeLast('.')}.lrc", "$audioFileName.lrc")
    return wanted.firstNotNullOfOrNull { name -> siblings.firstOrNull { it.equals(name, ignoreCase = true) } }
}

private fun strictUtf8(bytes: ByteArray): String? =
    try {
        Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }
