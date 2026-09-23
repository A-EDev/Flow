package io.github.aedev.flow.ui.screens.settings.diagnostics

import android.content.Context
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.player.stream.ClientGateTracker
import io.github.aedev.flow.ui.screens.settings.SettingsViewModel
import io.github.aedev.flow.utils.DeviceInfo
import io.github.aedev.flow.utils.FlowDiagnostics
import io.github.aedev.flow.utils.cipher.CipherDeobfuscator
import io.github.aedev.flow.utils.cipher.PlayerJsFetcher
import io.github.aedev.flow.utils.potoken.WebPoTokenSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SettingsViewModel() {
        val device: DeviceInfo by lazy { FlowDiagnostics.deviceInfo(context) }

        private val _session = MutableStateFlow<LogState>(LogState.Loading)
        private val _crashes = MutableStateFlow<LogState>(LogState.Loading)
        val session: StateFlow<LogState> = _session.asStateFlow()
        val crashes: StateFlow<LogState> = _crashes.asStateFlow()

        private var sessionText = ""

        init {
            viewModelScope.launch(Dispatchers.IO) {
                sessionText = FlowDiagnostics.readSessionLogs()
                _session.value = parseLog(sessionText, ::logcatLevel)
                _crashes.value = parseLog(FlowDiagnostics.crashLogsOrNull(context), ::crashLevel)
            }
        }

        suspend fun report(): String = withContext(Dispatchers.IO) { FlowDiagnostics.buildFullReport(context, sessionText) }

        fun clearCrashes() =
            write {
                withContext(Dispatchers.IO) { FlowDiagnostics.clearCrashLogs(context) }
                _crashes.value = LogState.Empty
            }

        /**
         * Re-attesting alone cannot lift a verdict the stream servers already reached about this
         * visitor, so the identity itself is discarded: the supported form of the "clear app data"
         * workaround for videos that stall a minute in.
         */
        suspend fun resetSession() {
            WebPoTokenSession.resetIdentity()
            ClientGateTracker.clear()
            CipherDeobfuscator.invalidateSignatureTimestamp()
            withContext(Dispatchers.IO) { PlayerJsFetcher.invalidateCache() }
        }
    }
