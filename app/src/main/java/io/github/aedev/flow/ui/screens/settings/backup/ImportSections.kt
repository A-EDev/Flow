package io.github.aedev.flow.ui.screens.settings.backup

import io.github.aedev.flow.R
import io.github.aedev.flow.data.backup.ImportKind

/** Import sources grouped by the app a person is moving from, which is how they look for them. */
internal val ImportSections: List<Pair<Int, List<ImportKind>>> =
    listOf(
        R.string.settings_backup_restore_flow to
            listOf(ImportKind.FLOW_BACKUP, ImportKind.MASTER, ImportKind.ENGINE, ImportKind.MUSIC_BRAIN),
        R.string.settings_backup_source_youtube to
            listOf(
                ImportKind.TAKEOUT,
                ImportKind.YOUTUBE_SUBSCRIPTIONS,
                ImportKind.YOUTUBE_HISTORY,
                ImportKind.YOUTUBE_PLAYLIST,
                ImportKind.WATCH_LATER,
                ImportKind.YOUTUBE_MUSIC_PLAYLIST,
            ),
        R.string.settings_backup_source_newpipe to
            listOf(ImportKind.NEWPIPE_SUBSCRIPTIONS, ImportKind.NEWPIPE_HISTORY, ImportKind.NEWPIPE_PLAYLISTS),
        R.string.settings_backup_source_libretube to listOf(ImportKind.LIBRETUBE_SUBSCRIPTIONS, ImportKind.LIBRETUBE_PLAYLISTS),
        R.string.settings_backup_source_freetube to listOf(ImportKind.FREETUBE_HISTORY),
        R.string.settings_backup_source_metrolist to listOf(ImportKind.METROLIST),
    )
