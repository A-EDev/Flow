package io.github.aedev.flow.data.backup

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.aedev.flow.R

/**
 * The apps a person can move their data from, each with the imports it offers. People look for an
 * import by the app they are leaving, so Settings and onboarding both group them this way.
 */
enum class ImportSource(
    @StringRes val titleRes: Int,
    @StringRes val shortTitleRes: Int,
    @DrawableRes val iconRes: Int?,
    val kinds: List<ImportKind>,
) {
    FLOW(
        R.string.settings_backup_restore_flow,
        R.string.onboarding_import_source_flow,
        R.drawable.ic_flow_logo,
        listOf(ImportKind.FLOW_BACKUP, ImportKind.MASTER, ImportKind.ENGINE, ImportKind.MUSIC_BRAIN),
    ),
    YOUTUBE(
        R.string.settings_backup_source_youtube,
        R.string.onboarding_import_source_youtube,
        R.drawable.ic_youtube,
        listOf(
            ImportKind.TAKEOUT,
            ImportKind.YOUTUBE_SUBSCRIPTIONS,
            ImportKind.YOUTUBE_HISTORY,
            ImportKind.YOUTUBE_PLAYLIST,
            ImportKind.WATCH_LATER,
            ImportKind.YOUTUBE_MUSIC_PLAYLIST,
        ),
    ),
    NEWPIPE(
        R.string.settings_backup_source_newpipe,
        R.string.settings_backup_source_newpipe,
        R.drawable.ic_newpipe,
        listOf(ImportKind.NEWPIPE_SUBSCRIPTIONS, ImportKind.NEWPIPE_HISTORY, ImportKind.NEWPIPE_PLAYLISTS),
    ),
    LIBRETUBE(
        R.string.settings_backup_source_libretube,
        R.string.settings_backup_source_libretube,
        R.drawable.ic_libretube,
        listOf(ImportKind.LIBRETUBE_SUBSCRIPTIONS, ImportKind.LIBRETUBE_PLAYLISTS),
    ),
    FREETUBE(
        R.string.settings_backup_source_freetube,
        R.string.settings_backup_source_freetube,
        null,
        listOf(ImportKind.FREETUBE_HISTORY),
    ),
    METROLIST(
        R.string.settings_backup_source_metrolist,
        R.string.settings_backup_source_metrolist,
        R.drawable.ic_metrolist,
        listOf(ImportKind.METROLIST),
    ),
}
