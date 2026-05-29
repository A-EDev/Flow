package io.github.aedev.flow.ui.screens.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.DEFAULT_FULLSCREEN_SEEKBAR_PADDING_DP
import io.github.aedev.flow.data.local.FullscreenSeekbarPaddingMode
import io.github.aedev.flow.data.local.MAX_FULLSCREEN_SEEKBAR_PADDING_DP
import io.github.aedev.flow.data.local.MusicPlayerBackgroundStyle
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SliderStyle
import io.github.aedev.flow.ui.screens.music.player.components.PlayerSliderTrack
import io.github.aedev.flow.ui.screens.music.player.components.SquigglySlider
import io.github.aedev.flow.ui.components.rememberFlowSheetState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    val currentMusicPlayerBackgroundStyle by playerPreferences.musicPlayerBackgroundStyle.collectAsState(
        initial = MusicPlayerBackgroundStyle.BLUR_GRADIENT
    )
    val brightnessSwipeGesturesEnabled by playerPreferences.brightnessSwipeGesturesEnabled.collectAsState(initial = true)
    val rememberBrightnessEnabled by playerPreferences.rememberBrightnessEnabled.collectAsState(initial = false)
    val volumeSwipeGesturesEnabled by playerPreferences.volumeSwipeGesturesEnabled.collectAsState(initial = true)
    val allowVolumeBoost by playerPreferences.allowVolumeBoost.collectAsState(initial = false)
    val showFullscreenTitle by playerPreferences.showFullscreenTitle.collectAsState(initial = false)
    val adaptivePlayerSizeEnabled by playerPreferences.adaptivePlayerSizeEnabled.collectAsState(initial = true)
    val fullscreenSeekbarPaddingMode by playerPreferences.fullscreenSeekbarPaddingMode.collectAsState(
        initial = FullscreenSeekbarPaddingMode.DEFAULT
    )
    val fullscreenSeekbarCustomPaddingDp by playerPreferences.fullscreenSeekbarCustomPaddingDp.collectAsState(
        initial = DEFAULT_FULLSCREEN_SEEKBAR_PADDING_DP
    )
    val fullscreenSeekbarPaddingDp = when (fullscreenSeekbarPaddingMode) {
        FullscreenSeekbarPaddingMode.FULL_WIDTH -> 0
        FullscreenSeekbarPaddingMode.DEFAULT -> DEFAULT_FULLSCREEN_SEEKBAR_PADDING_DP
        FullscreenSeekbarPaddingMode.CUSTOM -> fullscreenSeekbarCustomPaddingDp
    }

    var showStyleSheet by remember { mutableStateOf(false) }
    var showBackgroundStyleSheet by remember { mutableStateOf(false) }

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

    if (showBackgroundStyleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBackgroundStyleSheet = false },
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
                    text = stringResource(R.string.player_background_style_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )

                LazyColumn {
                    items(MusicPlayerBackgroundStyle.values()) { style ->
                        val isSelected = currentMusicPlayerBackgroundStyle == style

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        playerPreferences.setMusicPlayerBackgroundStyle(style)
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
                                    text = stringResource(getBackgroundStyleLabelResInScreen(style)),
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

                            Spacer(modifier = Modifier.height(10.dp))
                            PreviewPlayerBackground(style = style)
                        }

                        if (style != MusicPlayerBackgroundStyle.values().last()) {
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
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = painterResource(R.drawable.ic_music_note),
                        title = stringResource(R.string.player_background_style_title),
                        subtitle = stringResource(getBackgroundStyleLabelResInScreen(currentMusicPlayerBackgroundStyle)),
                        onClick = { showBackgroundStyleSheet = true }
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
                        title = stringResource(R.string.player_appearance_remember_brightness_title),
                        subtitle = stringResource(R.string.player_appearance_remember_brightness_subtitle),
                        checked = rememberBrightnessEnabled,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                playerPreferences.setRememberBrightnessEnabled(enabled)
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

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )

                    SettingsToggleItem(
                        icon = painterResource(R.drawable.ic_swipe_gesture),
                        title = stringResource(R.string.player_appearance_volume_boost_title),
                        subtitle = stringResource(R.string.player_appearance_volume_boost_subtitle),
                        checked = allowVolumeBoost,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                playerPreferences.setAllowVolumeBoost(enabled)
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

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )

                    FullscreenSeekbarPaddingItem(
                        mode = fullscreenSeekbarPaddingMode,
                        customPaddingDp = fullscreenSeekbarCustomPaddingDp,
                        effectivePaddingDp = fullscreenSeekbarPaddingDp,
                        onModeChange = { mode ->
                            coroutineScope.launch {
                                playerPreferences.setFullscreenSeekbarPaddingMode(mode)
                            }
                        },
                        onCustomPaddingChange = { paddingDp ->
                            coroutineScope.launch {
                                playerPreferences.setFullscreenSeekbarCustomPaddingDp(paddingDp)
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

@Composable
private fun FullscreenSeekbarPaddingItem(
    mode: FullscreenSeekbarPaddingMode,
    customPaddingDp: Int,
    effectivePaddingDp: Int,
    onModeChange: (FullscreenSeekbarPaddingMode) -> Unit,
    onCustomPaddingChange: (Int) -> Unit
) {
    val animatedPreviewPadding by animateDpAsState(
        targetValue = effectivePaddingDp.dp,
        animationSpec = spring(),
        label = "fullscreenSeekbarPreviewPadding"
    )
    val options = listOf(
        FullscreenSeekbarPaddingMode.FULL_WIDTH,
        FullscreenSeekbarPaddingMode.DEFAULT,
        FullscreenSeekbarPaddingMode.CUSTOM
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_progress_bar_style),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.player_fullscreen_seekbar_width_title),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(
                    R.string.player_fullscreen_seekbar_width_subtitle,
                    effectivePaddingDp
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))
            FullscreenSeekbarPaddingPreview(horizontalPadding = animatedPreviewPadding)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { option ->
                    val selected = mode == option
                    Surface(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onModeChange(option) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(getFullscreenSeekbarPaddingModeLabelRes(option)),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )
                        }
                    }
                }
            }

            if (mode == FullscreenSeekbarPaddingMode.CUSTOM) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = customPaddingDp.toFloat(),
                        onValueChange = { value ->
                            val snapped = ((value / 4f).roundToInt() * 4)
                                .coerceIn(0, MAX_FULLSCREEN_SEEKBAR_PADDING_DP)
                            onCustomPaddingChange(snapped)
                        },
                        valueRange = 0f..MAX_FULLSCREEN_SEEKBAR_PADDING_DP.toFloat(),
                        steps = (MAX_FULLSCREEN_SEEKBAR_PADDING_DP / 4) - 1,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.player_fullscreen_seekbar_width_value, customPaddingDp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(56.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun FullscreenSeekbarPaddingPreview(horizontalPadding: androidx.compose.ui.unit.Dp) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val videoSurfaceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(videoSurfaceColor)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(trackColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.36f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(primaryColor)
            )
        }
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


