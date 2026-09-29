package io.github.aedev.flow.ui.screens.settings.localmedia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.localmedia.LocalMediaPreferences
import io.github.aedev.flow.data.localmedia.LocalMediaRepository
import io.github.aedev.flow.data.localmedia.LocalMediaSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SHARING_TIMEOUT_MS = 5_000L

/** A folder the viewer hid, with the name the device gives it. */
data class HiddenFolder(
    val id: String,
    val name: String,
)

@HiltViewModel
class LocalMediaSettingsViewModel
    @Inject
    constructor(
        repository: LocalMediaRepository,
        private val preferences: LocalMediaPreferences,
    ) : ViewModel() {
        val settings: StateFlow<LocalMediaSettings> =
            preferences.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARING_TIMEOUT_MS), LocalMediaSettings())

        val hiddenFolders: StateFlow<List<HiddenFolder>> =
            combine(repository.library, preferences.settings) { library, settings ->
                val names = (library.videos + library.music).associate { it.folderId to it.folderName }
                settings.hiddenFolderIds.map { id -> HiddenFolder(id, names[id] ?: id) }.sortedBy { it.name.lowercase() }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SHARING_TIMEOUT_MS), emptyList())

        fun setHideAppAudio(hide: Boolean) {
            viewModelScope.launch { preferences.setHideAppAudio(hide) }
        }

        fun setMinAudioSeconds(seconds: Int) {
            viewModelScope.launch { preferences.setMinAudioSeconds(seconds) }
        }

        fun showFolder(id: String) {
            viewModelScope.launch { preferences.setFolderHidden(id, hidden = false) }
        }
    }
