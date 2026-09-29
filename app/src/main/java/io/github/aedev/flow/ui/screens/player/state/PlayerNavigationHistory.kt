package io.github.aedev.flow.ui.screens.player.state

/**
 * The player screen's own record of which videos were opened from inside the player, kept
 * deliberately alongside the playback queue: the queue answers "what plays next", this answers
 * "what did the user come from", and a related-video tap only ever appears in this one.
 *
 * Opening a video after stepping back drops everything ahead of the cursor, the way a browser
 * history does.
 */
internal class PlayerNavigationHistory(
    private val maxEntries: Int = MAX_ENTRIES,
) {
    private val entries = mutableListOf<String>()
    private var cursor = -1

    val canGoPrevious: Boolean
        get() = cursor > 0

    val size: Int
        get() = entries.size

    /** Records [videoId] as the entry being watched. A repeat of the current entry changes nothing. */
    fun push(videoId: String) {
        if (entries.isNotEmpty() && entries[cursor] == videoId) return

        while (entries.size - 1 > cursor) {
            entries.removeAt(entries.size - 1)
        }
        entries.add(videoId)
        while (entries.size > maxEntries) {
            entries.removeAt(0)
        }
        cursor = entries.size - 1
    }

    /** Steps the cursor back one entry and returns it, or null at the start of the history. */
    fun previous(): String? {
        if (cursor <= 0 || cursor >= entries.size) return null
        cursor--
        return entries.getOrNull(cursor)
    }

    fun clear() {
        entries.clear()
        cursor = -1
    }

    private companion object {
        const val MAX_ENTRIES = 100
    }
}
