package io.github.aedev.flow.ui.screens.library

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.QueuePlayNext
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.localmedia.LocalMediaItem
import io.github.aedev.flow.data.localmedia.toMusicTrack
import io.github.aedev.flow.data.localmedia.toVideo
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.library.LibraryKindHeader
import io.github.aedev.flow.ui.components.library.LibrarySelection
import io.github.aedev.flow.ui.components.shared.FlowErrorState
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.shared.FlowSelectionAction
import io.github.aedev.flow.ui.components.shared.FlowSelectionToolbar
import io.github.aedev.flow.ui.components.shared.FlowSidePanes
import io.github.aedev.flow.ui.components.shared.MediaKind
import io.github.aedev.flow.ui.components.shared.dismissKeyboardOnPress
import io.github.aedev.flow.ui.components.shared.flowGridColumns
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionUndo
import io.github.aedev.flow.ui.components.shared.quickactions.sharedQuickActionsViewModel
import io.github.aedev.flow.ui.components.shared.rememberFlowPaneState
import io.github.aedev.flow.ui.components.shared.shareMediaFiles
import io.github.aedev.flow.ui.screens.music.sharedMusicPlayerViewModel

private val FolderPaneWidth = 360.dp

@Composable
fun LocalMediaScreen(
    onBackClick: () -> Unit,
    onPlayVideos: (items: List<LocalMediaItem>, startIndex: Int, shuffle: Boolean) -> Unit,
    onPlayMusic: (items: List<LocalMediaItem>, startIndex: Int, shuffle: Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocalMediaViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val quickActions = sharedQuickActionsViewModel()
    val musicPlayer = sharedMusicPlayerViewModel()
    val isVideos = state.selection.kind == MediaKind.Videos

    var accessCheck by remember { mutableIntStateOf(0) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val videoAccess = remember(accessCheck) { context.videoAccess() }
    val musicAccess = remember(accessCheck) { context.musicAccess() }
    val access = if (isVideos) videoAccess else musicAccess
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            askedOnce = true
            accessCheck++
            viewModel.refresh()
        }
    val askForAccess = { permissionLauncher.launch(localMediaPermissions()) }
    LaunchedEffect(Unit) { if (videoAccess == MediaAccess.NONE && musicAccess == MediaAccess.NONE && !askedOnce) askForAccess() }
    // Access granted in app settings arrives while this screen is paused; look again on return.
    LifecycleResumeEffect(Unit) {
        val before = context.videoAccess() to context.musicAccess()
        if (before != (videoAccess to musicAccess)) {
            accessCheck++
            viewModel.refresh()
        }
        onPauseOrDispose {}
    }

    var menuItem by remember { mutableStateOf<LocalMediaItem?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val exitSelection = {
        selectionMode = false
        selectedIds = emptySet()
    }
    var pendingDelete by remember { mutableStateOf<List<String>>(emptyList()) }
    val deleteLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                exitSelection()
                if (deleteMovesToTrash) {
                    quickActions.announce(
                        context.resources.getQuantityString(R.plurals.local_files_trashed, pendingDelete.size, pendingDelete.size),
                        QuickActionUndo.RestoreFromTrash(pendingDelete),
                    )
                } else {
                    // On Android 10 the prompt only grants access; the delete itself runs again now.
                    val outcome = context.contentResolver.requestDelete(pendingDelete.map(Uri::parse))
                    if (outcome is LocalDeleteOutcome.Deleted) {
                        quickActions.announce(
                            context.resources.getQuantityString(R.plurals.local_files_deleted, outcome.count, outcome.count),
                        )
                    }
                }
            }
            pendingDelete = emptyList()
        }
    val deleteFiles = { items: List<LocalMediaItem> ->
        when (val outcome = context.contentResolver.requestDelete(items.map { Uri.parse(it.contentUri) })) {
            is LocalDeleteOutcome.NeedsConsent -> {
                pendingDelete = items.map { it.contentUri }
                deleteLauncher.launch(IntentSenderRequest.Builder(outcome.request).build())
            }

            is LocalDeleteOutcome.Deleted -> {
                quickActions.announce(
                    context.resources.getQuantityString(R.plurals.local_files_deleted, outcome.count, outcome.count),
                )
            }

            LocalDeleteOutcome.Failed -> {
                quickActions.announce(context.getString(R.string.local_delete_failed))
            }
        }
    }
    val shareFiles = { items: List<LocalMediaItem> ->
        val mime =
            if (items.all { it.isVideo }) {
                "video/*"
            } else if (items.none { it.isVideo }) {
                "audio/*"
            } else {
                "*/*"
            }
        context.shareMediaFiles(items.map { Uri.parse(it.contentUri) }, mime)
    }
    val fileActions =
        LocalFileActions(
            onPlayNext = { if (it.isVideo) quickActions.playVideoNext(it.toVideo()) else musicPlayer.playNext(it.toMusicTrack()) },
            onAddToQueue = { if (it.isVideo) quickActions.addVideoToQueue(it.toVideo()) else musicPlayer.addToQueue(it.toMusicTrack()) },
            onShare = shareFiles,
            onHideFolder = { item ->
                viewModel.hideFolder(item.folderId)
                quickActions.announce(context.getString(R.string.local_folder_hidden, item.folderName))
            },
            onDelete = deleteFiles,
            deleteMovesToTrash = deleteMovesToTrash,
        )

    val openFolder = state.openFolder
    BackHandler(enabled = selectionMode || openFolder != null) {
        if (selectionMode) exitSelection() else viewModel.openFolder(null)
    }

    val panes = rememberFlowPaneState()
    val twoPaneFolders = state.selection.view == LocalView.FOLDERS && panes.showsSidePane
    LaunchedEffect(twoPaneFolders, state.folders) {
        if (twoPaneFolders && openFolder == null) state.folders.firstOrNull()?.let { viewModel.openFolder(it.id) }
    }
    val gridColumns = flowGridColumns(compact = 2, medium = 3, expanded = 3)
    val asGrid = isVideos && (state.settings.videosAsGrid ?: (gridColumns > 2))
    val columns = if (asGrid) (if (twoPaneFolders) gridColumns - 1 else gridColumns) else 1
    val selection =
        LibrarySelection(selectionMode, selectedIds) { id ->
            selectedIds =
                if (id in selectedIds) selectedIds - id else selectedIds + id
        }
    val contentActions =
        LocalContentActions(
            onPlay = { items, index, shuffle -> if (isVideos) onPlayVideos(items, index, shuffle) else onPlayMusic(items, index, shuffle) },
            onLongClick = { menuItem = it },
            onOpenFolder = viewModel::openFolder,
            onManageHidden = onOpenSettings,
        )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            LocalMediaTopBar(
                title =
                    when {
                        selectionMode -> pluralStringResource(R.plurals.selected_count_template, selectedIds.size, selectedIds.size)
                        openFolder != null && !twoPaneFolders -> openFolder.name
                        else -> stringResource(R.string.local_media_title)
                    },
                onBack = {
                    when {
                        selectionMode -> exitSelection()
                        openFolder != null && !twoPaneFolders -> viewModel.openFolder(null)
                        else -> onBackClick()
                    }
                },
                selectionMode = selectionMode,
                canSelect = state.items.isNotEmpty() && access != MediaAccess.NONE,
                showGridToggle = isVideos && access != MediaAccess.NONE,
                asGrid = asGrid,
                onSelect = { selectionMode = true },
                onSelectAll = {
                    val shown = state.items.mapTo(HashSet()) { it.mediaId }
                    selectedIds = if (selectedIds.containsAll(shown)) emptySet() else shown
                },
                onToggleGrid = { viewModel.setVideosAsGrid(!asGrid) },
                onOpenSettings = onOpenSettings,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                LibraryKindHeader(
                    query = state.selection.filters.query,
                    onQueryChange = { query -> viewModel.updateFilters { it.copy(query = query) } },
                    placeholder = stringResource(R.string.local_search_hint),
                    selectedKind = state.selection.kind,
                    onKindSelected = {
                        exitSelection()
                        viewModel.selectKind(it)
                    },
                )
                if (access != MediaAccess.NONE) {
                    LocalMediaFilterBar(
                        isVideos = isVideos,
                        view = state.selection.view,
                        filters = state.selection.filters,
                        onViewChange = viewModel::selectView,
                        onFiltersChange = viewModel::updateFilters,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Crossfade(
                    targetState = state.selection.kind,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "local_kind",
                    modifier = Modifier.weight(1f).dismissKeyboardOnPress { focusManager.clearFocus() },
                ) {
                    when {
                        access == MediaAccess.NONE -> {
                            val activity = context as? Activity
                            LocalMediaPermissionState(
                                canAsk = !askedOnce || activity?.canStillAsk() == true,
                                onGrant = askForAccess,
                                onOpenSettings = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.fromParts("package", context.packageName, null),
                                            ),
                                        )
                                    }
                                },
                            )
                        }

                        state.isLoading -> {
                            FlowLoadingIndicator()
                        }

                        state.failed -> {
                            FlowErrorState(error = stringResource(R.string.local_load_failed), onRetry = viewModel::refresh)
                        }

                        else -> {
                            val banner: (@Composable () -> Unit)? =
                                if (isVideos &&
                                    access == MediaAccess.PARTIAL
                                ) {
                                    ({ PartialAccessBanner(onManage = askForAccess) })
                                } else {
                                    null
                                }
                            val showFolders = state.selection.view == LocalView.FOLDERS
                            val content: @Composable () -> Unit = {
                                LocalMediaContent(
                                    state = state,
                                    showFolders = showFolders && openFolder == null && !twoPaneFolders,
                                    columns = columns,
                                    selection = selection,
                                    actions = contentActions,
                                    isRefreshing = isRefreshing,
                                    onRefresh = viewModel::refresh,
                                    banner = banner,
                                )
                            }
                            if (twoPaneFolders) {
                                FlowSidePanes(
                                    panes = panes,
                                    sidePaneWidth = FolderPaneWidth,
                                    sidePane = { LocalFolderList(state, isVideos, onOpenFolder = viewModel::openFolder) },
                                    mainPane = content,
                                )
                            } else {
                                content()
                            }
                        }
                    }
                }
            }
            FlowSelectionToolbar(
                visible = selectionMode && selectedIds.isNotEmpty(),
                summary = pluralStringResource(R.plurals.selected_count_template, selectedIds.size, selectedIds.size),
                actions =
                    listOf(
                        FlowSelectionAction(Icons.Outlined.QueuePlayNext, stringResource(R.string.add_to_queue)) {
                            state.items.filter { it.mediaId in selectedIds }.forEach(fileActions.onAddToQueue)
                            exitSelection()
                        },
                        FlowSelectionAction(Icons.Outlined.Share, stringResource(R.string.share)) {
                            shareFiles(state.items.filter { it.mediaId in selectedIds })
                        },
                        FlowSelectionAction(Icons.Outlined.Delete, stringResource(R.string.action_delete)) {
                            deleteFiles(state.items.filter { it.mediaId in selectedIds })
                        },
                    ),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    menuItem?.let { item -> LocalFileMenu(item = item, actions = fileActions, onDismiss = { menuItem = null }) }
}

@Composable
private fun LocalMediaTopBar(
    title: String,
    onBack: () -> Unit,
    selectionMode: Boolean,
    canSelect: Boolean,
    showGridToggle: Boolean,
    asGrid: Boolean,
    onSelect: () -> Unit,
    onSelectAll: () -> Unit,
    onToggleGrid: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    FlowTopBar(
        title = title,
        onBack = onBack,
        actions = {
            if (selectionMode) {
                IconButton(
                    onClick = onSelectAll,
                ) { Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.select_all)) }
                return@FlowTopBar
            }
            if (showGridToggle) {
                IconButton(onClick = onToggleGrid) {
                    Icon(
                        imageVector = if (asGrid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                        contentDescription = stringResource(if (asGrid) R.string.local_list_view else R.string.local_grid_view),
                    )
                }
            }
            if (canSelect) {
                IconButton(
                    onClick = onSelect,
                ) { Icon(Icons.Default.Checklist, contentDescription = stringResource(R.string.select_videos)) }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_local_media_title))
            }
        },
    )
}
