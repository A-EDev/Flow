package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.data.model.Video

/**
 * Which ranked channel memory uploads still fit the feed: none already on screen, at most one per
 * channel across the whole feed, and never more than [room] (the lane's quota minus what it holds).
 */
internal fun channelMemoryPicks(
    ranked: List<Video>,
    onScreenIds: Set<String>,
    onScreenMemoryChannels: Set<String>,
    room: Int,
): List<Video> {
    if (room <= 0) return emptyList()
    val channels = HashSet(onScreenMemoryChannels)
    return ranked
        .asSequence()
        .filter { it.id !in onScreenIds }
        .filter { it.channelId.isNotBlank() && channels.add(it.channelId) }
        .take(room)
        .toList()
}
