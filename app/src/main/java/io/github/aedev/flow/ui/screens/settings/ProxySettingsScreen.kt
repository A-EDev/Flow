package io.github.aedev.flow.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.network.AppProxyConfig
import io.github.aedev.flow.network.AppProxyType
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun ProxySettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferences = remember { PlayerPreferences(context) }

    val savedConfig by preferences.proxyConfig.collectAsState(initial = AppProxyConfig())

    var enabled by remember(savedConfig) { mutableStateOf(savedConfig.enabled) }
    var type by remember(savedConfig) { mutableStateOf(savedConfig.type) }
    var host by remember(savedConfig) { mutableStateOf(savedConfig.host) }
    var portText by remember(savedConfig) { mutableStateOf(savedConfig.port.toString()) }
    var username by remember(savedConfig) { mutableStateOf(savedConfig.username) }
    var password by remember(savedConfig) { mutableStateOf(savedConfig.password) }
    var showTypeDialog by remember { mutableStateOf(false) }

    val parsedPort = portText.toIntOrNull()
    val hostError = enabled && host.isBlank()
    val portError = enabled && (parsedPort == null || parsedPort !in 1..65535)
    val canSave = !hostError && !portError

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                    }
                    Text(
                        text = stringResource(R.string.proxy_settings_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.proxy_settings_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Outlined.Public,
                        title = stringResource(R.string.proxy_settings_enabled),
                        subtitle = stringResource(R.string.proxy_settings_enabled_subtitle),
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                    HorizontalDivider(
                        Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.Outlined.Router,
                        title = stringResource(R.string.proxy_settings_type),
                        subtitle = when (type) {
                            AppProxyType.HTTP -> stringResource(R.string.proxy_type_http)
                            AppProxyType.SOCKS5 -> stringResource(R.string.proxy_type_socks5)
                        },
                        onClick = { showTypeDialog = true }
                    )
                }
            }

            item {
                SettingsGroup {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.proxy_settings_host)) },
                            singleLine = true,
                            isError = hostError,
                            leadingIcon = { Icon(Icons.Outlined.Public, contentDescription = null) }
                        )
                        if (hostError) {
                            Text(
                                text = stringResource(R.string.proxy_settings_invalid_host),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it.filter(Char::isDigit) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.proxy_settings_port)) },
                            singleLine = true,
                            isError = portError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            leadingIcon = { Icon(Icons.Outlined.Tag, contentDescription = null) }
                        )
                        if (portError) {
                            Text(
                                text = stringResource(R.string.proxy_settings_invalid_port),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.proxy_settings_username)) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Outlined.Public, contentDescription = null) }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.proxy_settings_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.proxy_settings_optional_auth),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SettingsGroup {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.proxy_settings_restart_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        if (!canSave) return@Button
                        coroutineScope.launch {
                            preferences.setProxyConfig(
                                AppProxyConfig(
                                    enabled = enabled,
                                    type = type,
                                    host = host,
                                    port = parsedPort ?: savedConfig.port,
                                    username = username,
                                    password = password
                                )
                            )
                            Toast.makeText(
                                context,
                                context.getString(R.string.proxy_settings_saved),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.btn_save))
                }
            }
        }
    }

    if (showTypeDialog) {
        AlertDialog(
            onDismissRequest = { showTypeDialog = false },
            title = { Text(stringResource(R.string.proxy_settings_type)) },
            text = {
                Column {
                    TextButton(onClick = {
                        type = AppProxyType.HTTP
                        showTypeDialog = false
                    }) {
                        Text(stringResource(R.string.proxy_type_http))
                    }
                    TextButton(onClick = {
                        type = AppProxyType.SOCKS5
                        showTypeDialog = false
                    }) {
                        Text(stringResource(R.string.proxy_type_socks5))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTypeDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}