package io.github.aedev.flow.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.backup.BackupOperation
import io.github.aedev.flow.data.backup.ImportKind
import io.github.aedev.flow.data.backup.ImportSource
import io.github.aedev.flow.ui.components.shared.FlowProgressBanner

private val TileSpacing = 10.dp
private val TilePadding = 16.dp
private val TileContentSpacing = 12.dp
private val GlyphSize = 40.dp
private val GlyphIconSize = 24.dp
private val DoneSize = 24.dp
private val DoneIconSize = 16.dp
private val DoneInset = 10.dp
private val FootnotePadding = 16.dp
private val WideGridWidth = 600.dp
private const val NARROW_COLUMNS = 2
private const val WIDE_COLUMNS = 3

/**
 * Import, grouped by the app a person is moving from. A tile opens that app's imports in a sheet;
 * a tile gets a check once one of its imports has finished.
 */
@Composable
internal fun ImportStep(
    header: LazyListScope.() -> Unit,
    importOperation: BackupOperation,
    importedSources: Set<ImportSource>,
    onImport: (ImportKind) -> Unit,
    contentPadding: PaddingValues,
) {
    var openSource by rememberSaveable { mutableStateOf<ImportSource?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= WideGridWidth) WIDE_COLUMNS else NARROW_COLUMNS
        val rows = ImportSource.entries.chunked(columns)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(TileSpacing),
        ) {
            header()
            (importOperation as? BackupOperation.Running)?.let { running ->
                item(key = "progress") {
                    FlowProgressBanner(
                        headline = running.headline,
                        current = running.current,
                        total = running.total,
                        note = stringResource(R.string.import_running_background),
                    )
                }
            }
            items(rows, key = { row -> row.first().name }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
                    row.forEach { source ->
                        SourceTile(
                            source = source,
                            imported = source in importedSources,
                            onClick = { openSource = source },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            item(key = "footnote") {
                Text(
                    text = stringResource(R.string.onboarding_import_footnote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(FootnotePadding),
                )
            }
        }
    }

    openSource?.let { source ->
        ImportSourceSheet(
            source = source,
            onImport = { kind ->
                openSource = null
                onImport(kind)
            },
            onDismiss = { openSource = null },
        )
    }
}

@Composable
private fun SourceTile(
    source: ImportSource,
    imported: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Box {
            Column(
                modifier = Modifier.fillMaxWidth().padding(TilePadding),
                verticalArrangement = Arrangement.spacedBy(TileContentSpacing),
            ) {
                SourceGlyph(source)
                Column {
                    Text(
                        text = stringResource(source.shortTitleRes),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.onboarding_import_count, source.kinds.size, source.kinds.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (imported) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(DoneInset).size(DoneSize),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.onboarding_import_done),
                            modifier = Modifier.size(DoneIconSize),
                        )
                    }
                }
            }
        }
    }
}

/** The source app's own icon on a neutral disc; brand icons keep their colours. */
@Composable
private fun SourceGlyph(source: ImportSource) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(GlyphSize)) {
        Box(contentAlignment = Alignment.Center) {
            val iconRes = source.iconRes
            if (iconRes != null) {
                // Metrolist's icon is light grey artwork that vanishes on a light disc; it reads as a silhouette.
                val tint = if (source == ImportSource.METROLIST) MaterialTheme.colorScheme.onSurface else Color.Unspecified
                Icon(painterResource(iconRes), contentDescription = null, tint = tint, modifier = Modifier.size(GlyphIconSize))
            } else {
                Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(GlyphIconSize))
            }
        }
    }
}
