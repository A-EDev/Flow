package io.github.aedev.flow.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aedev.flow.data.local.BackupRepository
import io.github.aedev.flow.notification.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity-scoped ViewModel for all data-import operations.
 *
 * Being scoped to the Activity (not the individual screen) means:
 *   - Import coroutines survive screen navigation (the user can leave the Import screen
 *     and the import carries on in the background inside viewModelScope).
 *   - A persistent notification keeps the user informed even when they are on a different screen.
 *   - Both the onboarding ImportStep and the Settings ImportDataScreen share the same instance,
 *     so a started import is visible from either screen.
 *
 * Usage from a @Composable:
 *   val activity = LocalContext.current as ComponentActivity
 *   val importViewModel: ImportViewModel = hiltViewModel(activity)
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    sealed class State {
        object Idle : State()

        /**
         * Import is running.
         * [current] / [total] are 0/0 while the file is being parsed (indeterminate phase).
         * Once avatar fetching starts, total is the channel count and current increments.
         */
        data class Running(val label: String, val current: Int, val total: Int) : State()

        /** Import finished successfully. Call [dismiss] to return to [Idle]. */
        data class Success(val label: String, val count: Int? = null, val message: String? = null) : State()

        /** Import failed. Call [dismiss] to return to [Idle]. */
        data class Error(val label: String, val message: String) : State()
    }

    private val backupRepo = BackupRepository(context)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val isRunning: Boolean get() = _state.value is State.Running

    // ── Public import launchers ───────────────────────────────────────────────

    fun importNewPipe(uri: Uri) {
        if (isRunning) return
        val label = "NewPipe subscriptions"
        viewModelScope.launch {
            startProgress(label, 0, 0)
            val result = backupRepo.importNewPipe(uri) { current, total ->
                updateProgress(label, current, total)
            }
            handleResult(label, result)
        }
    }

    fun importYouTube(uri: Uri) {
        if (isRunning) return
        val label = "YouTube subscriptions"
        viewModelScope.launch {
            startProgress(label, 0, 0)
            val result = backupRepo.importYouTube(uri) { current, total ->
                updateProgress(label, current, total)
            }
            handleResult(label, result)
        }
    }

    fun importYouTubeWatchHistory(uri: Uri) {
        importWatchHistory(uri, "YouTube watch history")
    }

    fun importFreeTubeWatchHistory(uri: Uri) {
        importWatchHistory(uri, "FreeTube watch history")
    }

    fun importNewPipeWatchHistory(uri: Uri) {
        if (isRunning) return
        val label = "NewPipe watch history"
        viewModelScope.launch {
            startProgress(label, 0, 0)
            val result = backupRepo.importNewPipeWatchHistory(uri)
            handleResult(label, result)
        }
    }

    fun importLibreTube(uri: Uri) {
        if (isRunning) return
        val label = "LibreTube subscriptions"
        viewModelScope.launch {
            startProgress(label, 0, 0)
            val result = backupRepo.importLibreTube(uri) { current, total ->
                updateProgress(label, current, total)
            }
            handleResult(label, result)
        }
    }

    fun importMetrolist(uri: Uri) {
        if (isRunning) return
        val label = "Metrolist music playlists"
        viewModelScope.launch {
            startProgress(label, 0, 0)
            val result = backupRepo.importMetrolist(uri) { current, total ->
                updateProgress(label, current, total)
            }
            handleResult(label, result)
        }
    }

    fun importNewPipePlaylists(uri: Uri) {
        if (isRunning) return
        val label = "NewPipe playlists"
        viewModelScope.launch {
            startProgress(label, 0, 0)
            val result = backupRepo.importNewPipePlaylists(uri) { current, total ->
                updateProgress(label, current, total)
            }
            handleResult(label, result)
        }
    }

    fun importLibreTubePlaylists(uri: Uri) {
        if (isRunning) return
        val label = "LibreTube playlists"
        viewModelScope.launch {
            startProgress(label, 0, 0)
            val result = backupRepo.importLibreTubePlaylists(uri) { current, total ->
                updateProgress(label, current, total)
            }
            handleResult(label, result)
        }
    }

    fun importYouTubeTakeout(uri: Uri) {
        if (isRunning) return
        val label = "YouTube Takeout"
        viewModelScope.launch {
            startProgress(label, 0, 0)
            val result = backupRepo.importYouTubeTakeout(uri) { stepLabel, current, total ->
                updateProgress("$label – $stepLabel", current, total)
            }
            NotificationHelper.cancelImportNotification(context)
            if (result.isSuccess) {
                val summary = result.getOrNull() ?: ""
                _state.value = State.Success(label, message = summary)
                if (NotificationHelper.hasNotificationPermission(context)) {
                    NotificationHelper.showImportComplete(context, label, 0, summary)
                }
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                _state.value = State.Error(label, msg)
            }
        }
    }

    fun importMasterBackup(uri: Uri) {
        if (isRunning) return
        val label = "Master backup"
        val successMessage = "Master backup restored successfully"
        viewModelScope.launch {
            startProgress(label, 0, 0)
            val result = backupRepo.importMasterBackup(uri)
            NotificationHelper.cancelImportNotification(context)
            if (result.isSuccess) {
                _state.value = State.Success(label, message = successMessage)
                if (NotificationHelper.hasNotificationPermission(context)) {
                    NotificationHelper.showImportComplete(context, label, 0, successMessage)
                }
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                _state.value = State.Error(label, msg)
            }
        }
    }

    /** Reset state back to [State.Idle] after the caller has handled a Success or Error. */
    fun dismiss() {
        _state.value = State.Idle
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun startProgress(label: String, current: Int, total: Int) {
        _state.value = State.Running(label, current, total)
        if (NotificationHelper.hasNotificationPermission(context)) {
            NotificationHelper.showImportProgress(context, label, current, total)
        }
    }

    private fun updateProgress(label: String, current: Int, total: Int) {
        _state.value = State.Running(label, current, total)
        if (NotificationHelper.hasNotificationPermission(context)) {
            NotificationHelper.showImportProgress(context, label, current, total)
        }
    }

    private fun importWatchHistory(uri: Uri, label: String) {
        if (isRunning) return
        viewModelScope.launch {
            startProgress(label, 0, 0)
            val result = backupRepo.importYouTubeWatchHistory(uri)
            handleResult(label, result)
        }
    }

    private fun handleResult(label: String, result: Result<Int>) {
        NotificationHelper.cancelImportNotification(context)
        if (result.isSuccess) {
            val count = result.getOrNull() ?: 0
            _state.value = State.Success(label, count = count)
            if (NotificationHelper.hasNotificationPermission(context)) {
                NotificationHelper.showImportComplete(context, label, count)
            }
        } else {
            val msg = result.exceptionOrNull()?.message ?: "Unknown error"
            _state.value = State.Error(label, msg)
        }
    }
}
