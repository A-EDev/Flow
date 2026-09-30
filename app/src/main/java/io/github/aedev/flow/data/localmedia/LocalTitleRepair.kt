package io.github.aedev.flow.data.localmedia

/**
 * The title with its accented letters back, when [title] is the damaged form some media scanners
 * and downloaders store: every UTF-8 byte of a non-ASCII character replaced by `?` ("Snälla" read
 * as "Sn??lla"). The real text is taken from [fileName], and only when the damaged title appears
 * in it exactly once on character boundaries; anything else keeps [title] as it is.
 */
internal fun repairedTitle(
    title: String,
    fileName: String,
): String {
    if ('?' !in title) return title
    val stem = fileName.substringBeforeLast('.')
    if (stem.all { it.code < ASCII_LIMIT }) return title

    val damaged = StringBuilder()
    val unitStart = ArrayList<Int>()
    val unitEnd = ArrayList<Int>()
    val startsUnit = ArrayList<Boolean>()
    var index = 0
    while (index < stem.length) {
        val codePoint = stem.codePointAt(index)
        val next = index + Character.charCount(codePoint)
        val text = if (codePoint < ASCII_LIMIT) stem.substring(index, next) else "?".repeat(utf8Length(codePoint))
        text.forEachIndexed { offset, char ->
            damaged.append(char)
            unitStart += index
            unitEnd += next
            startsUnit += offset == 0
        }
        index = next
    }

    val first = damaged.indexOf(title)
    if (first < 0 || damaged.lastIndexOf(title) != first) return title
    val last = first + title.length - 1
    val endsUnit = last + 1 == damaged.length || startsUnit[last + 1]
    if (!startsUnit[first] || !endsUnit) return title
    return stem.substring(unitStart[first], unitEnd[last])
}

private fun utf8Length(codePoint: Int): Int =
    when {
        codePoint < 0x800 -> 2
        codePoint < 0x10000 -> 3
        else -> 4
    }

private const val ASCII_LIMIT = 0x80
