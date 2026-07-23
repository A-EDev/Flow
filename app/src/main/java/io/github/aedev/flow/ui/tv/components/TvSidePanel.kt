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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

/**
 * Right-side modal panel — the TV replacement for bottom sheets and dropdowns.
 * Grabs focus on open (the caller dismisses via Back). Placed inside a Box.
 */
@Composable
fun BoxScope.TvSidePanel(
    visible: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimens = LocalTvDimens.current
    val firstFocusRequester = remember { FocusRequester() }

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
                    .focusRequester(firstFocusRequester)
                    .focusGroup()
                    .padding(
                        horizontal = 24.dp,
                        vertical = dimens.overscanVertical,
                    ),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Box(Modifier.weight(1f)) {
                    Column(content = content)
                }
            }
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            runCatching { firstFocusRequester.requestFocus() }
        }
    }
}
