package io.github.aedev.flow.ui.screens.settings.appearance

import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.ui.screens.settings.SettingsViewModel
import io.github.aedev.flow.utils.DateContextMode
import io.github.aedev.flow.utils.DateDisplayMode
import io.github.aedev.flow.utils.DateFormatStyle
import javax.inject.Inject

@HiltViewModel
class DateTimeViewModel
    @Inject
    constructor(
        private val preferences: PlayerPreferences,
    ) : SettingsViewModel() {
        val mode = preferences.dateDisplayMode.asState(DateDisplayMode.RELATIVE)
        val formatStyle = preferences.dateFormatStyle.asState(DateFormatStyle.SYSTEM)
        val listsMode = preferences.dateModeLists.asState(DateContextMode.DEFAULT)
        val watchMode = preferences.dateModeWatch.asState(DateContextMode.DEFAULT)
        val descriptionMode = preferences.dateModeDescription.asState(DateContextMode.DEFAULT)

        fun setMode(value: DateDisplayMode) = write { preferences.setDateDisplayMode(value) }

        fun setFormatStyle(value: DateFormatStyle) = write { preferences.setDateFormatStyle(value) }

        fun setListsMode(value: DateContextMode) = write { preferences.setDateModeLists(value) }

        fun setWatchMode(value: DateContextMode) = write { preferences.setDateModeWatch(value) }

        fun setDescriptionMode(value: DateContextMode) = write { preferences.setDateModeDescription(value) }
    }
