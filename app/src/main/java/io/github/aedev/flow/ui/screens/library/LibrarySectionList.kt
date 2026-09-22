package io.github.aedev.flow.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.library.LibraryNavigationRow

@Composable
internal fun LibrarySectionList(
    counts: LibraryCounts?,
    shortsEnabled: Boolean,
    onNavigateToHistory: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToLikedVideos: () -> Unit,
    onNavigateToWatchLater: () -> Unit,
    onNavigateToSavedShorts: () -> Unit,
    onNavigateToDownloads: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        LibrarySectionHeader(stringResource(R.string.library_section_header))
        LibrarySectionRow(LibrarySection.HISTORY, counts, onNavigateToHistory)
        LibrarySectionRow(LibrarySection.PLAYLISTS, counts, onNavigateToPlaylists)
        LibrarySectionRow(LibrarySection.WATCH_LATER, counts, onNavigateToWatchLater)
        LibrarySectionRow(LibrarySection.LIKES, counts, onNavigateToLikedVideos)
        LibrarySectionRow(LibrarySection.DOWNLOADS, counts, onNavigateToDownloads)
        if (shortsEnabled) {
            LibrarySectionRow(LibrarySection.SAVED_SHORTS, counts, onNavigateToSavedShorts)
        }
    }
}

@Composable
internal fun LibrarySectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

@Composable
internal fun LibrarySectionRow(
    section: LibrarySection,
    counts: LibraryCounts?,
    onClick: () -> Unit,
) {
    LibraryNavigationRow(
        icon = section.icon,
        title = section.title,
        subtitle = section.subtitle(counts),
        onClick = onClick,
    )
}
