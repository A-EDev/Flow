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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import io.github.aedev.flow.ui.components.library.LocalMediaList
import io.github.aedev.flow.ui.components.library.LocalMediaPermissionState
import io.github.aedev.flow.ui.components.shared.MediaKind
import io.github.aedev.flow.ui.components.shared.MediaKindSelector

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
