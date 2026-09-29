package io.github.aedev.flow.utils

import kotlin.math.roundToLong

/**
 * Parses `H:MM:SS`, `M:SS` or a bare seconds count into milliseconds, and returns null when [text]
 * is not a timestamp at all.
 *
 * Every field has to be a number: a malformed field rejects the whole timestamp rather than
 * counting as zero, so a half-parsed string can never seek somewhere the user did not ask for.
 * Fractional seconds are kept, which is what the SponsorBlock submit dialog needs.
 */
fun parseTimestampMs(text: String): Long? {
    val fields = text.trim().split(':').map { it.trim().toDoubleOrNull() ?: return null }
    val seconds =
        when (fields.size) {
            1 -> fields[0]
            2 -> fields[0] * 60 + fields[1]
            3 -> fields[0] * 3600 + fields[1] * 60 + fields[2]
            else -> return null
        }
    return (seconds * 1000).roundToLong()
}
