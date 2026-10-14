package io.github.aedev.flow.ui.screens.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.notes.Note
import io.github.aedev.flow.data.notes.NoteKind
import io.github.aedev.flow.ui.components.layout.LocalFlowBottomInsets
import io.github.aedev.flow.ui.components.layout.floatAboveBottomChrome
import io.github.aedev.flow.ui.components.layout.navigation.LocalMediaNavigator
import io.github.aedev.flow.ui.components.layout.rememberFlowPaneScaffoldDirective
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowNoteEditorDialog
import io.github.aedev.flow.ui.components.shared.FlowSelectionAction
import io.github.aedev.flow.ui.components.shared.FlowSelectionToolbar
import kotlinx.coroutines.launch

private val PaneCornerInset = 16.dp
private val KeySetSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })

/**
 * Every note in one place: the list beside the open note from medium width, one at a time on a
 * phone. A note's times play the video from there; the note itself opens its page. A long press
 * selects notes to delete, share or put in order.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun NotesScreen(
    onBackClick: () -> Unit,
    onPlay: (video: Video, startPositionMs: Long?) -> Unit,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = rememberListDetailPaneScaffoldNavigator<String>(scaffoldDirective = rememberFlowPaneScaffoldDirective())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val mediaNavigator = LocalMediaNavigator.current
    val snackbarHostState = remember { SnackbarHostState() }
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedKeys by rememberSaveable(stateSaver = KeySetSaver) { mutableStateOf(emptySet<String>()) }
    var reordering by rememberSaveable { mutableStateOf(false) }

    val twoPane =
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded &&
            navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
    val showingDetailAlone = !twoPane && navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Detail
    val openKey = navigator.currentDestination?.contentKey
    val openNote = state.all?.firstOrNull { it.key == openKey } ?: state.visible.firstOrNull()?.takeIf { twoPane }
    val selectedNotes = state.all.orEmpty().filter { it.key in selectedKeys }

    fun exitSelection() {
        selecting = false
        selectedKeys = emptySet()
    }

    fun open(note: Note) {
        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, note.key) }
    }

    fun back() {
        scope.launch { if (!navigator.navigateBack()) onBackClick() }
    }

    val undoLabel = stringResource(R.string.action_undo)
    val copiedLabel = stringResource(R.string.note_copied)

    fun delete(notes: List<Note>) {
        if (notes.isEmpty()) return
        viewModel.delete(notes)
        if (showingDetailAlone && notes.any { it.key == openKey }) back()
        exitSelection()
        val message = resources.getQuantityString(R.plurals.notes_deleted, notes.size, notes.size)
        scope.launch {
            val result = snackbarHostState.showSnackbar(message = message, actionLabel = undoLabel)
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(notes)
        }
    }

    fun copy(note: Note) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("note", note.text))
        scope.launch { snackbarHostState.showSnackbar(copiedLabel) }
    }

    fun share(notes: List<Note>) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText(notes))
        context.startActivity(Intent.createChooser(send, null))
    }

    BackHandler(enabled = selecting || reordering) {
        if (reordering) reordering = false else exitSelection()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            when {
                reordering -> {
                    FlowTopBar(title = stringResource(R.string.notes_reorder_title), onBack = { reordering = false })
                }

                selecting -> {
                    FlowTopBar(
                        title = pluralStringResource(R.plurals.selected_count_template, selectedKeys.size, selectedKeys.size),
                        onBack = ::exitSelection,
                        actions = {
                            IconButton(onClick = {
                                val shown = state.visible.map { it.key }.toSet()
                                selectedKeys = if (selectedKeys == shown) emptySet() else shown
                            }) {
                                Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.select_all))
                            }
                        },
                    )
                }

                else -> {
                    FlowTopBar(
                        title = if (showingDetailAlone) "" else stringResource(R.string.notes_title),
                        onBack = if (showingDetailAlone) ::back else onBackClick,
                        actions = {
                            if (!showingDetailAlone && !state.all.isNullOrEmpty()) {
                                NotesSortButton(selected = state.sort, onSelected = viewModel::setSort)
                                IconButton(onClick = { selecting = true }) {
                                    Icon(Icons.Default.Checklist, contentDescription = stringResource(R.string.notes_select))
                                }
                            }
                            if (openNote != null && (showingDetailAlone || twoPane)) {
                                NoteMenuButton(
                                    note = openNote,
                                    onCopy = { copy(openNote) },
                                    onOpenChannel = mediaNavigator::openChannel,
                                    onDelete = { delete(listOf(openNote)) },
                                )
                            }
                        },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.floatAboveBottomChrome(LocalFlowBottomInsets.current)) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (reordering) {
                NotesReorderList(notes = state.visible, onOrderChanged = viewModel::saveOrder)
            } else {
                NavigableListDetailPaneScaffold(
                    navigator = navigator,
                    listPane = {
                        AnimatedPane {
                            NotesListPane(
                                state = state,
                                openKey = openNote?.key?.takeIf { twoPane },
                                selection =
                                    NotesSelection(
                                        active = selecting,
                                        keys = selectedKeys,
                                        onToggle = { note ->
                                            selectedKeys =
                                                if (note.key in selectedKeys) selectedKeys - note.key else selectedKeys + note.key
                                        },
                                        onStart = { note ->
                                            selecting = true
                                            selectedKeys = setOf(note.key)
                                        },
                                    ),
                                onQueryChange = viewModel::setQuery,
                                onFilterChange = viewModel::setFilter,
                                onOpen = ::open,
                                onPlayMoment = { note, positionMs -> onPlay(note.toVideo(), positionMs) },
                            )
                        }
                    },
                    detailPane = {
                        AnimatedPane {
                            val paneModifier =
                                if (twoPane) {
                                    Modifier
                                        .padding(end = PaneCornerInset, bottom = PaneCornerInset)
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.extraLarge)
                                } else {
                                    Modifier
                                }
                            val note = openNote
                            if (note == null) {
                                FlowEmptyState(
                                    title = stringResource(R.string.notes_pick_title),
                                    icon = Icons.Outlined.StickyNote2,
                                    modifier = paneModifier.fillMaxSize(),
                                )
                            } else {
                                NoteDetailPane(
                                    note = note,
                                    onPlay = { positionMs -> onPlay(note.toVideo(), positionMs) },
                                    onOpenChannel = mediaNavigator::openChannel,
                                    onEdit = { editingKey = note.key },
                                    modifier = paneModifier,
                                )
                            }
                        }
                    },
                )
            }
            FlowSelectionToolbar(
                visible = selecting && selectedKeys.isNotEmpty(),
                summary = pluralStringResource(R.plurals.selected_count_template, selectedKeys.size, selectedKeys.size),
                actions =
                    listOf(
                        FlowSelectionAction(Icons.Outlined.Delete, stringResource(R.string.action_delete)) { delete(selectedNotes) },
                        FlowSelectionAction(Icons.Outlined.Share, stringResource(R.string.action_share)) { share(selectedNotes) },
                        FlowSelectionAction(Icons.Default.SwapVert, stringResource(R.string.notes_reorder)) {
                            exitSelection()
                            viewModel.startReordering()
                            reordering = true
                        },
                    ),
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    val editing = editingKey?.let { key -> state.all?.firstOrNull { it.key == key } }
    if (editing != null) {
        FlowNoteEditorDialog(
            initialText = editing.text,
            title = stringResource(if (editing.kind == NoteKind.Video) R.string.note_video_title else R.string.note_channel_title),
            onSave = { text -> viewModel.save(editing, text) },
            onDismiss = { editingKey = null },
        )
    }
}

/** The sort as an icon in the top bar, with the current choice checked in its menu. */
@Composable
private fun NotesSortButton(
    selected: NotesSort,
    onSelected: (NotesSort) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.notes_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            NotesSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sort.labelRes)) },
                    trailingIcon = { if (sort == selected) Icon(Icons.Default.Check, contentDescription = null) },
                    onClick = {
                        open = false
                        onSelected(sort)
                    },
                )
            }
        }
    }
}
