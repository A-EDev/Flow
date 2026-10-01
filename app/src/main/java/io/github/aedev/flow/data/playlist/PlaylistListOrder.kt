package io.github.aedev.flow.data.playlist

import io.github.aedev.flow.data.model.PlaylistInfo
import java.text.Collator

/** How a list of playlists is ordered; [CUSTOM] follows the order the viewer dragged them into. */
enum class PlaylistListOrder(
    val storageValue: String,
) {
    NEWEST("newest"),
    OLDEST("oldest"),
    NAME("name"),
    CUSTOM("custom"),
    ;

    companion object {
        fun fromStorageValue(value: String?): PlaylistListOrder = entries.firstOrNull { it.storageValue == value } ?: NEWEST
    }
}

fun List<PlaylistInfo>.sortedFor(order: PlaylistListOrder): List<PlaylistInfo> =
    when (order) {
        PlaylistListOrder.NEWEST -> {
            sortedByDescending { it.createdAt }
        }

        PlaylistListOrder.OLDEST -> {
            sortedBy { it.createdAt }
        }

        PlaylistListOrder.NAME -> {
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            sortedWith(compareBy(collator) { it.name })
        }

        PlaylistListOrder.CUSTOM -> {
            sortedWith(compareBy<PlaylistInfo> { it.position }.thenByDescending { it.createdAt })
        }
    }
