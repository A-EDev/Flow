package io.github.aedev.flow.ui.screens.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowEmptyState

@Composable
internal fun HomeFeedDisabledState(modifier: Modifier = Modifier) {
    FlowEmptyState(
        title = stringResource(R.string.content_settings_home_feed_disabled_title),
        subtitle = stringResource(R.string.content_settings_home_feed_disabled_body),
        icon = Icons.Outlined.SmartDisplay,
        modifier = modifier,
    )
}
