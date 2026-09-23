package io.github.aedev.flow.ui.screens.settings.about

import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
private val HeaderPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
private val BodyPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)
private val SectionSpacing = 16.dp
private val ItemSpacing = 8.dp
private val BulletSize = 6.dp
private val BulletTopOffset = 8.dp
private val BadgePadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
private val StateHeight = 240.dp

/**
 * Every release note bundled with the app, newest first, one expandable row per version. The
 * installed version opens expanded; the rest stay folded to their version and date.
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
                val openByDefault =
                    loaded.firstOrNull { compareVersions(versionOf(it.version), installed) == 0 }?.version
                        ?: loaded.first().version
                LazyColumn(
                    contentPadding =
                        PaddingValues(
                            start = ListPadding,
                            end = ListPadding,
                            top = ListPadding,
                            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + ListPadding,
                        ),
                    verticalArrangement = Arrangement.spacedBy(FlowSegmentedGap),
                ) {
                    itemsIndexed(loaded, key = { _, release -> release.version }) { index, release ->
                        ReleaseRow(
                            release = release,
                            installed = compareVersions(versionOf(release.version), installed) == 0,
                            initiallyExpanded = release.version == openByDefault,
                            shape = flowSegmentShape(index = index, count = loaded.size),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReleaseRow(
    release: ChangelogRelease,
    installed: Boolean,
    initiallyExpanded: Boolean,
    shape: Shape,
) {
    var expanded by rememberSaveable(release.version) { mutableStateOf(initiallyExpanded) }
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val chevronTurn by animateFloatAsState(if (expanded) 180f else 0f, effects, label = "chevron")
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val toggleLabel = stringResource(if (expanded) R.string.settings_changelog_collapse else R.string.settings_changelog_expand)

    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = toggleLabel, role = Role.Button) { expanded = !expanded }
                        .padding(HeaderPadding),
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
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(MaterialTheme.motionScheme.defaultEffectsSpec()) + fadeIn(effects),
                exit = shrinkVertically(MaterialTheme.motionScheme.defaultEffectsSpec()) + fadeOut(effects),
            ) {
                ReleaseBody(release)
            }
        }
    }
}

@Composable
private fun ReleaseBody(release: ChangelogRelease) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(BodyPadding),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        release.sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
                if (section.title.isNotEmpty()) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                section.items.forEach { item -> ChangeLine(item) }
            }
        }
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
