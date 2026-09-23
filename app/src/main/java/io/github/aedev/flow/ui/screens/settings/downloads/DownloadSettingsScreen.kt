package io.github.aedev.flow.ui.screens.settings.downloads

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.DownloadDialogStyle
import io.github.aedev.flow.data.local.VideoCodec
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.settings.choice
import io.github.aedev.flow.ui.components.settings.nav
import io.github.aedev.flow.ui.components.settings.slider
import io.github.aedev.flow.ui.components.settings.switch
import io.github.aedev.flow.ui.components.settings.toggleGroup
import io.github.aedev.flow.ui.components.shared.FlowChoice
import io.github.aedev.flow.ui.components.shared.FlowChoiceDialog
import io.github.aedev.flow.ui.components.shared.FlowToggleOption
import io.github.aedev.flow.ui.screens.settings.index.DownloadsIndex
import io.github.aedev.flow.ui.screens.settings.quality.VideoQualities
import io.github.aedev.flow.ui.screens.settings.quality.codecLabel
import io.github.aedev.flow.ui.screens.settings.quality.videoQualityLabel

private enum class DownloadPicker { QUALITY, CODEC, CACHE }

private const val MAX_THREADS = 8
private val UsageSpacing = 8.dp
private val CacheSizes = listOf(100, 200, 500, 0)

