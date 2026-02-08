@file:OptIn(ExperimentalMaterial3Api::class)
package com.flow.youtube.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.youtube.ui.theme.*
import com.flow.youtube.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ============================================================================
// Data Models
// ============================================================================

private data class ThemeInfo(
    val mode: ThemeMode,
    @androidx.annotation.StringRes val displayNameRes: Int,
    @androidx.annotation.StringRes val subtitleRes: Int,
    val category: ThemeCategory,
    val primaryColor: Color,
    val backgroundColor: Color,
    val surfaceColor: Color,
    val onSurfaceColor: Color,
    val accentColor: Color = Color.Unspecified,
    val surfaceVariantColor: Color = Color.Unspecified
)

private enum class ThemeCategory(@androidx.annotation.StringRes val labelRes: Int, val icon: @Composable () -> Unit) {
    LIGHT(com.flow.youtube.R.string.appearance_category_light, { Icon(Icons.Outlined.LightMode, null, modifier = Modifier.size(18.dp)) }),
    DARK(com.flow.youtube.R.string.appearance_category_dark, { Icon(Icons.Outlined.DarkMode, null, modifier = Modifier.size(18.dp)) }),
    SPECIAL(com.flow.youtube.R.string.appearance_category_special, { Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(18.dp)) })
}

private val ALL_THEMES = listOf(
    // Light Themes
    ThemeInfo(
        ThemeMode.LIGHT, com.flow.youtube.R.string.theme_name_pure_light, com.flow.youtube.R.string.theme_desc_pure_light,
        ThemeCategory.LIGHT,
        primaryColor = YouTubeRed,
        backgroundColor = White,
        surfaceColor = LightSurface,
        onSurfaceColor = Color(0xFF0F0F0F),
        surfaceVariantColor = LightSurfaceVariant
    ),
    ThemeInfo(
        ThemeMode.MINT_LIGHT, com.flow.youtube.R.string.theme_name_mint_fresh, com.flow.youtube.R.string.theme_desc_mint_fresh,
        ThemeCategory.LIGHT,
        primaryColor = MintLightThemeColors.Primary,
        backgroundColor = MintLightThemeColors.Background,
        surfaceColor = MintLightThemeColors.Surface,
        onSurfaceColor = MintLightThemeColors.Text,
        accentColor = MintLightThemeColors.Secondary,
        surfaceVariantColor = MintLightThemeColors.Border
    ),
    ThemeInfo(
        ThemeMode.ROSE_LIGHT, com.flow.youtube.R.string.theme_name_rose_petal, com.flow.youtube.R.string.theme_desc_rose_petal,
        ThemeCategory.LIGHT,
        primaryColor = RoseLightThemeColors.Primary,
        backgroundColor = RoseLightThemeColors.Background,
        surfaceColor = RoseLightThemeColors.Surface,
        onSurfaceColor = RoseLightThemeColors.Text,
        accentColor = RoseLightThemeColors.Secondary,
        surfaceVariantColor = RoseLightThemeColors.Border
    ),
    ThemeInfo(
        ThemeMode.SKY_LIGHT, com.flow.youtube.R.string.theme_name_sky_blue, com.flow.youtube.R.string.theme_desc_sky_blue,
        ThemeCategory.LIGHT,
        primaryColor = SkyLightThemeColors.Primary,
        backgroundColor = SkyLightThemeColors.Background,
        surfaceColor = SkyLightThemeColors.Surface,
        onSurfaceColor = SkyLightThemeColors.Text,
        accentColor = SkyLightThemeColors.Secondary,
        surfaceVariantColor = SkyLightThemeColors.Border
    ),
    ThemeInfo(
        ThemeMode.CREAM_LIGHT, com.flow.youtube.R.string.theme_name_cream_paper, com.flow.youtube.R.string.theme_desc_cream_paper,
        ThemeCategory.LIGHT,
        primaryColor = CreamLightThemeColors.Primary,
        backgroundColor = CreamLightThemeColors.Background,
        surfaceColor = CreamLightThemeColors.Surface,
        onSurfaceColor = CreamLightThemeColors.Text,
        accentColor = CreamLightThemeColors.Secondary,
        surfaceVariantColor = CreamLightThemeColors.Border
    ),

    // Dark Themes
    ThemeInfo(
        ThemeMode.DARK, com.flow.youtube.R.string.theme_name_classic_dark, com.flow.youtube.R.string.theme_desc_classic_dark,
        ThemeCategory.DARK,
        primaryColor = YouTubeRed,
        backgroundColor = DarkBackground,
        surfaceColor = DarkSurface,
        onSurfaceColor = TextPrimary,
        surfaceVariantColor = DarkSurfaceVariant
    ),
    ThemeInfo(
        ThemeMode.OLED, com.flow.youtube.R.string.theme_name_true_black, com.flow.youtube.R.string.theme_desc_true_black,
        ThemeCategory.DARK,
        primaryColor = YouTubeRed,
        backgroundColor = Black,
        surfaceColor = OLEDThemeColors.Surface,
        onSurfaceColor = TextPrimary,
        surfaceVariantColor = OLEDThemeColors.Border
    ),
    ThemeInfo(
        ThemeMode.MIDNIGHT_BLACK, com.flow.youtube.R.string.theme_name_midnight, com.flow.youtube.R.string.theme_desc_midnight,
        ThemeCategory.DARK,
        primaryColor = MidnightBlackThemeColors.Primary,
        backgroundColor = MidnightBlackThemeColors.Background,
        surfaceColor = MidnightBlackThemeColors.Surface,
        onSurfaceColor = MidnightBlackThemeColors.Text,
        accentColor = MidnightBlackThemeColors.Secondary,
        surfaceVariantColor = MidnightBlackThemeColors.Border
    ),
    ThemeInfo(
        ThemeMode.OCEAN_BLUE, com.flow.youtube.R.string.theme_name_deep_ocean, com.flow.youtube.R.string.theme_desc_deep_ocean,
        ThemeCategory.DARK,
        primaryColor = OceanBlueThemeColors.Primary,
        backgroundColor = OceanBlueThemeColors.Background,
        surfaceColor = OceanBlueThemeColors.Surface,
        onSurfaceColor = OceanBlueThemeColors.Text,
        accentColor = OceanBlueThemeColors.Secondary,
        surfaceVariantColor = OceanBlueThemeColors.Border
    ),
    ThemeInfo(
        ThemeMode.FOREST_GREEN, com.flow.youtube.R.string.theme_name_forest, com.flow.youtube.R.string.theme_desc_forest,
        ThemeCategory.DARK,
        primaryColor = ForestGreenThemeColors.Primary,
        backgroundColor = ForestGreenThemeColors.Background,
        surfaceColor = ForestGreenThemeColors.Surface,
        onSurfaceColor = ForestGreenThemeColors.Text,
        accentColor = ForestGreenThemeColors.Secondary,
        surfaceVariantColor = ForestGreenThemeColors.Border
    ),
    ThemeInfo(
        ThemeMode.LAVENDER_MIST, com.flow.youtube.R.string.theme_name_lavender, com.flow.youtube.R.string.theme_desc_lavender,
        ThemeCategory.DARK,
        primaryColor = Color(0xFFB39DDB),
        backgroundColor = Color(0xFF120F1A),
        surfaceColor = Color(0xFF1F1A2E),
        onSurfaceColor = Color(0xFFEDE7F6),
        accentColor = Color(0xFF9575CD),
        surfaceVariantColor = Color(0xFF2A2235)
    ),
    ThemeInfo(
        ThemeMode.SUNSET_ORANGE, com.flow.youtube.R.string.theme_name_sunset, com.flow.youtube.R.string.theme_desc_sunset,
        ThemeCategory.DARK,
        primaryColor = SunsetOrangeThemeColors.Primary,
        backgroundColor = SunsetOrangeThemeColors.Background,
        surfaceColor = SunsetOrangeThemeColors.Surface,
        onSurfaceColor = SunsetOrangeThemeColors.Text,
        accentColor = SunsetOrangeThemeColors.Secondary,
        surfaceVariantColor = SunsetOrangeThemeColors.Border
    ),
    ThemeInfo(
        ThemeMode.PURPLE_NEBULA, com.flow.youtube.R.string.theme_name_nebula, com.flow.youtube.R.string.theme_desc_nebula,
        ThemeCategory.DARK,
        primaryColor = PurpleNebulaThemeColors.Primary,
        backgroundColor = PurpleNebulaThemeColors.Background,
        surfaceColor = PurpleNebulaThemeColors.Surface,
        onSurfaceColor = PurpleNebulaThemeColors.Text,
        accentColor = PurpleNebulaThemeColors.Secondary,
        surfaceVariantColor = PurpleNebulaThemeColors.Border
    ),
    ThemeInfo(
        ThemeMode.ROSE_GOLD, com.flow.youtube.R.string.theme_name_rose_gold, com.flow.youtube.R.string.theme_desc_rose_gold,
        ThemeCategory.DARK,
        primaryColor = RoseGoldThemeColors.Primary,
        backgroundColor = RoseGoldThemeColors.Background,
        surfaceColor = RoseGoldThemeColors.Surface,
        onSurfaceColor = RoseGoldThemeColors.Text,
        accentColor = RoseGoldThemeColors.Secondary,
        surfaceVariantColor = RoseGoldThemeColors.Border
    ),
    ThemeInfo(
        ThemeMode.ARCTIC_ICE, com.flow.youtube.R.string.theme_name_arctic, com.flow.youtube.R.string.theme_desc_arctic,
        ThemeCategory.DARK,
        primaryColor = ArcticIceThemeColors.Primary,
        backgroundColor = ArcticIceThemeColors.Background,
        surfaceColor = ArcticIceThemeColors.Surface,
        onSurfaceColor = ArcticIceThemeColors.Text,
        accentColor = ArcticIceThemeColors.Secondary,
        surfaceVariantColor = ArcticIceThemeColors.Border
    ),
    ThemeInfo(
        ThemeMode.MINTY_FRESH, com.flow.youtube.R.string.theme_name_mint_night, com.flow.youtube.R.string.theme_desc_mint_night,
        ThemeCategory.DARK,
        primaryColor = Color(0xFF80CBC4),
        backgroundColor = Color(0xFF0F1A18),
        surfaceColor = Color(0xFF1A2E2B),
        onSurfaceColor = Color(0xFFE0F2F1),
        accentColor = Color(0xFF4DB6AC),
        surfaceVariantColor = Color(0xFF1E302D)
    ),

    // Special Themes
    ThemeInfo(
        ThemeMode.CRIMSON_RED, com.flow.youtube.R.string.theme_name_crimson, com.flow.youtube.R.string.theme_desc_crimson,
        ThemeCategory.SPECIAL,
        primaryColor = CrimsonRedThemeColors.Primary,
        backgroundColor = CrimsonRedThemeColors.Background,
        surfaceColor = CrimsonRedThemeColors.Surface,
        onSurfaceColor = CrimsonRedThemeColors.Text,
        accentColor = CrimsonRedThemeColors.Secondary,
        surfaceVariantColor = CrimsonRedThemeColors.Border
    ),
    ThemeInfo(
        ThemeMode.COSMIC_VOID, com.flow.youtube.R.string.theme_name_cosmic_void, com.flow.youtube.R.string.theme_desc_cosmic_void,
        ThemeCategory.SPECIAL,
        primaryColor = Color(0xFF7C4DFF),
        backgroundColor = Color(0xFF050505),
        surfaceColor = Color(0xFF121212),
        onSurfaceColor = Color(0xFFE0E0E0),
        accentColor = Color(0xFF651FFF),
        surfaceVariantColor = Color(0xFF1A1225)
    ),
    ThemeInfo(
        ThemeMode.SOLAR_FLARE, com.flow.youtube.R.string.theme_name_solar_flare, com.flow.youtube.R.string.theme_desc_solar_flare,
        ThemeCategory.SPECIAL,
        primaryColor = Color(0xFFFFD740),
        backgroundColor = Color(0xFF1A1500),
        surfaceColor = Color(0xFF2E2600),
        onSurfaceColor = Color(0xFFFFFDE7),
        accentColor = Color(0xFFFFAB00),
        surfaceVariantColor = Color(0xFF352A10)
    ),
    ThemeInfo(
        ThemeMode.CYBERPUNK, com.flow.youtube.R.string.theme_name_cyberpunk, com.flow.youtube.R.string.theme_desc_cyberpunk,
        ThemeCategory.SPECIAL,
        primaryColor = Color(0xFFFF00FF),
        backgroundColor = Color(0xFF0D001A),
        surfaceColor = Color(0xFF1F0033),
        onSurfaceColor = Color(0xFFE0E0E0),
        accentColor = Color(0xFF00FFFF),
        surfaceVariantColor = Color(0xFF200F35)
    ),
    ThemeInfo(
        ThemeMode.ROYAL_GOLD, com.flow.youtube.R.string.theme_name_royal_gold, com.flow.youtube.R.string.theme_desc_royal_gold,
        ThemeCategory.SPECIAL,
        primaryColor = RoyalGoldThemeColors.Primary,
        backgroundColor = RoyalGoldThemeColors.Background,
        surfaceColor = RoyalGoldThemeColors.Surface,
        onSurfaceColor = RoyalGoldThemeColors.Text,
        accentColor = RoyalGoldThemeColors.Secondary,
        surfaceVariantColor = RoyalGoldThemeColors.Border
    ),
    ThemeInfo(
        ThemeMode.NORDIC_HORIZON, com.flow.youtube.R.string.theme_name_nordic, com.flow.youtube.R.string.theme_desc_nordic,
        ThemeCategory.SPECIAL,
        primaryColor = NordicHorizonThemeColors.Primary,
        backgroundColor = NordicHorizonThemeColors.Background,
        surfaceColor = NordicHorizonThemeColors.Surface,
        onSurfaceColor = NordicHorizonThemeColors.Text,
        accentColor = NordicHorizonThemeColors.Secondary,
        surfaceVariantColor = NordicHorizonThemeColors.Border
    ),
    ThemeInfo(
        ThemeMode.ESPRESSO, com.flow.youtube.R.string.theme_name_espresso, com.flow.youtube.R.string.theme_desc_espresso,
        ThemeCategory.SPECIAL,
        primaryColor = EspressoThemeColors.Primary,
        backgroundColor = EspressoThemeColors.Background,
        surfaceColor = EspressoThemeColors.Surface,
        onSurfaceColor = EspressoThemeColors.Text,
        accentColor = EspressoThemeColors.Secondary,
        surfaceVariantColor = EspressoThemeColors.Border
    ),
    ThemeInfo(
        ThemeMode.GUNMETAL, com.flow.youtube.R.string.theme_name_gunmetal, com.flow.youtube.R.string.theme_desc_gunmetal,
        ThemeCategory.SPECIAL,
        primaryColor = GunmetalThemeColors.Primary,
        backgroundColor = GunmetalThemeColors.Background,
        surfaceColor = GunmetalThemeColors.Surface,
        onSurfaceColor = GunmetalThemeColors.Text,
        accentColor = GunmetalThemeColors.Secondary,
        surfaceVariantColor = GunmetalThemeColors.Border
    )

)

