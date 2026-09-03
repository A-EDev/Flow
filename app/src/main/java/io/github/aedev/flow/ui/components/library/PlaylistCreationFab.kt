package io.github.aedev.flow.ui.components.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R

internal enum class PlaylistCreationTarget {
    Video,
    Music,
}

@Composable
internal fun PlaylistCreationFabMenu(
    onCreateVideo: () -> Unit,
    onCreateMusic: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val iconRotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        label = "playlist-fab-icon",
    )
    BackHandler(enabled = expanded) { expanded = false }

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.video)) },
                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onCreateVideo()
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.tab_music)) },
                    icon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onCreateMusic()
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription =
                    stringResource(
                        if (expanded) {
                            R.string.playlist_creation_menu_close
                        } else {
                            R.string.playlist_creation_menu_open
                        },
                    ),
                modifier = Modifier.rotate(iconRotation),
            )
        }
    }
}
