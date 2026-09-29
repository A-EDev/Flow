package io.github.aedev.flow.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowEmptyState

private const val ACTION_WIDTH_FRACTION = 0.6f

/** Access to the library is missing: ask again, or send the viewer to settings once asking can't work. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LocalMediaPermissionState(
    canAsk: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    FlowEmptyState(
        title = stringResource(R.string.local_media_permission_title),
        subtitle = stringResource(if (canAsk) R.string.local_media_permission_body else R.string.local_permission_denied_body),
        icon = Icons.Outlined.VideoLibrary,
        action = {
            FilledTonalButton(
                onClick = if (canAsk) onGrant else onOpenSettings,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(ACTION_WIDTH_FRACTION).height(ButtonDefaults.MinHeight),
            ) {
                Text(stringResource(if (canAsk) R.string.local_media_grant else R.string.local_open_settings))
            }
        },
    )
}

/** Android 14's "Select photos and videos": say only some files are visible and offer the picker again. */
@Composable
internal fun PartialAccessBanner(
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.local_partial_access_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.local_partial_access_body), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onManage) { Text(stringResource(R.string.local_manage)) }
            }
        }
    }
}

/** How many files the library's rules hide, with a way to change the rules. */
@Composable
internal fun HiddenFilesNote(
    count: Int,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = pluralStringResource(R.plurals.local_hidden_count, count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onManage) { Text(stringResource(R.string.local_manage)) }
    }
}
