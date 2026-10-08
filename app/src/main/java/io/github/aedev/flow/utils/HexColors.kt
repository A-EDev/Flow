package io.github.aedev.flow.utils

private const val HEX_RADIX = 16
private const val RGB_DIGITS = 6
private const val ARGB_DIGITS = 8
private const val OPAQUE_PREFIX = "FF"
private const val ARGB_MASK = 0xFFFFFFFFL

/** What a hex field keeps of what was typed: a leading #, then at most eight hex digits. */
fun sanitizeHexInput(raw: String): String {
    val body =
        raw
            .trim()
            .uppercase()
            .removePrefix("#")
            .filter { it.isDigit() || it in 'A'..'F' }
    return "#" + body.take(ARGB_DIGITS)
}

/** `#RRGGBB` (opaque) or `#AARRGGBB` as an ARGB long, or null for anything else. */
fun parseHexColor(input: String): Long? {
    val body = input.trim().removePrefix("#")
    val argb =
        when (body.length) {
            RGB_DIGITS -> OPAQUE_PREFIX + body
            ARGB_DIGITS -> body
            else -> return null
        }
    return argb.toLongOrNull(HEX_RADIX)
}

fun Long.toHexArgb(): String = "#%08X".format(this and ARGB_MASK)

/** `#RRGGBB`, the colour without its alpha. */
fun Long.toHexRgb(): String = "#%06X".format(this and RGB_MASK)

private const val RGB_MASK = 0xFFFFFFL
