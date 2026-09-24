package io.github.aedev.flow.ui.components.musicplayer

enum class SkipDirection {
    NEXT,
    PREVIOUS,
}

fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).toInt()
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}
