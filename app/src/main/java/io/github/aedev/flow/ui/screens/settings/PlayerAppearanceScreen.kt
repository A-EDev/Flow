package io.github.aedev.flow.ui.screens.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SliderStyle
import io.github.aedev.flow.ui.screens.music.player.components.PlayerSliderTrack
import io.github.aedev.flow.ui.screens.music.player.components.SquigglySlider
import io.github.aedev.flow.ui.components.rememberFlowSheetState
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerAppearanceScreen(
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerPreferences = remember { PlayerPreferences(context) }
    
    val currentSliderStyle by playerPreferences.sliderStyle.collectAsState(initial = SliderStyle.DEFAULT)
    val brightnessSwipeGesturesEnabled by playerPreferences.brightnessSwipeGesturesEnabled.collectAsState(initial = true)
    val volumeSwipeGesturesEnabled by playerPreferences.volumeSwipeGesturesEnabled.collectAsState(initial = true)
    val showFullscreenTitle by playerPreferences.showFullscreenTitle.collectAsState(initial = false)
    val adaptivePlayerSizeEnabled by playerPreferences.adaptivePlayerSizeEnabled.collectAsState(initial = true)

    var showStyleSheet by remember { mutableStateOf(false) }

    if (showStyleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStyleSheet = false },
            sheetState = rememberFlowSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.player_appearance_style_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
                
                LazyColumn {
                    items(SliderStyle.values()) { style ->
                        val isSelected = currentSliderStyle == style
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        playerPreferences.setSliderStyle(style)
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(getStyleLabelResInScreen(style)),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                PreviewPlayerSlider(style = style)
                            }
                        }
                        
                        if (style != SliderStyle.values().last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 24.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }


    Scaffold(
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
                        text = stringResource(R.string.player_appearance_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
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
                SectionHeader(text = stringResource(R.string.player_appearance_header))
            }
            
            item {
                SettingsGroup {
                    SettingsItem(
                        icon = painterResource(R.drawable.ic_progress_bar_style),
                        title = stringResource(R.string.player_appearance_style_title),
                        subtitle = stringResource(getStyleLabelResInScreen(currentSliderStyle)),
                        onClick = { showStyleSheet = true }
                    )
                }
            }
            
            item {
                Text(
                    text = stringResource(R.string.player_appearance_style_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(text = stringResource(R.string.player_appearance_gestures_header))
            }

            item {
                SettingsGroup {
                    SettingsToggleItem(
                        icon = painterResource(R.drawable.ic_swipe_gesture),
                        title = stringResource(R.string.player_appearance_brightness_gesture_title),
                        subtitle = stringResource(R.string.player_appearance_brightness_gesture_subtitle),
                        checked = brightnessSwipeGesturesEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                playerPreferences.setBrightnessSwipeGesturesEnabled(enabled)
                            }
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )

                    SettingsToggleItem(
                        icon = painterResource(R.drawable.ic_swipe_gesture),
                        title = stringResource(R.string.player_appearance_volume_gesture_title),
                        subtitle = stringResource(R.string.player_appearance_volume_gesture_subtitle),
                        checked = volumeSwipeGesturesEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                playerPreferences.setVolumeSwipeGesturesEnabled(enabled)
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(text = stringResource(R.string.player_appearance_fullscreen_header))
            }

            item {
                SettingsGroup {
                    SettingsToggleItem(
                        icon = painterResource(R.drawable.ic_aspect_ratio),
                        title = stringResource(R.string.player_adaptive_size_title),
                        subtitle = stringResource(R.string.player_adaptive_size_subtitle),
                        checked = adaptivePlayerSizeEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                playerPreferences.setAdaptivePlayerSizeEnabled(enabled)
                            }
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )

                    SettingsToggleItem(
                        icon = painterResource(R.drawable.ic_progress_bar_style),
                        title = stringResource(R.string.player_show_title_title),
                        subtitle = stringResource(R.string.player_show_title_subtitle),
                        checked = showFullscreenTitle,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                playerPreferences.setShowFullscreenTitle(enabled)
                            }
                        }
                    )
                }
            }

            // Mini Player Preferences section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(text = stringResource(R.string.mini_player_header))
            }
            
            item {
                SettingsGroup {
                    val miniPlayerScale by playerPreferences.miniPlayerScale.collectAsState(initial = 0.45f)
                    val miniPlayerShowSkip by playerPreferences.miniPlayerShowSkipControls.collectAsState(initial = false)
                    val miniPlayerShowNextPrev by playerPreferences.miniPlayerShowNextPrevControls.collectAsState(initial = false)
                    
                    var expandedScale by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedScale = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check, // Placeholder icon since no mini player vector exists
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.mini_player_size),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            val scaleLabel = when (miniPlayerScale) {
                                0.35f -> stringResource(R.string.mini_player_small)
                                0.55f -> stringResource(R.string.mini_player_large)
                                else -> stringResource(R.string.mini_player_normal)
                            }
                            Text(
                                text = scaleLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Box {
                            Icon(
                                imageVector = Icons.Outlined.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            DropdownMenu(
                                expanded = expandedScale,
                                onDismissRequest = { expandedScale = false }
                            ) {
                                listOf(
                                    stringResource(R.string.mini_player_small) to 0.35f,
                                    stringResource(R.string.mini_player_normal) to 0.45f,
                                    stringResource(R.string.mini_player_large) to 0.55f
                                ).forEach { (label, scale) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            coroutineScope.launch { playerPreferences.setMiniPlayerScale(scale) }
                                            expandedScale = false
                                        },
                                        trailingIcon = if (miniPlayerScale == scale) ({
                                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        }) else null
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    
                    SettingsToggleItem(
                        icon = painterResource(R.drawable.ic_swipe_gesture),
                        title = stringResource(R.string.skip_button_title),
                        subtitle = stringResource(R.string.skip_button_subtitle),
                        checked = miniPlayerShowSkip,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch { playerPreferences.setMiniPlayerShowSkipControls(enabled) }
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsToggleItem(
                        icon = painterResource(R.drawable.ic_swipe_gesture), 
                        title = stringResource(R.string.player_nav_btn_title),
                        subtitle = stringResource(R.string.player_nav_btn_subtitle),
                        checked = miniPlayerShowNextPrev,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch { playerPreferences.setMiniPlayerShowNextPrevControls(enabled) }
                        }
                    )
                }
            }

        }
    }
}



@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.painter.Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SettingsToggleItem(
    icon: androidx.compose.ui.graphics.painter.Painter,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewPlayerSlider(style: SliderStyle) {
    val progress = 0.4f 
    val duration = 100f 
    val position = duration * progress 
    when (style) {
        SliderStyle.METROLIST -> {
            Slider(
                value = position,
                onValueChange = {},
                valueRange = 0f..duration,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )
        }
        SliderStyle.METROLIST_SLIM -> {
            Slider(
                value = position,
                onValueChange = {},
                valueRange = 0f..duration,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )
        }
        SliderStyle.SQUIGGLY -> {
            SquigglySlider(
                value = position,
                onValueChange = {},
                valueRange = 0f..duration,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    thumbColor = MaterialTheme.colorScheme.primary
                ),
                isPlaying = true
            )
        }
        SliderStyle.SLIM -> {
             Slider(
                value = position,
                onValueChange = {},
                valueRange = 0f..duration,
                thumb = { Spacer(modifier = Modifier.size(0.dp)) }, 
                track = { sliderState ->
                    PlayerSliderTrack(
                        sliderState = sliderState,
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        trackHeight = 4.dp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
            )
        }
        SliderStyle.DEFAULT -> {
            val animatedTrackHeight = 12.dp
            
            Slider(
                value = position,
                onValueChange = {},
                valueRange = 0f..duration,
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .shadow(8.dp, CircleShape)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                },
                track = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(animatedTrackHeight)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                            MaterialTheme.colorScheme.primary
                                        )
                                    )
                                )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )
        }
    }
}


private fun getStyleLabelResInScreen(style: SliderStyle): Int {    return when (style) {
        SliderStyle.DEFAULT -> R.string.style_default
        SliderStyle.METROLIST -> R.string.style_metrolist
        SliderStyle.METROLIST_SLIM -> R.string.style_metrolist_slim
        SliderStyle.SQUIGGLY -> R.string.style_squiggly
        SliderStyle.SLIM -> R.string.style_slim
    }
}
