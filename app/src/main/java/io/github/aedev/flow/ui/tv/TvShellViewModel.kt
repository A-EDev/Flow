package io.github.aedev.flow.ui.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.ui.components.layout.navigation.NavigationVisibility
import io.github.aedev.flow.ui.tv.navigation.TvNavigation
import io.github.aedev.flow.ui.tv.navigation.tvNavigation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The TV shell's tabs, read from the same navigation settings the phone's bar uses. */
@HiltViewModel
class TvShellViewModel
    @Inject
    constructor(
        preferences: PlayerPreferences,
    ) : ViewModel() {
        val navigation: StateFlow<TvNavigation?> =
            combine(
                preferences.navTabOrder,
                preferences.defaultNavTabIndex,
                preferences.homeNavigationEnabled,
                preferences.musicNavigationEnabled,
            ) { order, defaultTabId, home, music ->
                tvNavigation(order, defaultTabId, NavigationVisibility(home = home, music = music))
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }
