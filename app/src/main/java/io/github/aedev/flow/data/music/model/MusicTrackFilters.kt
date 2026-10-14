package io.github.aedev.flow.data.music.model

/** A song the audio player can play as music: not a video, an album row, or a clip too short or long. */
fun MusicTrack.isAudioMusicCandidate(): Boolean {
    val usableDuration = duration == 0 || duration in 30..1200
    return itemType == MusicItemType.SONG && !isVideoSong && videoId.isNotBlank() && usableDuration
}

fun List<MusicTrack>.audioMusicOnly(): List<MusicTrack> = filter { it.isAudioMusicCandidate() }.distinctBy { it.videoId }
