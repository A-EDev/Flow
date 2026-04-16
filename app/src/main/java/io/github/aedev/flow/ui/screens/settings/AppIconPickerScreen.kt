package io.github.aedev.flow.ui.screens.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import kotlinx.coroutines.launch

private const val ICON_NAMESPACE = "io.github.aedev.flow"

private data class AppIconOption(
    val componentSuffix: String,
    val nameRes: Int,
    val bgColor: Color,
    val fgDrawableRes: Int,
    val labelColor: Color = Color.White,
    /** When true, preview bg + icon tint come from MaterialTheme dynamic colors */
    val isDynamic: Boolean = false
)

private val ALL_ICONS = listOf(
    AppIconOption(".IconFlowRed",    R.string.icon_name_flow_red,   Color(0xFFCD2027), R.drawable.ic_launcher_foreground),
    AppIconOption(".IconAmoled",     R.string.icon_name_amoled,     Color(0xFF000000), R.drawable.ic_fg_amoled),
    AppIconOption(".IconMonochrome", R.string.icon_name_monochrome, Color(0xFFFFFFFF), R.drawable.ic_fg_monochrome, labelColor = Color(0xFF1C1B1F)),
    AppIconOption(".IconGhost",      R.string.icon_name_ghost,      Color(0xFF121212), R.drawable.ic_fg_ghost),
    AppIconOption(".IconDynamic",    R.string.icon_name_dynamic,    Color.Unspecified, R.drawable.ic_notification_logo, isDynamic = true)
)

private fun getActiveIconSuffix(context: Context): String {
    val pm = context.packageManager
    val pkg = context.packageName
    for (icon in ALL_ICONS) {
        val cn = ComponentName(pkg, "$ICON_NAMESPACE${icon.componentSuffix}")
        val state = pm.getComponentEnabledSetting(cn)
        if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            return icon.componentSuffix
        }
    }
    return ALL_ICONS.first().componentSuffix
}

// ─────────────────────────────────────────────────────────────────
// Icon switch logic
// ─────────────────────────────────────────────────────────────────

private fun switchIcon(context: Context, newSuffix: String) {
    val pm = context.packageManager
    val pkg = context.packageName

    for (icon in ALL_ICONS) {
        val cn = ComponentName(pkg, "$ICON_NAMESPACE${icon.componentSuffix}")
        val want = if (icon.componentSuffix == newSuffix)
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        pm.setComponentEnabledSetting(cn, want, PackageManager.DONT_KILL_APP)
    }
}

// ─────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconPickerScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferences = remember { PlayerPreferences(context) }
    var selectedSuffix by remember { mutableStateOf(getActiveIconSuffix(context)) }

    LaunchedEffect(Unit) {
        preferences.setSelectedAppIcon(selectedSuffix)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_item_app_icon), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.settings_item_app_icon_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.app_icon_picker_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(ALL_ICONS) { icon ->
                    IconOptionCard(
                        option = icon,
                        isSelected = selectedSuffix == icon.componentSuffix,
                        onClick = {
                            if (selectedSuffix != icon.componentSuffix) {
                                switchIcon(context, icon.componentSuffix)
                                selectedSuffix = icon.componentSuffix
                                coroutineScope.launch { preferences.setSelectedAppIcon(icon.componentSuffix) }
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.app_icon_apply_toast),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Single icon card
// ─────────────────────────────────────────────────────────────────

@Composable
private fun IconOptionCard(
    option: AppIconOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                val previewBg = if (option.isDynamic)
                    MaterialTheme.colorScheme.secondaryContainer
                else
                    option.bgColor
                val imageTint = if (option.isDynamic)
                    ColorFilter.tint(MaterialTheme.colorScheme.onSecondaryContainer)
                else
                    null

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(previewBg),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(option.fgDrawableRes),
                        contentDescription = null,
                        colorFilter = imageTint,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = stringResource(option.nameRes),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
