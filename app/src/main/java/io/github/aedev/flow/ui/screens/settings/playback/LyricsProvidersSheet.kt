package io.github.aedev.flow.ui.screens.settings.playback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowSheetHeader
import io.github.aedev.flow.ui.components.shared.FlowSwitch
import io.github.aedev.flow.ui.components.shared.ReorderHandle
import io.github.aedev.flow.ui.components.shared.rememberFlowSheetState
import sh.calvin.reorderable.ReorderableColumn

private val RowPadding = 24.dp
private val SheetBottomSpacing = 16.dp

/**
 * The lyrics sources in the order they are tried. Rows drag by their handle — TalkBack users get
 * Move up and Move down instead — and each can be switched off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsProvidersSheet(
    viewModel: PlaybackSettingsViewModel,
    onDismiss: () -> Unit,
) {
    val persisted by viewModel.lyricsProviders.collectAsStateWithLifecycle()
    var providers by remember(persisted) { mutableStateOf(persisted) }
    val haptic = LocalHapticFeedback.current
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)

    fun move(
        from: Int,
        to: Int,
    ) {
        if (from == to || to !in providers.indices) return
        providers = providers.toMutableList().apply { add(to, removeAt(from)) }
        viewModel.setLyricsProviderOrder(providers.map { it.name })
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        FlowSheetHeader(
            title = stringResource(R.string.lyrics_provider_title),
            subtitle = stringResource(R.string.settings_lyrics_provider_order_description),
            onClose = onDismiss,
            showDragHandle = false,
        )
        ReorderableColumn(
            list = providers,
            onSettle = ::move,
            onMove = { haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) },
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(
                        PaddingValues(
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + SheetBottomSpacing,
                        ),
                    ),
        ) { index, provider, isDragging ->
            key(provider.name) {
                ReorderableItem {
                    Surface(color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = RowPadding)
                                    .semantics {
                                        customActions =
                                            buildList {
                                                if (index > 0) {
                                                    add(
                                                        CustomAccessibilityAction(moveUp) {
                                                            move(index, index - 1)
                                                            true
                                                        },
                                                    )
                                                }
                                                if (index < providers.lastIndex) {
                                                    add(
                                                        CustomAccessibilityAction(moveDown) {
                                                            move(index, index + 1)
                                                            true
                                                        },
                                                    )
                                                }
                                            }
                                    },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(LocalMinimumInteractiveComponentSize.current)
                                        .draggableHandle(),
                                contentAlignment = Alignment.Center,
                            ) { ReorderHandle() }
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            FlowSwitch(
                                checked = provider.enabled,
                                onCheckedChange = { viewModel.setLyricsProviderEnabled(provider.name, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}
