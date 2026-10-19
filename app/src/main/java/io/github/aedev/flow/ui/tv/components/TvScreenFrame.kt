package io.github.aedev.flow.ui.tv.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

/** The 16 dp margin every phone page already keeps at its edges. */
private val PageMargin = 16.dp

/**
 * Holds a phone page inside the TV safe area. The rail already sits on the start edge, and the page
 * keeps its own margin, so only the top and the rest of the end edge are added.
 */
@Composable
fun TvScreenFrame(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val dimens = LocalTvDimens.current
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(top = dimens.overscanVertical, end = dimens.overscanHorizontal - PageMargin),
        content = content,
    )
}
