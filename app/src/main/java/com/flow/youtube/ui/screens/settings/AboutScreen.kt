package com.flow.youtube.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.youtube.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val IconReddit: ImageVector by lazy {
    ImageVector.Builder(
        name = "Reddit",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(12f, 0f)
            curveTo(5.373f, 0f, 0f, 5.373f, 0f, 12f)
            curveToRelative(0f, 3.314f, 1.343f, 6.314f, 3.515f, 8.485f)
            lineToRelative(-2.286f, 2.286f)
            curveTo(0.775f, 23.225f, 1.097f, 24f, 1.738f, 24f)
            lineTo(12f, 24f)
            curveToRelative(6.627f, 0f, 12f, -5.373f, 12f, -12f)
            reflectiveCurveTo(18.627f, 0f, 12f, 0f); close()
            moveTo(16.388f, 3.199f)
            curveToRelative(1.104f, 0f, 1.999f, 0.895f, 1.999f, 1.999f)
            curveToRelative(0f, 1.105f, -0.895f, 2f, -1.999f, 2f)
            curveToRelative(-0.946f, 0f, -1.739f, -0.657f, -1.947f, -1.539f)
            verticalLineToRelative(0.002f)
            curveToRelative(-1.147f, 0.162f, -2.032f, 1.15f, -2.032f, 2.341f)
            verticalLineToRelative(0.007f)
            curveToRelative(1.776f, 0.067f, 3.4f, 0.567f, 4.686f, 1.363f)
            curveToRelative(0.473f, -0.363f, 1.064f, -0.58f, 1.707f, -0.58f)
            curveToRelative(1.547f, 0f, 2.802f, 1.254f, 2.802f, 2.802f)
            curveToRelative(0f, 1.117f, -0.655f, 2.081f, -1.601f, 2.531f)
            curveToRelative(-0.088f, 3.256f, -3.637f, 5.876f, -7.997f, 5.876f)
            curveToRelative(-4.361f, 0f, -7.905f, -2.617f, -7.998f, -5.87f)
            curveToRelative(-0.954f, -0.447f, -1.614f, -1.415f, -1.614f, -2.538f)
            curveToRelative(0f, -1.548f, 1.255f, -2.802f, 2.803f, -2.802f)
            curveToRelative(0.645f, 0f, 1.239f, 0.218f, 1.712f, 0.585f)
            curveToRelative(1.275f, -0.79f, 2.881f, -1.291f, 4.64f, -1.365f)
            verticalLineToRelative(-0.01f)
            curveToRelative(0f, -1.663f, 1.263f, -3.034f, 2.88f, -3.207f)
            curveToRelative(0.188f, -0.911f, 0.993f, -1.595f, 1.959f, -1.595f); close()
            moveTo(8.303f, 11.575f)
            curveToRelative(-0.784f, 0f, -1.459f, 0.78f, -1.506f, 1.797f)
            curveToRelative(-0.047f, 1.016f, 0.64f, 1.429f, 1.426f, 1.429f)
            curveToRelative(0.786f, 0f, 1.371f, -0.369f, 1.418f, -1.385f)
            curveToRelative(0.047f, -1.017f, -0.553f, -1.841f, -1.338f, -1.841f); close()
            moveTo(15.709f, 11.575f)
            curveToRelative(-0.786f, 0f, -1.385f, 0.824f, -1.338f, 1.841f)
            curveToRelative(0.047f, 1.017f, 0.634f, 1.385f, 1.418f, 1.385f)
            curveToRelative(0.785f, 0f, 1.473f, -0.413f, 1.426f, -1.429f)
            curveToRelative(-0.046f, -1.017f, -0.721f, -1.797f, -1.506f, -1.797f); close()
            moveTo(12.006f, 15.588f)
            curveToRelative(-0.974f, 0f, -1.907f, 0.048f, -2.77f, 0.135f)
            curveToRelative(-0.147f, 0.015f, -0.241f, 0.168f, -0.183f, 0.305f)
            curveToRelative(0.483f, 1.154f, 1.622f, 1.964f, 2.953f, 1.964f)
            curveToRelative(1.33f, 0f, 2.47f, -0.81f, 2.953f, -1.964f)
            curveToRelative(0.057f, -0.137f, -0.037f, -0.29f, -0.184f, -0.305f)
            curveToRelative(-0.863f, -0.087f, -1.795f, -0.135f, -2.769f, -0.135f); close()
        }
    }.build()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDonations: () -> Unit
) {
    val context = LocalContext.current
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showDeviceInfoDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    // version info
    val packageInfo = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0) }
        catch (e: Exception) { null }
    }
    val versionName = packageInfo?.versionName ?: "Unknown"
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo?.longVersionCode?.toString() ?: "0"
    } else {
        @Suppress("DEPRECATION")
        packageInfo?.versionCode?.toString() ?: "0"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                windowInsets = WindowInsets(0)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_notification_logo),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 3.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "v$versionName ($versionCode)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { HorizontalDivider() }

            item { AboutSectionLabel(stringResource(R.string.section_app)) }
            item {
                AboutRow(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.about_changelog),
                    subtitle = stringResource(R.string.whats_new_in_flow),
                    onClick = { showChangelogDialog = true }
                )
            }
            item { AboutRowDivider() }
            item {
                AboutRow(
                    icon = Icons.Outlined.Favorite,
                    title = stringResource(R.string.donate_item_title),
                    subtitle = stringResource(R.string.support_dev_subtitle),
                    onClick = onNavigateToDonations
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }

            item { AboutSectionLabel(stringResource(R.string.section_contact)) }
            item {
                AboutRow(
                    icon = Icons.Outlined.Public,
                    title = stringResource(R.string.about_website),
                    subtitle = "flow.aedev.me",
                    onClick = { openUrl(context, "https://flow.aedev.me") }
                )
            }
            item { AboutRowDivider() }
            item {
                AboutRowWithPainter(
                    iconPainter = painterResource(id = R.drawable.ic_github),
                    title = stringResource(R.string.github_label),
                    subtitle = stringResource(R.string.github_subtitle),
                    onClick = { openUrl(context, "https://github.com/A-EDev/flow") }
                )
            }
            item { AboutRowDivider() }
            item {
                AboutRowWithVector(
                    iconVector = IconReddit,
                    title = "Reddit",
                    subtitle = "r/Flow_Official",
                    onClick = { openUrl(context, "https://www.reddit.com/r/Flow_Official/") }
                )
            }
            item { AboutRowDivider() }
            item {
                AboutRow(
                    icon = Icons.Outlined.Person,
                    title = stringResource(R.string.about_creator),
                    subtitle = "A-EDev",
                    onClick = { openUrl(context, "https://github.com/A-EDev") }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }

            item { AboutSectionLabel(stringResource(R.string.section_legal)) }
            item {
                AboutRow(
                    icon = Icons.Outlined.Description,
                    title = stringResource(R.string.about_license),
                    subtitle = "GNU GPL v3",
                    onClick = { showLicenseDialog = true }
                )
            }
            item { AboutRowDivider() }
            item {
                AboutRow(
                    icon = Icons.Outlined.Extension,
                    title = stringResource(R.string.newpipe_extractor_title),
                    subtitle = stringResource(R.string.newpipe_extractor_subtitle),
                    onClick = { openUrl(context, "https://github.com/TeamNewPipe/NewPipeExtractor") }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(top = 8.dp)) }

            item { AboutSectionLabel(stringResource(R.string.section_device)) }
            item {
                AboutRow(
                    icon = Icons.Outlined.Smartphone,
                    title = stringResource(R.string.about_device_info),
                    subtitle = "${Build.MANUFACTURER} ${Build.MODEL}",
                    onClick = { showDeviceInfoDialog = true }
                )
            }
        }
    }

    if (showLicenseDialog) LicenseDialog(onDismiss = { showLicenseDialog = false })
    if (showDeviceInfoDialog) DeviceInfoDialog(onDismiss = { showDeviceInfoDialog = false })
    if (showChangelogDialog) ChangelogDialog(onDismiss = { showChangelogDialog = false })
}


