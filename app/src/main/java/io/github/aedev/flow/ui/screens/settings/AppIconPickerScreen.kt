package io.github.aedev.flow.ui.screens.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import kotlinx.coroutines.launch

private const val ICON_NAMESPACE = "io.github.aedev.flow"

private data class AppIconOption(
    val componentSuffix: String,
    val nameRes: Int,
    val previewBackground: Color,
    val foregroundRes: Int,
    val usesThemeColors: Boolean = false
)

private val ALL_ICONS = listOf(
    AppIconOption(".IconFlowRed", R.string.icon_name_flow_red, Color(0xFF0F0F0F), R.drawable.ic_launcher_foreground),
    AppIconOption(".IconFlowLight", R.string.icon_name_flow_light, Color(0xFFFFFFFF), R.drawable.ic_launcher_foreground),
    AppIconOption(".IconAmoled", R.string.icon_name_amoled, Color(0xFF000000), R.drawable.ic_fg_amoled),
    AppIconOption(".IconMonochrome", R.string.icon_name_monochrome, Color(0xFFFFFFFF), R.drawable.ic_fg_monochrome),
    AppIconOption(".IconGhost", R.string.icon_name_ghost, Color(0xFF121212), R.drawable.ic_fg_ghost),
    AppIconOption(".IconDynamic", R.string.icon_name_dynamic, Color.Unspecified, R.drawable.ic_launcher_dynamic_foreground, usesThemeColors = true),
    AppIconOption(".IconMaterialSky", R.string.icon_name_material_sky, Color(0xFFD7E3FF), R.drawable.ic_launcher_foreground),
    AppIconOption(".IconMaterialMint", R.string.icon_name_material_mint, Color(0xFFC7E8D4), R.drawable.ic_launcher_foreground)
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

private fun switchIcon(context: Context, newSuffix: String) {
    val pm = context.packageManager
    val pkg = context.packageName

    for (icon in ALL_ICONS) {
        val cn = ComponentName(pkg, "$ICON_NAMESPACE${icon.componentSuffix}")
        val want = if (icon.componentSuffix == newSuffix) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        pm.setComponentEnabledSetting(cn, want, PackageManager.DONT_KILL_APP)
    }
}

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
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_item_app_icon),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.app_icon_picker_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 152.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AppIconWarningCard()
            }

            items(ALL_ICONS) { icon ->
                IconOptionCard(
                    option = icon,
                    isSelected = selectedSuffix == icon.componentSuffix,
                    onClick = {
                        if (selectedSuffix != icon.componentSuffix) {
                            switchIcon(context, icon.componentSuffix)
                            selectedSuffix = icon.componentSuffix
                            coroutineScope.launch {
                                preferences.setSelectedAppIcon(icon.componentSuffix)
                            }
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

@Composable
private fun AppIconWarningCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
    ) {
        Text(
            text = stringResource(R.string.app_icon_picker_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun IconOptionCard(
    option: AppIconOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(8.dp)
    val previewBackground = if (option.usesThemeColors) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        option.previewBackground
    }
    val imageTint = if (option.usesThemeColors) {
        ColorFilter.tint(MaterialTheme.colorScheme.onSecondaryContainer)
    } else {
        null
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.92f)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = cardShape
            )
            .clip(cardShape)
            .clickable(onClick = onClick),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.26f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(previewBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(option.foregroundRes),
                        contentDescription = null,
                        colorFilter = imageTint,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(option.nameRes),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
