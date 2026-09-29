package io.github.aedev.flow.ui.screens.settings.about

import android.content.Context
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.BuildConfig
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.shared.FlowSegmentedGap
import io.github.aedev.flow.ui.components.shared.FlowSheetHeader
import io.github.aedev.flow.ui.components.shared.flowSegmentShape
import io.github.aedev.flow.ui.components.shared.rememberFlowSheetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val TAG = "ChangelogSheet"
private const val CHANGELOG_DIR = "changelog"
private const val RELEASES_URL = "https://github.com/A-EDev/Flow/releases"

private val ListPadding = 16.dp
private val RowPadding = 16.dp
private val HeaderVerticalPadding = 14.dp
private val SectionTopPadding = 12.dp
private val LineTopPadding = 8.dp
private val ItemSpacing = 8.dp
private val BodyEndHeight = 16.dp
private val BulletSize = 6.dp
private val BulletTopOffset = 8.dp
private val BadgePadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
private val StateHeight = 240.dp

/**
 * Every release note bundled with the app, newest first, one expandable group per version. The
 * installed version opens expanded; the rest stay folded to their version and date.
 *
 * Each heading and change is its own lazy item rather than one block per release, so opening a
 * release composes only the lines on screen and never re-measures the whole note while it animates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChangelogSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val releases by produceState<List<ChangelogRelease>?>(null) {
        value = withContext(Dispatchers.IO) { loadChangelogs(context) }
    }
    val installed = remember { versionOf(BuildConfig.VERSION_NAME.substringBefore('-')) }
    var expanded by rememberSaveable { mutableStateOf<List<String>?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        FlowSheetHeader(
            title = stringResource(R.string.about_changelog),
            subtitle = stringResource(R.string.whats_new_in_flow),
            onClose = onDismiss,
            showDragHandle = false,
        )
        val loaded = releases
        when {
            loaded == null -> {
                Box(Modifier.fillMaxWidth().height(StateHeight), contentAlignment = Alignment.Center) { FlowLoadingIndicator() }
            }

            loaded.isEmpty() -> {
                Box(Modifier.fillMaxWidth().height(StateHeight)) {
                    FlowEmptyState(title = stringResource(R.string.no_changelog_found_message))
                }
            }

            else -> {
                val open =
                    expanded
                        ?: listOf(
                            loaded.firstOrNull { compareVersions(versionOf(it.version), installed) == 0 }?.version
                                ?: loaded.first().version,
                        )
                val shapes = loaded.indices.map { flowSegmentShape(index = it, count = loaded.size) }
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(),
                    contentPadding =
                        PaddingValues(
                            start = ListPadding,
                            end = ListPadding,
                            top = ListPadding,
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + ListPadding,
                        ),
                ) {
                    loaded.forEachIndexed { index, release ->
                        releaseItems(
                            release = release,
                            first = index == 0,
                            installed = compareVersions(versionOf(release.version), installed) == 0,
                            expanded = release.version in open,
                            shape = shapes[index],
                            onToggle = {
                                expanded = if (release.version in open) open - release.version else open + release.version
                            },
                        )
                    }
                    item(key = "all_releases") {
                        FilledTonalButton(
                            onClick = { runCatching { uriHandler.openUri(RELEASES_URL) } },
                            modifier = Modifier.fillMaxWidth().padding(top = ListPadding),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_github),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Box(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.settings_changelog_all_releases))
                        }
                    }
                }
            }
        }
    }
}

/** A release as lazy items: its header, then while [expanded] each heading and change in turn. */
private fun LazyListScope.releaseItems(
    release: ChangelogRelease,
    first: Boolean,
    installed: Boolean,
    expanded: Boolean,
    shape: Shape,
    onToggle: () -> Unit,
) {
    val version = release.version
    item(key = "header:$version") {
        ReleaseHeader(
            release = release,
            installed = installed,
            expanded = expanded,
            shape = if (expanded) shape.withoutBottomCorners() else shape,
            onToggle = onToggle,
            modifier = Modifier.animateItem().padding(top = if (first) 0.dp else FlowSegmentedGap),
        )
    }
    if (!expanded) return
    release.sections.forEachIndexed { sectionIndex, section ->
        if (section.title.isNotEmpty()) {
            item(key = "section:$version:$sectionIndex") {
                BodyItem(Modifier.padding(top = if (sectionIndex == 0) 0.dp else SectionTopPadding)) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        section.items.forEachIndexed { lineIndex, line ->
            item(key = "line:$version:$sectionIndex:$lineIndex") {
                BodyItem(Modifier.padding(top = LineTopPadding)) { ChangeLine(line) }
            }
        }
    }
    item(key = "end:$version") {
        Box(
            Modifier
                .animateItem()
                .fillMaxWidth()
                .height(BodyEndHeight)
                .clip(shape.withoutTopCorners())
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
    }
}

/** One line of an open release, on the release's surface colour so the lines read as one card. */
@Composable
private fun LazyItemScope.BodyItem(
    innerModifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .animateItem()
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RectangleShape)
            .padding(horizontal = RowPadding)
            .then(innerModifier),
    ) { content() }
}

@Composable
private fun ReleaseHeader(
    release: ChangelogRelease,
    installed: Boolean,
    expanded: Boolean,
    shape: Shape,
    onToggle: () -> Unit,
    modifier: Modifier,
) {
    val chevronTurn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "chevron",
    )
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val toggleLabel = stringResource(if (expanded) R.string.settings_changelog_collapse else R.string.settings_changelog_expand)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClickLabel = toggleLabel, role = Role.Button, onClick = onToggle)
                .padding(horizontal = RowPadding, vertical = HeaderVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_changelog_version, release.version),
                style = MaterialTheme.typography.titleMedium,
            )
            release.date?.let {
                Text(
                    text = it.format(dateFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (installed) ReleaseBadge(stringResource(R.string.settings_changelog_installed), highlighted = true)
        if (release.preRelease) ReleaseBadge(stringResource(R.string.settings_changelog_prerelease), highlighted = false)
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { rotationZ = chevronTurn },
        )
    }
}

@Composable
private fun ChangeLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(ItemSpacing)) {
        Box(
            Modifier
                .padding(top = BulletTopOffset)
                .size(BulletSize)
                .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ReleaseBadge(
    label: String,
    highlighted: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(BadgePadding))
    }
}

private fun Shape.withoutBottomCorners(): Shape =
    (this as? CornerBasedShape)?.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize) ?: RectangleShape

private fun Shape.withoutTopCorners(): Shape =
    (this as? CornerBasedShape)?.copy(topStart = ZeroCornerSize, topEnd = ZeroCornerSize) ?: RectangleShape

private fun loadChangelogs(context: Context): List<ChangelogRelease> =
    runCatching {
        sortedChangelogs(
            context.assets
                .list(CHANGELOG_DIR)
                .orEmpty()
                .toList(),
        ).mapNotNull { file ->
            runCatching {
                val text =
                    context.assets
                        .open("$CHANGELOG_DIR/$file")
                        .bufferedReader()
                        .use { it.readText() }
                parseChangelog(text, fallbackVersion = file.removePrefix("v").removeSuffix(".txt"))
            }.onFailure { Log.w(TAG, "Changelog $file could not be read", it) }.getOrNull()
        }
    }.onFailure { Log.w(TAG, "Changelogs could not be listed", it) }.getOrDefault(emptyList())
