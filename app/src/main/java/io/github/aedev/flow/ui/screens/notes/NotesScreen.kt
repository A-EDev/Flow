package io.github.aedev.flow.ui.screens.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch

private val PaneCornerInset = 16.dp

/**
 * Every note in one place: the list beside the open note from medium width, one at a time on a
 * phone. A note's times play the video from there; the note itself opens its page.
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
    val mediaNavigator = LocalMediaNavigator.current
    val snackbarHostState = remember { SnackbarHostState() }
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }

    val twoPane =
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded &&
            navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
    val showingDetailAlone = !twoPane && navigator.currentDestination?.pane == ListDetailPaneScaffoldRole.Detail
    val openKey = navigator.currentDestination?.contentKey
    val openNote = state.all?.firstOrNull { it.key == openKey } ?: state.visible.firstOrNull()?.takeIf { twoPane }

    fun open(note: Note) {
        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, note.key) }
    }

    fun back() {
        scope.launch { if (!navigator.navigateBack()) onBackClick() }
    }

    val deletedLabel = stringResource(R.string.note_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val copiedLabel = stringResource(R.string.note_copied)

    fun delete(note: Note) {
        viewModel.delete(note)
        if (showingDetailAlone) back()
        scope.launch {
            val result = snackbarHostState.showSnackbar(message = deletedLabel, actionLabel = undoLabel)
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(note)
        }
    }

    fun copy(note: Note) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("note", note.text))
        scope.launch { snackbarHostState.showSnackbar(copiedLabel) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            FlowTopBar(
                title = if (showingDetailAlone) "" else stringResource(R.string.notes_title),
                onBack = if (showingDetailAlone) ::back else onBackClick,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.floatAboveBottomChrome(LocalFlowBottomInsets.current)) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            modifier = Modifier.padding(padding),
            listPane = {
                AnimatedPane {
                    NotesListPane(
                        state = state,
                        selectedKey = openNote?.key?.takeIf { twoPane },
                        onQueryChange = viewModel::setQuery,
                        onFilterChange = viewModel::setFilter,
                        onSortChange = viewModel::setSort,
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
                            onCopy = { copy(note) },
                            onDelete = { delete(note) },
                            modifier = paneModifier,
                        )
                    }
                }
            },
        )
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
