package io.github.aedev.flow.ui.screens.settings.network

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.network.AppProxyType
import io.github.aedev.flow.ui.components.settings.SettingEntry
import io.github.aedev.flow.ui.components.settings.SettingsGroupScope
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.settings.notice
import io.github.aedev.flow.ui.components.settings.switch
import io.github.aedev.flow.ui.components.settings.toggleGroup
import io.github.aedev.flow.ui.components.shared.FlowToggleOption
import io.github.aedev.flow.ui.screens.settings.index.NetworkIndex
import kotlinx.coroutines.launch

private val FieldPadding = 12.dp

/** The optional proxy for remote traffic, edited as a form and applied on Save. */
@Composable
internal fun NetworkSettingsScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
    viewModel: NetworkSettingsViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.proxy_settings_saved)
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val types =
        listOf(
            FlowToggleOption(AppProxyType.HTTP, stringResource(R.string.proxy_type_http)),
            FlowToggleOption(AppProxyType.SOCKS5, stringResource(R.string.proxy_type_socks5)),
        )
    val hostError = stringResource(R.string.proxy_settings_invalid_host)
    val portError = stringResource(R.string.proxy_settings_invalid_port)
    val passwordToggle = stringResource(if (passwordVisible) R.string.settings_hide_password else R.string.settings_show_password)

    SettingsPage(
        title = stringResource(R.string.settings_network_title),
        onBack = onBack,
        highlight = highlight,
        snackbarHostState = snackbarHostState,
        actions = {
            TextButton(
                enabled = draft != null && draft != saved && draft?.valid == true,
                onClick = {
                    if (viewModel.save()) scope.launch { snackbarHostState.showSnackbar(savedMessage) }
                },
            ) { Text(stringResource(R.string.save)) }
        },
    ) {
        val form = draft ?: return@SettingsPage
        notice("network.about", text = { stringResource(R.string.proxy_settings_description) }, icon = Icons.Outlined.Info)
        group(key = "network.proxy", header = R.string.proxy_settings_title) {
            switch(NetworkIndex.enabled, form.enabled, { on -> viewModel.edit { it.copy(enabled = on) } }, icon = Icons.Outlined.Public)
            toggleGroup(NetworkIndex.type, types, form.type, { type -> viewModel.edit { it.copy(type = type) } })
        }
        group(key = "network.server", header = R.string.settings_section_proxy_server) {
            field(NetworkIndex.host, form.host, { value -> viewModel.edit { it.copy(host = value) } }, Icons.Outlined.Public) {
                error = hostError.takeIf { form.hostError }
            }
            field(
                NetworkIndex.port,
                form.port,
                { value -> viewModel.edit { it.copy(port = value.filter(Char::isDigit)) } },
                Icons.Outlined.Tag,
            ) {
                error = portError.takeIf { form.portError }
                keyboardType = KeyboardType.Number
            }
        }
        group(
            key = "network.sign_in",
            header = R.string.settings_section_proxy_sign_in,
            footer = R.string.proxy_settings_optional_auth,
        ) {
            field(NetworkIndex.username, form.username, { value -> viewModel.edit { it.copy(username = value) } }, Icons.Outlined.Person)
            field(NetworkIndex.password, form.password, { value -> viewModel.edit { it.copy(password = value) } }, Icons.Outlined.Lock) {
                keyboardType = KeyboardType.Password
                hidden = !passwordVisible
                trailing = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = passwordToggle,
                        )
                    }
                }
            }
        }
        notice("network.restart", text = { stringResource(R.string.proxy_settings_restart_notice) })
    }
}

private class FieldOptions {
    var error: String? = null
    var keyboardType: KeyboardType = KeyboardType.Text
    var hidden: Boolean = false
    var trailing: (@Composable () -> Unit)? = null
}

private fun SettingsGroupScope.field(
    entry: SettingEntry,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    configure: FieldOptions.() -> Unit = {},
) {
    val options = FieldOptions().apply(configure)
    row(entry.key) { shape -> SettingsTextField(entry, value, onValueChange, icon, options, shape) }
}

@Composable
private fun SettingsTextField(
    entry: SettingEntry,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    options: FieldOptions,
    shape: Shape,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(FieldPadding),
            label = { Text(stringResource(entry.title)) },
            leadingIcon = { Icon(icon, contentDescription = null) },
            trailingIcon = options.trailing,
            isError = options.error != null,
            supportingText = options.error?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = options.keyboardType),
            visualTransformation = if (options.hidden) PasswordVisualTransformation() else VisualTransformation.None,
        )
    }
}
