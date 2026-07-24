package io.github.aedev.flow.ui.tv.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

/**
 * Screen frame for TV destinations: overscan-safe header area with an optional
 * trailing action, then full-bleed content. Scrolling content applies its own
 * horizontal overscan padding so cards can travel edge to edge.
 */
@Composable
fun TvScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val dimens = LocalTvDimens.current
    Column(modifier = modifier.fillMaxSize()) {
        TvScreenHeader(
            title = title,
            subtitle = subtitle,
            action = action,
            modifier = Modifier.padding(
                start = dimens.overscanHorizontal,
                end = dimens.overscanHorizontal,
                top = dimens.overscanVertical,
            ),
        )
        Spacer(Modifier.height(20.dp))
        Box(Modifier.weight(1f)) {
            content()
        }
    }
}
