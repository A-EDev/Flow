package io.github.aedev.flow.data.localmedia

/**
 * The title with its accented letters back, when [title] is the damaged form some media scanners
 * store: each non-ASCII character replaced by one or more `?` ("Snälla" read as "Sn??lla"). The
 * real text comes from [fileName], and only when the damaged title matches exactly one run of the
 * file name between word edges; anything else keeps [title] as it is.
 *
 * Scanners disagree on how many `?` a character becomes (one per byte, per UTF-16 unit or per
 * character), so a run of them stands for any run of non-ASCII characters. Downloaders such as
 * yt-dlp swap characters file names cannot hold for full-width look-alikes (`:` for `：`), so those
 * match either form and come back as the original.
 */
internal fun repairedTitle(
    title: String,
    fileName: String,
): String {
    if ('?' !in title) return title
    val stem = fileName.substringBeforeLast('.')
    if (stem.all { it.code < ASCII_LIMIT }) return title
    val match = damagedTitlePattern(title).findAll(stem).singleOrNull() ?: return title
    return match.value.map { FilenameSubstitutes.entries.firstOrNull { (_, substitute) -> substitute == it }?.key ?: it }.joinToString("")
}

private fun damagedTitlePattern(title: String): Regex {
    val pattern = StringBuilder(WORD_EDGE_BEFORE)
    var index = 0
    while (index < title.length) {
        val char = title[index]
        if (char == '?') {
            while (index < title.length && title[index] == '?') index++
            pattern.append(NON_ASCII_RUN)
            continue
        }
        val substitute = FilenameSubstitutes[char]
        if (substitute == null) {
            pattern.append(Regex.escape(char.toString()))
        } else {
            pattern
                .append("(?:")
                .append(Regex.escape(char.toString()))
                .append('|')
                .append(Regex.escape(substitute.toString()))
                .append(')')
        }
        index++
    }
    return Regex(pattern.append(WORD_EDGE_AFTER).toString())
}

// The full-width characters yt-dlp writes in file names for the ones they cannot hold.
private val FilenameSubstitutes =
    mapOf(
        '"' to '＂',
        '*' to '＊',
        ':' to '：',
        '<' to '＜',
        '>' to '＞',
        '?' to '？',
        '|' to '｜',
        '/' to '⧸',
        '\\' to '⧹',
    )

private const val NON_ASCII_RUN = "[^\\x00-\\x7F]+"
private const val WORD_EDGE_BEFORE = "(?<![\\p{L}\\p{N}])"
private const val WORD_EDGE_AFTER = "(?![\\p{L}\\p{N}])"
private const val ASCII_LIMIT = 0x80