// ============================================================================
// Main Screen
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onNavigateBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedCategory by remember { mutableStateOf<ThemeCategory?>(null) }
    var showAppliedSnackbar by remember { mutableStateOf(false) }
    var lastAppliedTheme by remember { mutableStateOf("") }

    val filteredThemes = remember(selectedCategory) {
        if (selectedCategory == null) ALL_THEMES
        else ALL_THEMES.filter { it.category == selectedCategory }
    }

    val currentThemeInfo = remember(currentTheme) {
        ALL_THEMES.find { it.mode == currentTheme } ?: ALL_THEMES[0]
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(showAppliedSnackbar) {
        if (showAppliedSnackbar) {
            snackbarHostState.showSnackbar(
                message = context.getString(com.flow.youtube.R.string.appearance_applied_toast, lastAppliedTheme),
                duration = SnackbarDuration.Short
            )
            showAppliedSnackbar = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Ambient background glow
        AmbientGlow(currentThemeInfo.primaryColor)

        Scaffold(
            topBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.appearance_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.appearance_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent
        ) { padding ->

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // ── Current Theme Hero ──
                item(span = { GridItemSpan(2) }) {
                    CurrentThemeHero(currentThemeInfo)
                }

                // ── Category Filter Chips ──
                item(span = { GridItemSpan(2) }) {
                    CategoryFilterRow(
                        selectedCategory = selectedCategory,
                        onCategorySelected = { selectedCategory = it }
                    )
                }

                // ── Section Label ──
item(span = { GridItemSpan(2) }) {
                    val countText = if (selectedCategory == null) androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.appearance_themes_count_all, ALL_THEMES.size)
                    else androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.appearance_themes_count_filtered, filteredThemes.size, androidx.compose.ui.res.stringResource(selectedCategory!!.labelRes).lowercase())

                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                // ── Theme Cards Grid ──
                items(
                    items = filteredThemes,
                    key = { it.mode.name }
                ) { themeInfo ->
                    ThemeCard(
                        themeInfo = themeInfo,
                        isSelected = currentTheme == themeInfo.mode,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onThemeChange(themeInfo.mode)
                            lastAppliedTheme = context.getString(themeInfo.displayNameRes)
                            showAppliedSnackbar = true
                        }
                    )
                }

                // ── Bottom Spacing ──
                item(span = { GridItemSpan(2) }) {
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

// ============================================================================
// Ambient Background Glow
// ============================================================================

@Composable
private fun AmbientGlow(accentColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")

    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .blur(80.dp)
    ) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accentColor.copy(alpha = animatedAlpha), Color.Transparent),
                center = Offset(size.width * 0.8f, size.height * 0.1f + animatedOffset),
                radius = size.width * 0.6f
            ),
            center = Offset(size.width * 0.8f, size.height * 0.1f + animatedOffset),
            radius = size.width * 0.6f
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accentColor.copy(alpha = animatedAlpha * 0.5f), Color.Transparent),
                center = Offset(size.width * 0.15f, size.height * 0.85f - animatedOffset * 0.5f),
                radius = size.width * 0.4f
            ),
            center = Offset(size.width * 0.15f, size.height * 0.85f - animatedOffset * 0.5f),
            radius = size.width * 0.4f
        )
    }
}

