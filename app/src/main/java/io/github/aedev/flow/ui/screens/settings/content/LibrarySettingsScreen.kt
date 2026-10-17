package io.github.aedev.flow.ui.screens.settings.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.library.LibrarySection
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.settings.switch
import io.github.aedev.flow.ui.screens.settings.index.LibrarySectionsIndex

/** One switch per Library section, laid out as Library lays them out; a hidden section is left out of Library. */
@Composable
internal fun LibrarySettingsScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
    viewModel: LibrarySettingsViewModel = hiltViewModel(),
) {
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    val separatePlaylists by viewModel.separatePlaylists.collectAsStateWithLifecycle()
    val shortsContent by viewModel.shortsContent.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()

    fun enabled(section: LibrarySection): Boolean =
        when (section) {
            LibrarySection.SAVED_SHORTS -> shortsContent
            LibrarySection.NOTES -> notes
            else -> true
        }

    SettingsPage(title = stringResource(R.string.library), onBack = onBack, highlight = highlight) {
        group(key = "library.content", header = R.string.library_section_header) {
            LibrarySection.content(separatePlaylists).forEach { section ->
                switch(
                    LibrarySectionsIndex.entry(section),
                    checked = section !in hidden,
                    onCheckedChange = { viewModel.setShown(section, it) },
                    enabled = enabled(section),
                )
            }
        }
        group(key = "library.settings_data", header = R.string.library_settings_data_header) {
            LibrarySection.settingsAndData.forEach { section ->
                switch(
                    LibrarySectionsIndex.entry(section),
                    checked = section !in hidden,
                    onCheckedChange = { viewModel.setShown(section, it) },
                    enabled = enabled(section),
                )
            }
        }
    }
}
