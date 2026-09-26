package io.github.aedev.flow.ui.components.videoplayer.sheet

import io.github.aedev.flow.data.model.Video

/**
 * One key per queue row that survives removals and moves: the video id, plus how many times the
 * same video appeared earlier, since a queue can hold one video twice.
 */
internal fun queueRowKeys(videos: List<Video>): List<String> {
    val seen = HashMap<String, Int>()
    return videos.map { video ->
        val occurrence = seen.merge(video.id, 1, Int::plus)!! - 1
        "${video.id}#$occurrence"
    }
}
