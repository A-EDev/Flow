package io.github.aedev.flow.ui.screens.settings.playback

private const val MIN_SPEED = 0.1f
private const val MAX_SPEED = 10f

/** Custom speed presets are stored as a comma-separated list; this reads it, sorted, valid ones only. */
internal fun parseSpeedPresets(raw: String): List<Float> =
    raw
        .split(",")
        .mapNotNull { it.trim().toFloatOrNull() }
        .filter { it in MIN_SPEED..MAX_SPEED }
        .distinct()
        .sorted()

internal fun serializeSpeedPresets(presets: List<Float>): String = presets.distinct().sorted().joinToString(",")

/** A typed preset, accepting either decimal separator, or null when it is not a usable speed. */
internal fun parseSpeedInput(input: String): Float? =
    input
        .trim()
        .replace(',', '.')
        .toFloatOrNull()
        ?.takeIf { it in MIN_SPEED..MAX_SPEED }

/** Seconds of delay before autoplay switches videos; the preference allows up to 30. */
internal val AutoplayCountdownOptions = listOf(0, 3, 5, 10, 15, 20, 30)

internal val DoubleTapSeekOptions = listOf(5, 10, 15, 20, 30)

/** Long-press speeds; 0 turns the long-press gesture off. */
internal val LongPressSpeedOptions = listOf(0f, 0.3f, 0.5f, 0.75f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

internal const val SHORTS_AUTO_SCROLL_MIN = 5
internal const val SHORTS_AUTO_SCROLL_MAX = 20
