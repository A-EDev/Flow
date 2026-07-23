package io.github.aedev.flow.ui.screens.sync

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.sync.SyncManager
import io.github.aedev.flow.sync.SyncState
import io.github.aedev.flow.sync.protocol.SyncRole
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Thin ViewModel over the singleton [SyncManager] (which survives config changes). */
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val manager: SyncManager,
) : ViewModel() {

    val state: StateFlow<SyncState> = manager.state

    fun host(role: SyncRole, collections: List<String>) = manager.host(role, collections)
    fun hostForTv(role: SyncRole, collections: List<String>) = manager.hostForTv(role, collections)
    fun join(role: SyncRole, qrText: String, collections: List<String>) = manager.join(role, qrText, collections)
    fun confirmSas(matches: Boolean) = manager.confirmSas(matches)
    fun confirmConsent(accepted: Boolean) = manager.confirmConsent(accepted)
    fun cancel() = manager.cancel()
    fun reset() = manager.reset()
}
