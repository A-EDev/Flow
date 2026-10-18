package io.github.aedev.flow.ui.tv.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.PlayerPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The two Home settings TV Home honours; the feed itself comes from the shared HomeViewModel. */
@HiltViewModel
class TvHomeViewModel
    @Inject
    constructor(
        preferences: PlayerPreferences,
    ) : ViewModel() {
        val feedEnabled: StateFlow<Boolean> =
            preferences.homeFeedEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
        val refreshOnReselect: StateFlow<Boolean> =
            preferences.refreshHomeOnReselect.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    }
