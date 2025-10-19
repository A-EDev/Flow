package com.flow.youtube.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flow.youtube.ui.theme.ThemeMode
import com.flow.youtube.ui.theme.extendedColors

@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showRegionDialog by remember { mutableStateOf(false) }
    var backgroundPlayEnabled by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp)
        ) {
            // Appearance Section
            item {
                SectionHeader(text = "Appearance")
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.Palette,
                    title = "Theme",
                    subtitle = when (currentTheme) {
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                        ThemeMode.OLED -> "OLED Black"
                        ThemeMode.OCEAN_BLUE -> "Ocean Blue"
                        ThemeMode.FOREST_GREEN -> "Forest Green"
                        ThemeMode.SUNSET_ORANGE -> "Sunset Orange"
                        ThemeMode.PURPLE_NEBULA -> "Purple Nebula"
                        ThemeMode.MIDNIGHT_BLACK -> "Midnight Black"
                        ThemeMode.ROSE_GOLD -> "Rose Gold"
                        ThemeMode.ARCTIC_ICE -> "Arctic Ice"
                        ThemeMode.CRIMSON_RED -> "Crimson Red"
                    },
                    onClick = { showThemeDialog = true }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // Content & Playback Section
            item {
                SectionHeader(text = "Content & Playback")
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.TrendingUp,
                    title = "Trending Region",
                    subtitle = "United States",
                    onClick = { showRegionDialog = true }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                SettingsItem(
                    icon = Icons.Outlined.HighQuality,
                    title = "Default Video Quality",
                    subtitle = "Wi-Fi: 1080p • Cellular: 480p",
                    onClick = { showVideoQualityDialog = true }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                SettingsSwitchItem(
                    icon = Icons.Outlined.PlayCircle,
                    title = "Background Play",
                    subtitle = "Continue playing when app is in background",
                    checked = backgroundPlayEnabled,
                    onCheckedChange = { backgroundPlayEnabled = it }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // Privacy & Data Section
            item {
                SectionHeader(text = "Privacy & Data Management")
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.DeleteSweep,
                    title = "Clear Watch History",
                    subtitle = "Remove all watched videos",
                    onClick = { /* Show confirmation */ }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                SettingsItem(
                    icon = Icons.Outlined.ManageSearch,
                    title = "Clear Search History",
                    subtitle = "Remove all search queries",
                    onClick = { /* Show confirmation */ }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                SettingsItem(
                    icon = Icons.Outlined.FileUpload,
                    title = "Export Data",
                    subtitle = "Backup your data to a file",
                    onClick = { /* Export */ }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                SettingsItem(
                    icon = Icons.Outlined.FileDownload,
                    title = "Import Data",
                    subtitle = "Restore from backup file",
                    onClick = { /* Import */ }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // About Section
            item {
                SectionHeader(text = "About")
            }

            item {
                SettingsItem(
                    icon = Icons.Outlined.Info,
                    title = "App Version",
                    subtitle = "1.0.0",
                    onClick = { }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                val context = LocalContext.current
                SettingsItem(
                    icon = Icons.Outlined.Person,
                    title = "Made By A-EDev",
                    subtitle = "Tap to visit GitHub profile",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/A-EDev"))
                        context.startActivity(intent)
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                SettingsItem(
                    icon = Icons.Outlined.Code,
                    title = "Powered by NewPipeExtractor",
                    subtitle = "Open source YouTube extraction library",
                    onClick = { }
                )
            }
        }
    }

    // Theme Dialog
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = currentTheme,
            onThemeSelected = {
                onThemeChange(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textSecondary
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Theme") },
        text = {
            Column {
                ThemeMode.values().forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onThemeSelected(theme) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (theme) {
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                                ThemeMode.OLED -> "OLED Black"
                                ThemeMode.OCEAN_BLUE -> "Ocean Blue"
                                ThemeMode.FOREST_GREEN -> "Forest Green"
                                ThemeMode.SUNSET_ORANGE -> "Sunset Orange"
                                ThemeMode.PURPLE_NEBULA -> "Purple Nebula"
                                ThemeMode.MIDNIGHT_BLACK -> "Midnight Black"
                                ThemeMode.ROSE_GOLD -> "Rose Gold"
                                ThemeMode.ARCTIC_ICE -> "Arctic Ice"
                                ThemeMode.CRIMSON_RED -> "Crimson Red"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (theme == currentTheme) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