/** Where downloads are saved, how they are made, and the storage access that lists them. */
@Composable
internal fun DownloadSettingsScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
    viewModel: DownloadSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val videoLocation by viewModel.videoLocation.collectAsStateWithLifecycle()
    val musicLocation by viewModel.musicLocation.collectAsStateWithLifecycle()
    val quickQuality by viewModel.quickQuality.collectAsStateWithLifecycle()
    val codec by viewModel.codec.collectAsStateWithLifecycle()
    val menuStyle by viewModel.menuStyle.collectAsStateWithLifecycle()
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val cacheSizeMb by viewModel.cacheSizeMb.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()

    var picker by rememberSaveable { mutableStateOf<DownloadPicker?>(null) }
    var locationTarget by rememberSaveable { mutableStateOf<DownloadTarget?>(null) }
    var access by remember { mutableStateOf(StorageAccess.read(context)) }
    LifecycleResumeEffect(Unit) {
        access = StorageAccess.read(context)
        onPauseOrDispose { }
    }

    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            val target = locationTarget
            if (uri != null && target != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                (treeUriToPath(uri) ?: uri.path)?.takeIf { it.isNotBlank() }?.let { viewModel.setLocation(target, it) }
                locationTarget = null
            }
        }
    val mediaPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { access = StorageAccess.read(context) }
    val legacyWrite = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !context.granted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            legacyWrite.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    val videoPath = videoLocation ?: viewModel.defaultPath(DownloadTarget.VIDEO)
    val musicPath = musicLocation ?: viewModel.defaultPath(DownloadTarget.MUSIC)
    val usage =
        storage?.let {
            stringResource(
                R.string.settings_storage_usage,
                Formatter.formatShortFileSize(context, it.freeBytes),
                Formatter.formatShortFileSize(context, it.totalBytes),
            )
        } ?: stringResource(R.string.unknown)
    val grantedLabel = stringResource(R.string.media_access_granted_subtitle)
    val videoDeniedLabel = stringResource(R.string.media_access_denied_subtitle)
    val audioDeniedLabel = stringResource(R.string.audio_access_denied_subtitle)
    val allFilesLabel =
        stringResource(if (access.allFiles) R.string.files_access_granted_subtitle else R.string.files_access_denied_subtitle)
    val menuStyles =
        listOf(
            FlowToggleOption(DownloadDialogStyle.FULL, stringResource(R.string.download_menu_style_classic)),
            FlowToggleOption(DownloadDialogStyle.COMPACT, stringResource(R.string.download_menu_style_compact)),
        )

    SettingsPage(
        title = stringResource(R.string.settings_downloads_title),
        onBack = onBack,
        highlight = highlight,
    ) {
        group(key = "downloads.storage", header = R.string.storage_header) {
            nav(DownloadsIndex.videoLocation, value = videoPath, icon = Icons.Outlined.VideoFile, showChevron = false, onClick = {
                locationTarget = DownloadTarget.VIDEO
            })
            nav(DownloadsIndex.musicLocation, value = musicPath, icon = Icons.Outlined.AudioFile, showChevron = false, onClick = {
                locationTarget = DownloadTarget.MUSIC
            })
            row(DownloadsIndex.usage.key) { shape -> StorageUsageRow(usage, storage?.usedFraction, shape) }
            choice(DownloadsIndex.cacheSize, onClick = { picker = DownloadPicker.CACHE }) { stringResource(cacheSizeLabel(cacheSizeMb)) }
        }
        group(key = "downloads.defaults", header = R.string.settings_section_download_defaults) {
            choice(DownloadsIndex.quickQuality, onClick = { picker = DownloadPicker.QUALITY }) {
                stringResource(videoQualityLabel(quickQuality))
            }
            choice(DownloadsIndex.codec, onClick = { picker = DownloadPicker.CODEC }) { codecLabel(codec) }
            toggleGroup(DownloadsIndex.menuStyle, menuStyles, menuStyle, viewModel::setMenuStyle)
            switch(DownloadsIndex.wifiOnly, viewModel.wifiOnly, viewModel::setWifiOnly)
        }
        group(key = "downloads.performance", header = R.string.performance_header, footer = R.string.performance_optimization_note) {
            slider(
                DownloadsIndex.threads,
                value = threads.toFloat(),
                onValueCommitted = { viewModel.setThreads(it.toInt()) },
                valueRange = 1f..MAX_THREADS.toFloat(),
                steps = MAX_THREADS - 2,
                valueLabel = { it.toInt().toString() },
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            group(
                key = "downloads.access",
                header = R.string.settings_section_storage_access,
                footer = R.string.settings_storage_access_summary,
            ) {
                nav(
                    DownloadsIndex.allFilesAccess,
                    value = allFilesLabel,
                    icon = Icons.Outlined.FolderSpecial,
                    enabled = !access.allFiles,
                    showChevron = false,
                    onClick = { context.openAllFilesAccess() },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    nav(
                        DownloadsIndex.videoAccess,
                        value = if (access.video) grantedLabel else videoDeniedLabel,
                        icon = Icons.Outlined.PermMedia,
                        enabled = !access.video,
                        showChevron = false,
                        onClick = { mediaPermission.launch(Manifest.permission.READ_MEDIA_VIDEO) },
                    )
                    nav(
                        DownloadsIndex.audioAccess,
                        value = if (access.audio) grantedLabel else audioDeniedLabel,
                        icon = Icons.Outlined.Folder,
                        enabled = !access.audio,
                        showChevron = false,
                        onClick = { mediaPermission.launch(Manifest.permission.READ_MEDIA_AUDIO) },
                    )
                }
            }
        }
    }

    locationTarget?.let { target ->
        DownloadLocationDialog(
            target = target,
            current = if (target == DownloadTarget.MUSIC) musicLocation else videoLocation,
            defaultPath = viewModel.defaultPath(target),
            downloadsPath = viewModel.downloadsPath(),
            internalPath = viewModel.internalPath(),
            onSelect = { viewModel.setLocation(target, it) },
            onBrowse = { folderPicker.launch(null) },
            onDismiss = { locationTarget = null },
        )
    }

    when (picker) {
        DownloadPicker.QUALITY -> {
            FlowChoiceDialog(
                title = stringResource(R.string.settings_quick_download_quality),
                options = VideoQualities.drop(1).map { FlowChoice(it, stringResource(videoQualityLabel(it))) },
                selected = quickQuality,
                onSelect = viewModel::setQuickQuality,
                onDismiss = { picker = null },
            )
        }

        DownloadPicker.CODEC -> {
            FlowChoiceDialog(
                title = stringResource(R.string.default_download_codec_label),
                options =
                    VideoCodec.entries.map { codecOption ->
                        FlowChoice(
                            codecOption,
                            codecLabel(codecOption),
                            if (codecOption == VideoCodec.AUTO) stringResource(R.string.default_download_codec_auto_subtitle) else null,
                        )
                    },
                selected = codec,
                onSelect = viewModel::setCodec,
                onDismiss = { picker = null },
            )
        }

        DownloadPicker.CACHE -> {
            FlowChoiceDialog(
                title = stringResource(R.string.cache_size_header),
                options = CacheSizes.map { FlowChoice(it, stringResource(cacheSizeLabel(it))) },
                selected = cacheSizeMb,
                onSelect = viewModel::setCacheSize,
                onDismiss = { picker = null },
            )
        }

        null -> {
            Unit
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StorageUsageRow(
    usage: String,
    usedFraction: Float?,
    shape: Shape,
) {
    SegmentedListItem(
        verticalAlignment = Alignment.CenterVertically,
        shapes = ListItemDefaults.shapes(shape = shape),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        leadingContent = { Icon(Icons.Outlined.SdStorage, contentDescription = null) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(UsageSpacing)) {
                Text(usage)
                usedFraction?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
            }
        },
    ) {
        Text(stringResource(DownloadsIndex.usage.title))
    }
}

private fun cacheSizeLabel(megabytes: Int): Int =
    when (megabytes) {
        100 -> R.string.cache_size_100mb
        200 -> R.string.cache_size_200mb
        0 -> R.string.cache_size_unlimited
        else -> R.string.cache_size_500mb
    }

private data class StorageAccess(
    val allFiles: Boolean,
    val video: Boolean,
    val audio: Boolean,
) {
    companion object {
        fun read(context: Context): StorageAccess {
            val tiramisu = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            return StorageAccess(
                allFiles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager(),
                video = tiramisu && context.granted(Manifest.permission.READ_MEDIA_VIDEO),
                audio = tiramisu && context.granted(Manifest.permission.READ_MEDIA_AUDIO),
            )
        }
    }
}

private fun Context.granted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.openAllFilesAccess() {
    val appPage = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", packageName, null))
    runCatching { startActivity(appPage) }
        .onFailure { runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } }
}
