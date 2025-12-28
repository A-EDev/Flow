package com.flow.youtube.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.flow.youtube.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onNavigateBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: System (All), 1: Light, 2: Dark
    
    // Filter themes based on tab
    val themes = when (selectedTab) {
        1 -> listOf(ThemeMode.LIGHT)
        2 -> ThemeMode.values().filter { it != ThemeMode.LIGHT }.toList()
        else -> ThemeMode.values().toList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Theme",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TabButton(
                    text = "All",
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f)
                )
                TabButton(
                    text = "Light",
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f)
                )
                TabButton(
                    text = "Dark",
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    modifier = Modifier.weight(1f)
                )
            }


            Spacer(modifier = Modifier.height(24.dp))

            // Theme Grid/List
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(themes) { theme ->
                    ThemePreviewCard(
                        theme = theme,
                        isSelected = currentTheme == theme,
                        onClick = { onThemeChange(theme) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Other settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pure black dark mode",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = currentTheme == ThemeMode.OLED || currentTheme == ThemeMode.MIDNIGHT_BLACK,
                    onCheckedChange = { isChecked ->
                        if (isChecked) onThemeChange(ThemeMode.OLED)
                        else if (currentTheme == ThemeMode.OLED) onThemeChange(ThemeMode.DARK)
                    }
                )
            }
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemePreviewCard(
    theme: ThemeMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = getThemeColors(theme)
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)
    ) {
        Box(
            modifier = Modifier
                .height(160.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp)
                )
                .background(colorScheme.background)
                .clickable(onClick = onClick)
        ) {
            // Mock UI elements inside the card
            Column(modifier = Modifier.padding(8.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colorScheme.onSurface.copy(alpha = 0.2f))
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(16.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                .padding(2.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Content blocks
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorScheme.surfaceVariant)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colorScheme.onSurface.copy(alpha = 0.1f))
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colorScheme.onSurface.copy(alpha = 0.1f))
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // FAB
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary)
                        .align(Alignment.End)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = formatThemeName(theme),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatThemeName(theme: ThemeMode): String {
    return theme.name.split("_")
        .joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }
}

@Composable
private fun getThemeColors(theme: ThemeMode): ColorScheme {
    // We approximate the colors here for the preview cards
    return when (theme) {
        ThemeMode.LIGHT -> lightColorScheme()
        ThemeMode.DARK -> darkColorScheme()
        ThemeMode.OLED -> darkColorScheme(background = Color.Black, surface = Color(0xFF121212))
        ThemeMode.LAVENDER_MIST -> darkColorScheme(primary = Color(0xFFB39DDB), background = Color(0xFF120F1A))
        ThemeMode.OCEAN_BLUE -> darkColorScheme(primary = Color(0xFF006994), background = Color(0xFF0A1929))
        ThemeMode.FOREST_GREEN -> darkColorScheme(primary = Color(0xFF2E7D32), background = Color(0xFF0D1F12))
        ThemeMode.SUNSET_ORANGE -> darkColorScheme(primary = Color(0xFFFF6F00), background = Color(0xFF1F0F08))
        ThemeMode.PURPLE_NEBULA -> darkColorScheme(primary = Color(0xFF7B1FA2), background = Color(0xFF1A0C26))
        ThemeMode.MIDNIGHT_BLACK -> darkColorScheme(primary = Color(0xFF00BCD4), background = Color.Black)
        ThemeMode.ROSE_GOLD -> darkColorScheme(primary = Color(0xFFE91E63), background = Color(0xFF1A0D12))
        ThemeMode.ARCTIC_ICE -> darkColorScheme(primary = Color(0xFF00BCD4), background = Color(0xFF0E1821))
        ThemeMode.CRIMSON_RED -> darkColorScheme(primary = Color(0xFFDC143C), background = Color(0xFF1A0A0A))
        ThemeMode.MINTY_FRESH -> darkColorScheme(primary = Color(0xFF80CBC4), background = Color(0xFF0F1A18))
        ThemeMode.COSMIC_VOID -> darkColorScheme(primary = Color(0xFF7C4DFF), background = Color(0xFF050505))
        ThemeMode.SOLAR_FLARE -> darkColorScheme(primary = Color(0xFFFFD740), background = Color(0xFF1A1500))
        ThemeMode.CYBERPUNK -> darkColorScheme(primary = Color(0xFFFF00FF), background = Color(0xFF0D001A))
        // NEW ADDITIONS
        ThemeMode.ROYAL_GOLD -> darkColorScheme(primary = Color(0xFFFFD700), background = Color(0xFF050505))
        ThemeMode.NORDIC_HORIZON -> darkColorScheme(primary = Color(0xFF88C0D0), background = Color(0xFF242933))
        ThemeMode.ESPRESSO -> darkColorScheme(primary = Color(0xFFD7CCC8), background = Color(0xFF181210))
        ThemeMode.GUNMETAL -> darkColorScheme(primary = Color(0xFF78909C), background = Color(0xFF0F1216))
    }
}