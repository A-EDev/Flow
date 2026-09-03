package io.github.aedev.flow.ui.screens.library

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.shared.ArtworkThumbnail
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.MediaKind
import io.github.aedev.flow.ui.components.shared.MediaKindSelector
import io.github.aedev.flow.ui.components.shared.MediaRow
import io.github.aedev.flow.ui.components.shared.MediaThumbnail
import io.github.aedev.flow.utils.formatDurationMillis
import java.util.Locale

private val ListContentPadding = PaddingValues(vertical = 8.dp)
private val ListItemSpacing = 2.dp
private val ScanIndicatorSize = 36.dp
private val ScanIndicatorStroke = 3.dp
private val EmptyActionWidthFraction = 0.6f
private val EmptyActionHeight = 48.dp
private const val BYTES_PER_UNIT = 1024.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMediaScreen(
    onBackClick: () -> Unit,
    onVideoClick: (LocalMediaItem) -> Unit,
    onMusicClick: (items: List<LocalMediaItem>, index: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocalMediaViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedKind by remember { mutableStateOf(MediaKind.Videos) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val permissionsToRequest =
        remember {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

    fun hasAnyPermission(): Boolean =
        permissionsToRequest.any { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { results ->
            if (results.values.any { it }) viewModel.scan() else viewModel.onPermissionDenied()
        }

    LaunchedEffect(Unit) {
        if (hasAnyPermission()) viewModel.scan() else permissionLauncher.launch(permissionsToRequest)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            FlowTopBar(
                title = stringResource(R.string.local_media_title),
                onBack = onBackClick,
                actions = {
                    IconButton(
                        onClick = {
                            if (hasAnyPermission()) {
                                viewModel.scan()
                            } else {
                                permissionLauncher.launch(permissionsToRequest)
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.local_media_rescan),
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            MediaKindSelector(
                options = MediaKind.entries,
                selected = selectedKind,
                onSelected = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedKind = it
                },
                label = { stringResource(it.labelRes) },
                icon = { it.icon },
            )

            if (uiState.permissionDenied && !hasAnyPermission()) {
                LocalMediaPermissionState(
                    onGrant = {
                        if (hasAnyPermission()) {
                            viewModel.scan()
                        } else {
                            permissionLauncher.launch(permissionsToRequest)
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null),
                                    ),
                                )
                            }
                        }
                    },
                )
            } else {
                Crossfade(
                    targetState = selectedKind,
                    animationSpec = tween(250, easing = EaseOutCubic),
                    label = "local_kind_crossfade",
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .weight(1f),
                ) { kind ->
                    LocalMediaList(
                        items = if (kind == MediaKind.Videos) uiState.videos else uiState.music,
                        kind = kind,
                        isScanning = uiState.isScanning,
                        hasScanned = uiState.hasScanned,
                        onRefresh = { viewModel.scan() },
                        onItemClick = { items, index ->
                            if (kind == MediaKind.Videos) onVideoClick(items[index]) else onMusicClick(items, index)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalMediaList(
    items: List<LocalMediaItem>,
    kind: MediaKind,
    isScanning: Boolean,
    hasScanned: Boolean,
    onRefresh: () -> Unit,
    onItemClick: (List<LocalMediaItem>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isScanning,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier.fillMaxSize(),
    ) {
        if (items.isEmpty()) {
            LocalMediaEmptyState(kind = kind, isScanning = isScanning && !hasScanned)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = ListContentPadding,
                verticalArrangement = Arrangement.spacedBy(ListItemSpacing),
            ) {
                itemsIndexed(
                    items = items,
                    key = { _, item -> item.id },
                    contentType = { _, _ -> kind.name },
                ) { index, item ->
                    LocalMediaRow(
                        item = item,
                        kind = kind,
                        onClick = { onItemClick(items, index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalMediaRow(
    item: LocalMediaItem,
    kind: MediaKind,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val trailingLabel =
        if (kind == MediaKind.Videos) {
            fileSizeLabel(item.sizeBytes)
        } else {
            if (item.durationMs > 0) formatDurationMillis(item.durationMs) else ""
        }

    MediaRow(
        title = item.title,
        modifier = modifier,
        subtitle = mediaSubtitle(item.subtitle, trailingLabel),
        titleMaxLines = if (kind == MediaKind.Videos) 2 else 1,
        onClick = onClick,
    ) {
        if (kind == MediaKind.Videos) {
            MediaThumbnail(
                videoId = item.contentUri,
                thumbnailUrl = item.contentUri,
                durationSeconds = (item.durationMs / 1000L).toInt(),
                placeholder = Icons.Outlined.VideoLibrary,
            )
        } else {
            ArtworkThumbnail(
                thumbnailUrl = item.artworkUri,
                placeholder = Icons.Outlined.MusicNote,
            )
        }
    }
}

@Composable
private fun LocalMediaEmptyState(
    kind: MediaKind,
    isScanning: Boolean,
) {
    val label = stringResource(kind.labelRes)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        if (isScanning) {
            FlowEmptyState(
                title = stringResource(R.string.local_media_scanning),
                action = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ScanIndicatorSize),
                        strokeWidth = ScanIndicatorStroke,
                    )
                },
            )
        } else {
            FlowEmptyState(
                title = stringResource(R.string.local_media_empty_title, label),
                subtitle = stringResource(R.string.local_media_empty_body),
                icon = kind.icon,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LocalMediaPermissionState(onGrant: () -> Unit) {
    FlowEmptyState(
        title = stringResource(R.string.local_media_permission_title),
        subtitle = stringResource(R.string.local_media_permission_body),
        icon = Icons.Outlined.VideoLibrary,
        action = {
            FilledTonalButton(
                onClick = onGrant,
                shapes = ButtonDefaults.shapes(),
                modifier =
                    Modifier
                        .fillMaxWidth(EmptyActionWidthFraction)
                        .height(EmptyActionHeight),
            ) {
                Text(stringResource(R.string.local_media_grant))
            }
        },
    )
}

@Composable
private fun mediaSubtitle(
    primary: String,
    secondary: String,
): String =
    when {
        primary.isNotBlank() && secondary.isNotBlank() -> {
            stringResource(R.string.video_metadata_short_template, primary, secondary)
        }

        primary.isNotBlank() -> {
            primary
        }

        else -> {
            secondary
        }
    }

@Composable
private fun fileSizeLabel(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kilobytes = bytes / BYTES_PER_UNIT
    if (kilobytes < BYTES_PER_UNIT) {
        return stringResource(R.string.file_size_kb, String.format(Locale.getDefault(), "%.0f", kilobytes))
    }
    val megabytes = kilobytes / BYTES_PER_UNIT
    if (megabytes < BYTES_PER_UNIT) {
        return stringResource(R.string.file_size_mb, String.format(Locale.getDefault(), "%.1f", megabytes))
    }
    return stringResource(
        R.string.file_size_gb,
        String.format(Locale.getDefault(), "%.2f", megabytes / BYTES_PER_UNIT),
    )
}
