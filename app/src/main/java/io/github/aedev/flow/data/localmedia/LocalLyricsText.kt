package io.github.aedev.flow.data.localmedia

private val OffsetTag = Regex("""^\s*\[offset:\s*([+-]?\d+)\s*]\s*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
private val HeaderTag = Regex("""^\s*\[[a-zA-Z#]+:[^\]]*]\s*$""")

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