@Composable
fun PreviewPlayerBackground(style: MusicPlayerBackgroundStyle) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val brush = when (style) {
        MusicPlayerBackgroundStyle.BLUR_GRADIENT -> Brush.linearGradient(
            listOf(primary.copy(alpha = 0.80f), secondary.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.90f))
        )
        MusicPlayerBackgroundStyle.BLUR -> Brush.linearGradient(
            listOf(surface.copy(alpha = 0.85f), primary.copy(alpha = 0.45f), surface.copy(alpha = 0.85f))
        )
        MusicPlayerBackgroundStyle.GRADIENT -> Brush.linearGradient(
            listOf(primary.copy(alpha = 0.95f), secondary.copy(alpha = 0.75f), Color.Black.copy(alpha = 0.92f))
        )
        MusicPlayerBackgroundStyle.DEFAULT -> Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(brush)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 14.dp)
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.82f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
                .width(112.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.72f))
        )
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

private fun getBackgroundStyleLabelResInScreen(style: MusicPlayerBackgroundStyle): Int {
    return when (style) {
        MusicPlayerBackgroundStyle.BLUR_GRADIENT -> R.string.player_background_style_blur_gradient
        MusicPlayerBackgroundStyle.BLUR -> R.string.player_background_style_blur
        MusicPlayerBackgroundStyle.GRADIENT -> R.string.player_background_style_gradient
        MusicPlayerBackgroundStyle.DEFAULT -> R.string.player_background_style_default
    }
}

private fun getFullscreenSeekbarPaddingModeLabelRes(mode: FullscreenSeekbarPaddingMode): Int {
    return when (mode) {
        FullscreenSeekbarPaddingMode.FULL_WIDTH -> R.string.player_fullscreen_seekbar_width_full
        FullscreenSeekbarPaddingMode.DEFAULT -> R.string.player_fullscreen_seekbar_width_default
        FullscreenSeekbarPaddingMode.CUSTOM -> R.string.player_fullscreen_seekbar_width_custom
    }
}
