package io.github.aedev.flow.ui.screens.settings.network

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.network.AppProxyConfig
import io.github.aedev.flow.network.AppProxyType
import io.github.aedev.flow.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The proxy form as typed, before it is validated and saved. */
@Immutable
data class ProxyDraft(
    val enabled: Boolean,
    val type: AppProxyType,
    val host: String,
    val port: String,
    val username: String,
    val password: String,
) {
    val hostError: Boolean get() = enabled && host.isBlank()
    val portError: Boolean get() = enabled && port.toIntOrNull()?.let { it in PORT_RANGE } != true
    val valid: Boolean get() = !hostError && !portError

    fun toConfig(fallbackPort: Int) =
        AppProxyConfig(
            enabled = enabled,
            type = type,
            host = host.trim(),
            port = port.toIntOrNull() ?: fallbackPort,
            username = username.trim(),
            password = password,
        )

    companion object {
        val PORT_RANGE = 1..65535

        fun of(config: AppProxyConfig) =
            ProxyDraft(config.enabled, config.type, config.host, config.port.toString(), config.username, config.password)
    }
}

/**
 * Holds the proxy form while it is edited. Nothing reaches the network stack until [save], because a
 * half-typed host would otherwise reroute every request as each key is pressed.
 */
@HiltViewModel
class NetworkSettingsViewModel
    @Inject
    constructor(
        private val preferences: PlayerPreferences,
    ) : SettingsViewModel() {
        private var savedPort = DEFAULT_PORT
        private val _saved = MutableStateFlow<ProxyDraft?>(null)
        private val _draft = MutableStateFlow<ProxyDraft?>(null)
        val saved: StateFlow<ProxyDraft?> = _saved.asStateFlow()
        val draft: StateFlow<ProxyDraft?> = _draft.asStateFlow()

        init {
            viewModelScope.launch {
                val config = preferences.proxyConfig.first()
                savedPort = config.port
                _saved.value = ProxyDraft.of(config)
                _draft.value = ProxyDraft.of(config)
            }
        }

        fun edit(transform: (ProxyDraft) -> ProxyDraft) = _draft.update { it?.let(transform) }

        /** Saves the draft when it is valid; reports whether it was. */
        fun save(): Boolean {
            val current = _draft.value ?: return false
            if (!current.valid) return false
            val config = current.toConfig(savedPort)
            savedPort = config.port
            _saved.value = ProxyDraft.of(config)
            _draft.value = ProxyDraft.of(config)
            write { preferences.setProxyConfig(config) }
            return true
        }

        private companion object {
            const val DEFAULT_PORT = 8080
        }
    }