@Composable
private fun AboutSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun AboutRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutRowWithPainter(
    iconPainter: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = iconPainter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutRowWithVector(
    iconVector: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = iconVector,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 62.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}


@Composable
fun LicenseDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var licenseText by remember { mutableStateOf(context.getString(R.string.loading_ellipsis)) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                context.assets.open("license.txt").bufferedReader().use {
                    licenseText = it.readText()
                }
            } catch (e: Exception) {
                licenseText = context.getString(R.string.error_license_load)
                e.printStackTrace()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gnu_license_full_title)) },
        text = {
            Box(Modifier.heightIn(max = 400.dp)) {
                LazyColumn {
                    item {
                        Text(
                            text = licenseText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

@Composable
fun DeviceInfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    val deviceInfo = remember {
        buildString {
            append(context.getString(R.string.manufacturer_label, Build.MANUFACTURER) + "\n")
            append(context.getString(R.string.model_label, Build.MODEL) + "\n")
            append(context.getString(R.string.board_label, Build.BOARD) + "\n")
            append(context.getString(R.string.arch_label, Build.SUPPORTED_ABIS.joinToString(", ")) + "\n")
            append(context.getString(R.string.android_sdk_label, Build.VERSION.SDK_INT.toString()) + "\n")
            append(context.getString(R.string.os_label, Build.VERSION.RELEASE) + "\n")
            append(context.getString(R.string.density_label, android.content.res.Resources.getSystem().displayMetrics.density.toString()) + "\n")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_device_info)) },
        text = {
            Text(
                text = deviceInfo,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_ok)) }
        },
        dismissButton = {
            TextButton(onClick = {
                clipboardManager.setText(AnnotatedString(deviceInfo))
            }) { Text(stringResource(R.string.btn_copy)) }
        }
    )
}

@Composable
fun ChangelogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var changelogText by remember { mutableStateOf(context.getString(R.string.loading_ellipsis)) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val assetManager = context.assets
                val files = assetManager.list("changelog") ?: emptyArray()
                val latestFile = files.filter { it.endsWith(".txt") }
                    .sortedWith(compareByDescending { it })
                    .firstOrNull()

                if (latestFile != null) {
                    assetManager.open("changelog/$latestFile").bufferedReader().use {
                        changelogText = it.readText()
                    }
                } else {
                    changelogText = context.getString(R.string.no_changelog_found_message)
                }
            } catch (e: Exception) {
                changelogText = context.getString(R.string.error_changelog_load)
                e.printStackTrace()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_changelog)) },
        text = {
            Box(Modifier.heightIn(max = 400.dp)) {
                LazyColumn {
                    item {
                        Text(
                            text = changelogText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// Compat shim — kept for any existing call sites
@Composable
fun CustomIconSettingsItem(
    iconPainter: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) = AboutRowWithPainter(iconPainter = iconPainter, title = title, subtitle = subtitle, onClick = onClick)
