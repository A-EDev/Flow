package io.github.aedev.flow.ui.components.music.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.aedev.flow.data.music.model.MusicTrack

/**
 * The one place a song or album menu is opened from. Screens and shelves call [openSong] or
 * [openCollection]; the shell draws the sheet once through [MusicMenuSheets], so no screen hosts
 * its own copy.
 */
@Stable
class MusicMenus {
    internal var song by mutableStateOf<MusicTrack?>(null)
    internal var collection by mutableStateOf<MusicCollectionActionItem?>(null)

    fun openSong(track: MusicTrack) {
        song = track
    }

    fun openCollection(item: MusicCollectionActionItem) {
        collection = item
    }
}

/** Static because the shell provides one host for its whole lifetime. Without one, menus open nothing. */
val LocalMusicMenus = staticCompositionLocalOf { MusicMenus() }

@Composable
fun rememberMusicMenus(): MusicMenus = remember { MusicMenus() }

/** Draws whichever menu [menus] has open. Call once, in the shell. */
@Composable
fun MusicMenuSheets(menus: MusicMenus) {
    menus.song?.let { track ->
        MusicQuickActionsSheet(track = track, onDismiss = { menus.song = null })
    }
    menus.collection?.let { item ->
        MusicCollectionQuickActionsSheet(item = item, onDismiss = { menus.collection = null })
    }
}
