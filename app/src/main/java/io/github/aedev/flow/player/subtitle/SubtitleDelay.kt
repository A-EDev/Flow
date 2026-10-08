package io.github.aedev.flow.player.subtitle

/**
 * How far captions are shifted from the picture: positive shows them later, negative earlier.
 * Decoders read it on every cue lookup, so a change applies to the track already on screen.
 */
class SubtitleDelay {
    @Volatile
    var offsetUs: Long = 0L
}
