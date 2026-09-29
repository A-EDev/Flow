package io.github.aedev.flow.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Base for the settings pages' view models, which are mostly thin views over preference flows.
 * Every exposed value stops collecting five seconds after its page leaves the screen.
 */
abstract class SettingsViewModel : ViewModel() {
    protected fun <T> Flow<T>.asState(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

    protected fun write(block: suspend () -> Unit): Job = viewModelScope.launch { block() }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
