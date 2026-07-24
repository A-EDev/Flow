package io.github.aedev.flow.ui.tv.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Right-side modal panel — the TV replacement for bottom sheets and dropdowns.
 * While open it is a focus trap: D-pad can never move focus out of the panel,
 * and if the focused row leaves composition (content swap, list reload) focus
 * is re-grabbed automatically. Dismissed via Back or the header close button.
 * Placed inside a Box.
 */
@Composable
fun BoxScope.TvSidePanel(
    visible: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimens = LocalTvDimens.current
    val firstFocusRequester = remember { FocusRequester() }
    var panelHasFocus by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.align(Alignment.CenterEnd),
        enter = fadeIn() + slideInHorizontally { it / 3 },
        exit = fadeOut() + slideOutHorizontally { it / 3 },
    ) {
        Surface(
            modifier = Modifier
                .width(dimens.sidePanelWidth)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { panelHasFocus = it.hasFocus }
                    // Trap: no D-pad direction may move focus out of the panel.
                    .focusProperties {
                        @OptIn(ExperimentalComposeUiApi::class)
                        exit = { FocusRequester.Cancel }
                    }
                    .focusGroup()
                    .padding(
                        horizontal = 24.dp,
                        vertical = dimens.overscanVertical,
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    onClose?.let {
                        TvIconButton(
                            icon = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.close),
                            onClick = it,
                        )
                    }
                }
                // Focus lands on the content's first row, not the close button.
                Box(
                    Modifier
                        .weight(1f)
                        .focusRequester(firstFocusRequester)
                        .focusGroup()
                ) {
                    Column(content = content)
                }
            }
        }
    }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        snapshotFlow { panelHasFocus }.collectLatest { hasFocus ->
            if (hasFocus) return@collectLatest
            repeat(20) {
                delay(120)
                if (panelHasFocus) return@collectLatest
                runCatching { firstFocusRequester.requestFocus() }
            }
        }
    }
}