// ============================================================================
// Current Theme Hero Card
// ============================================================================

@Composable
private fun CurrentThemeHero(themeInfo: ThemeInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            themeInfo.backgroundColor,
                            themeInfo.surfaceColor
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            themeInfo.primaryColor.copy(alpha = 0.3f),
                            themeInfo.primaryColor.copy(alpha = 0.1f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            // Decorative circles
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = themeInfo.primaryColor.copy(alpha = 0.08f),
                    radius = size.width * 0.3f,
                    center = Offset(size.width * 0.85f, size.height * 0.2f)
                )
                drawCircle(
                    color = (if (themeInfo.accentColor != Color.Unspecified) themeInfo.accentColor
                    else themeInfo.primaryColor).copy(alpha = 0.05f),
                    radius = size.width * 0.2f,
                    center = Offset(size.width * 0.1f, size.height * 0.9f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mini device preview
                MiniDevicePreview(
                    themeInfo = themeInfo,
                    modifier = Modifier.size(width = 60.dp, height = 100.dp)
                )

                Spacer(Modifier.width(20.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = themeInfo.primaryColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.appearance_current_theme),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = themeInfo.primaryColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        androidx.compose.ui.res.stringResource(themeInfo.displayNameRes),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = themeInfo.onSurfaceColor
                    )

                    Text(
                        androidx.compose.ui.res.stringResource(themeInfo.subtitleRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = themeInfo.onSurfaceColor.copy(alpha = 0.6f)
                    )

                    Spacer(Modifier.height(12.dp))

                    // Color palette dots
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ColorDot(themeInfo.primaryColor, "Primary")
                        ColorDot(themeInfo.backgroundColor, "BG", border = true)
                        ColorDot(themeInfo.surfaceColor, "Surface")
                        if (themeInfo.accentColor != Color.Unspecified) {
                            ColorDot(themeInfo.accentColor, "Accent")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorDot(color: Color, label: String, border: Boolean = false) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (border) Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                else Modifier
            )
    )
}

// ============================================================================
// Mini Device Preview
// ============================================================================

@Composable
private fun MiniDevicePreview(
    themeInfo: ThemeInfo,
    modifier: Modifier = Modifier
) {
    val svColor = if (themeInfo.surfaceVariantColor != Color.Unspecified)
        themeInfo.surfaceVariantColor else themeInfo.surfaceColor

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cornerRadius = CornerRadius(12f, 12f)

        // Device frame
        drawRoundRect(
            color = themeInfo.onSurfaceColor.copy(alpha = 0.2f),
            size = Size(w, h),
            cornerRadius = cornerRadius,
            style = Stroke(width = 2f)
        )

        // Screen background
        drawRoundRect(
            color = themeInfo.backgroundColor,
            topLeft = Offset(3f, 3f),
            size = Size(w - 6f, h - 6f),
            cornerRadius = CornerRadius(10f, 10f)
        )

        // Status bar
        drawRoundRect(
            color = themeInfo.surfaceColor,
            topLeft = Offset(3f, 3f),
            size = Size(w - 6f, h * 0.12f),
            cornerRadius = CornerRadius(10f, 10f)
        )

        // Top bar accent line
        drawRoundRect(
            color = themeInfo.primaryColor,
            topLeft = Offset(w * 0.1f, h * 0.15f),
            size = Size(w * 0.35f, 3f),
            cornerRadius = CornerRadius(2f, 2f)
        )

        // Content card 1
        drawRoundRect(
            color = themeInfo.surfaceColor,
            topLeft = Offset(w * 0.08f, h * 0.22f),
            size = Size(w * 0.84f, h * 0.18f),
            cornerRadius = CornerRadius(6f, 6f)
        )

        // Thumbnail placeholder inside card
        drawRoundRect(
            color = svColor,
            topLeft = Offset(w * 0.12f, h * 0.25f),
            size = Size(w * 0.25f, h * 0.12f),
            cornerRadius = CornerRadius(4f, 4f)
        )

        // Text lines inside card
        drawRoundRect(
            color = themeInfo.onSurfaceColor.copy(alpha = 0.15f),
            topLeft = Offset(w * 0.42f, h * 0.26f),
            size = Size(w * 0.45f, 3f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = themeInfo.onSurfaceColor.copy(alpha = 0.1f),
            topLeft = Offset(w * 0.42f, h * 0.32f),
            size = Size(w * 0.3f, 3f),
            cornerRadius = CornerRadius(2f, 2f)
        )

        // Content card 2
        drawRoundRect(
            color = themeInfo.surfaceColor,
            topLeft = Offset(w * 0.08f, h * 0.44f),
            size = Size(w * 0.84f, h * 0.18f),
            cornerRadius = CornerRadius(6f, 6f)
        )

        // Thumbnail 2
        drawRoundRect(
            color = svColor,
            topLeft = Offset(w * 0.12f, h * 0.47f),
            size = Size(w * 0.25f, h * 0.12f),
            cornerRadius = CornerRadius(4f, 4f)
        )

        // Text lines 2
        drawRoundRect(
            color = themeInfo.onSurfaceColor.copy(alpha = 0.15f),
            topLeft = Offset(w * 0.42f, h * 0.48f),
            size = Size(w * 0.4f, 3f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = themeInfo.onSurfaceColor.copy(alpha = 0.1f),
            topLeft = Offset(w * 0.42f, h * 0.54f),
            size = Size(w * 0.25f, 3f),
            cornerRadius = CornerRadius(2f, 2f)
        )

        // Bottom nav bar
        drawRoundRect(
            color = themeInfo.surfaceColor,
            topLeft = Offset(3f, h * 0.85f),
            size = Size(w - 6f, h * 0.15f - 3f),
            cornerRadius = CornerRadius(0f, 0f)
        )

        // Nav bar icons
        val navY = h * 0.91f
        val navSpacing = w / 5f
        for (i in 1..4) {
            val dotColor = if (i == 1) themeInfo.primaryColor
            else themeInfo.onSurfaceColor.copy(alpha = 0.2f)
            drawCircle(
                color = dotColor,
                radius = 3f,
                center = Offset(navSpacing * i, navY)
            )
        }

        // FAB
        drawCircle(
            color = themeInfo.primaryColor,
            radius = w * 0.08f,
            center = Offset(w * 0.85f, h * 0.75f)
        )
    }
}

// ============================================================================
// Category Filter Row
// ============================================================================

@Composable
private fun CategoryFilterRow(
    selectedCategory: ThemeCategory?,
    onCategorySelected: (ThemeCategory?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "All" chip
        FilterChip(
            selected = selectedCategory == null,
            onClick = { onCategorySelected(null) },
            label = { Text(androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.appearance_category_all)) },
            leadingIcon = if (selectedCategory == null) {
                { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
            } else {
                { Icon(Icons.Outlined.GridView, null, modifier = Modifier.size(16.dp)) }
            },
            shape = RoundedCornerShape(12.dp)
        )

        ThemeCategory.entries.forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = {
                    onCategorySelected(if (selectedCategory == category) null else category)
                },
                label = { Text(androidx.compose.ui.res.stringResource(category.labelRes)) },
                leadingIcon = if (selectedCategory == category) {
                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                } else {
                    category.icon
                },
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

// ============================================================================
// Theme Card (Grid Item)
// ============================================================================

@Composable
private fun ThemeCard(
    themeInfo: ThemeInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "scale"
    )

    val borderAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(300),
        label = "border"
    )

    val elevation by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 2.dp,
        animationSpec = tween(300),
        label = "elevation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .then(
                if (isSelected) Modifier.border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            themeInfo.primaryColor,
                            (if (themeInfo.accentColor != Color.Unspecified) themeInfo.accentColor
                            else themeInfo.primaryColor).copy(alpha = 0.6f)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) else Modifier
            ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            themeInfo.backgroundColor,
                            themeInfo.surfaceColor
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Mini preview at top
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.4f)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    MiniAppPreview(themeInfo)

                    // Selected badge
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(24.dp)
                                .shadow(4.dp, CircleShape)
                                .background(themeInfo.primaryColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                null,
                                tint = if (themeInfo.category == ThemeCategory.LIGHT)
                                    Color.White else themeInfo.backgroundColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Theme name
                Text(
                    androidx.compose.ui.res.stringResource(themeInfo.displayNameRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = themeInfo.onSurfaceColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Subtitle
                Text(
                    androidx.compose.ui.res.stringResource(themeInfo.subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = themeInfo.onSurfaceColor.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp
                )

                Spacer(Modifier.height(8.dp))

                // Color palette strip
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val colors = buildList {
                        add(themeInfo.primaryColor)
                        add(themeInfo.surfaceColor)
                        add(themeInfo.backgroundColor)
                        if (themeInfo.accentColor != Color.Unspecified) add(themeInfo.accentColor)
                    }
                    colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(color)
                                .then(
                                    if (color == themeInfo.backgroundColor)
                                        Modifier.border(
                                            0.5.dp,
                                            themeInfo.onSurfaceColor.copy(alpha = 0.15f),
                                            RoundedCornerShape(3.dp)
                                        )
                                    else Modifier
                                )
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// Mini App Preview (Inside Theme Card)
// ============================================================================

@Composable
private fun MiniAppPreview(themeInfo: ThemeInfo) {
    val svColor = if (themeInfo.surfaceVariantColor != Color.Unspecified)
        themeInfo.surfaceVariantColor else themeInfo.surfaceColor

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Background
        drawRect(color = themeInfo.backgroundColor)

        // Top bar
        drawRect(
            color = themeInfo.surfaceColor,
            topLeft = Offset.Zero,
            size = Size(w, h * 0.15f)
        )

        // Search bar in top bar
        drawRoundRect(
            color = svColor,
            topLeft = Offset(w * 0.05f, h * 0.04f),
            size = Size(w * 0.7f, h * 0.08f),
            cornerRadius = CornerRadius(8f, 8f)
        )

        // Avatar circle in top bar
        drawCircle(
            color = themeInfo.primaryColor.copy(alpha = 0.4f),
            radius = h * 0.04f,
            center = Offset(w * 0.9f, h * 0.08f)
        )

        // Video thumbnail 1
        drawRoundRect(
            color = svColor,
            topLeft = Offset(w * 0.04f, h * 0.19f),
            size = Size(w * 0.92f, h * 0.3f),
            cornerRadius = CornerRadius(8f, 8f)
        )

        // Play button on thumbnail
        drawCircle(
            color = themeInfo.primaryColor.copy(alpha = 0.8f),
            radius = h * 0.05f,
            center = Offset(w * 0.5f, h * 0.34f)
        )

        // Channel avatar
        drawCircle(
            color = themeInfo.primaryColor.copy(alpha = 0.3f),
            radius = h * 0.03f,
            center = Offset(w * 0.1f, h * 0.55f)
        )

        // Title lines
        drawRoundRect(
            color = themeInfo.onSurfaceColor.copy(alpha = 0.2f),
            topLeft = Offset(w * 0.18f, h * 0.52f),
            size = Size(w * 0.7f, 3f),
            cornerRadius = CornerRadius(2f, 2f)
        )
        drawRoundRect(
            color = themeInfo.onSurfaceColor.copy(alpha = 0.12f),
            topLeft = Offset(w * 0.18f, h * 0.57f),
            size = Size(w * 0.45f, 3f),
            cornerRadius = CornerRadius(2f, 2f)
        )

        // Video thumbnail 2
        drawRoundRect(
            color = svColor,
            topLeft = Offset(w * 0.04f, h * 0.65f),
            size = Size(w * 0.92f, h * 0.2f),
            cornerRadius = CornerRadius(8f, 8f)
        )

        // Bottom nav bar
        drawRect(
            color = themeInfo.surfaceColor,
            topLeft = Offset(0f, h * 0.88f),
            size = Size(w, h * 0.12f)
        )

        // Bottom nav icons
        val navY = h * 0.94f
        val spacing = w / 6f
        for (i in 1..5) {
            val dotColor = if (i == 1) themeInfo.primaryColor
            else themeInfo.onSurfaceColor.copy(alpha = 0.15f)
            drawCircle(
                color = dotColor,
                radius = 3.5f,
                center = Offset(spacing * i, navY)
            )
        }
    }
}