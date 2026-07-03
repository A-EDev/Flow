package io.github.aedev.flow.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.screens.settings.ImportProgressBanner
import io.github.aedev.flow.ui.screens.settings.ImportViewModel

@Composable
internal fun ImportStep(
    importState: ImportViewModel.State,
    onImportFlowBackup: () -> Unit,
    onImportMasterBackup: () -> Unit,
    onImportEngineData: () -> Unit,
    onImportNewPipe: () -> Unit,
    onImportYouTube: () -> Unit,
    onImportYouTubeHistory: () -> Unit,
    onImportFreeTubeHistory: () -> Unit,
    onImportNewPipeHistory: () -> Unit,
    onImportLibreTube: () -> Unit,
    onImportMetrolist: () -> Unit,
    onImportNewPipePlaylists: () -> Unit,
    onImportLibreTubePlaylists: () -> Unit,
    onImportYouTubeTakeout: () -> Unit,
    onImportYouTubePlaylist: () -> Unit,
    onImportYouTubeMusicPlaylist: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            StepHeader(
                title = stringResource(R.string.onboarding_import_title),
                subtitle = stringResource(R.string.onboarding_import_subtitle)
            )
        }
        item { ImportProgressBanner(importState) }

        item { ImportSectionLabel(stringResource(R.string.onboarding_backup_restore_section)) }
        item {
            ImportCard(
                painter = rememberVectorPainter(Icons.Default.Restore),
                title = stringResource(R.string.import_flow_backup_item_title),
                description = stringResource(R.string.import_flow_backup_desc),
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onImportFlowBackup
            )
        }
        item {
            ImportCard(
                painter = rememberVectorPainter(Icons.Outlined.Archive),
                title = stringResource(R.string.import_master_backup_title),
                description = stringResource(R.string.import_master_backup_desc),
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onImportMasterBackup
            )
        }
        item {
            ImportCard(
                painter = rememberVectorPainter(Icons.Outlined.Psychology),
                title = stringResource(R.string.import_engine_data),
                description = stringResource(R.string.import_engine_data_desc),
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onImportEngineData
            )
        }

        item { ImportSectionLabel(stringResource(R.string.import_subscriptions_section_title)) }
        item {
            ImportCard(
                painter = painterResource(id = R.drawable.ic_newpipe),
                title = stringResource(R.string.import_from_newpipe),
                description = stringResource(R.string.import_from_newpipe_desc),
                onClick = onImportNewPipe
            )
        }
        item {
            ImportCard(
                painter = painterResource(id = R.drawable.ic_youtube),
                title = stringResource(R.string.import_from_youtube),
                description = stringResource(R.string.import_from_youtube_desc),
                onClick = onImportYouTube
            )
        }
        item {
            ImportCard(
                painter = painterResource(id = R.drawable.ic_libretube),
                title = stringResource(R.string.import_from_libretube),
                description = stringResource(R.string.import_from_libretube_desc),
                onClick = onImportLibreTube
            )
        }

        item { ImportSectionLabel(stringResource(R.string.import_history_section_title)) }
        item {
            ImportCard(
                painter = painterResource(id = R.drawable.ic_youtube),
                title = stringResource(R.string.import_yt_takeout_all),
                description = stringResource(R.string.import_yt_takeout_all_desc),
                onClick = onImportYouTubeTakeout
            )
        }
        item {
            ImportCard(
                painter = painterResource(id = R.drawable.ic_youtube),
                title = stringResource(R.string.import_yt_watch_history),
                description = stringResource(R.string.import_yt_watch_history_desc),
                onClick = onImportYouTubeHistory
            )
        }
        item {
            ImportCard(
                painter = rememberVectorPainter(Icons.Outlined.History),
                title = stringResource(R.string.import_freetube_history),
                description = stringResource(R.string.import_freetube_history_desc),
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onImportFreeTubeHistory
            )
        }
        item {
            ImportCard(
                painter = painterResource(id = R.drawable.ic_newpipe),
                title = stringResource(R.string.import_newpipe_history),
                description = stringResource(R.string.import_newpipe_history_desc),
                onClick = onImportNewPipeHistory
            )
        }

        item { ImportSectionLabel(stringResource(R.string.import_playlists_section_title)) }
        item {
            ImportCard(
                painter = painterResource(id = R.drawable.ic_newpipe),
                title = stringResource(R.string.import_newpipe_playlists),
                description = stringResource(R.string.import_newpipe_playlists_desc),
                onClick = onImportNewPipePlaylists
            )
        }
        item {
            ImportCard(
                painter = painterResource(id = R.drawable.ic_libretube),
                title = stringResource(R.string.import_libretube_playlists),
                description = stringResource(R.string.import_libretube_playlists_desc),
                onClick = onImportLibreTubePlaylists
            )
        }
        item {
            ImportCard(
                painter = painterResource(id = R.drawable.ic_youtube),
                title = stringResource(R.string.import_yt_playlist),
                description = stringResource(R.string.import_yt_playlist_desc),
                onClick = onImportYouTubePlaylist
            )
        }

        item { ImportSectionLabel(stringResource(R.string.import_music_apps_section_title)) }
        item {
            ImportCard(
                painter = painterResource(id = R.drawable.ic_metrolist),
                title = stringResource(R.string.import_from_metrolist),
                description = stringResource(R.string.import_from_metrolist_desc),
                onClick = onImportMetrolist
            )
        }
        item {
            ImportCard(
                painter = rememberVectorPainter(Icons.Outlined.QueueMusic),
                title = stringResource(R.string.import_yt_music_playlist),
                description = stringResource(R.string.import_yt_music_playlist_desc),
                iconTint = MaterialTheme.colorScheme.primary,
                onClick = onImportYouTubeMusicPlaylist
            )
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ImportSectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun ImportCard(
    painter: Painter,
    title: String,
    description: String,
    iconTint: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = iconTint
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
            Icon(
                Icons.Outlined.FileDownload,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .alpha(0.45f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
