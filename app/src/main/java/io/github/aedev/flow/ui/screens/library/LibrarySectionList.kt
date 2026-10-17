package io.github.aedev.flow.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.library.LibraryNavigationRow
import io.github.aedev.flow.ui.components.library.LibrarySection
import io.github.aedev.flow.ui.components.shared.FlowMaxContentWidth

@Composable
internal fun LibrarySectionList(
    sections: List<LibrarySection>,
    counts: LibraryCounts?,
    onOpenSection: (LibrarySection) -> Unit,
) {
    Column(modifier = Modifier.widthIn(max = FlowMaxContentWidth).padding(horizontal = 16.dp)) {
        LibrarySectionHeader(stringResource(R.string.library_section_header))
        sections.forEach { section ->
            LibrarySectionRow(section, counts) { onOpenSection(section) }
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
