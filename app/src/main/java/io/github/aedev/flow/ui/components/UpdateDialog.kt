package io.github.aedev.flow.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.aedev.flow.R
import io.github.aedev.flow.utils.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val displayedChangelog by
        produceState(
            initialValue = updateInfo.changelog,
            key1 = updateInfo.changelog,
            key2 = updateInfo.version,
        ) {
            value = withContext(Dispatchers.IO) { resolveDisplayedChangelog(context, updateInfo) }
        }
    val backdropInteractionSource = remember { MutableInteractionSource() }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                        .clickable(
                            interactionSource = backdropInteractionSource,
                            indication = null,
                            onClick = onDismiss,
                        ),
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = BottomSheetDefaults.ExpandedShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp, bottom = 24.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_notification_logo),
                                contentDescription = stringResource(R.string.update_flow),
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = stringResource(R.string.new_update_available),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.ui_version, updateInfo.version),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = stringResource(R.string.release_notes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )

                        MarkdownChangelogText(markdown = displayedChangelog)
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(52.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.maybe_later),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        Button(
                            onClick = onUpdate,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(52.dp),
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.update_flow),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun resolveDisplayedChangelog(
    context: Context,
    updateInfo: UpdateInfo,
): String {
    if (!updateInfo.version.contains("1.4.0") && updateInfo.changelog.isNotBlank()) {
        return updateInfo.changelog
    }

    val versionFile = if (updateInfo.version.startsWith("v")) updateInfo.version else "v${updateInfo.version}"
    val exact =
        runCatching {
            context.assets
                .open("changelog/$versionFile.txt")
                .bufferedReader()
                .use { it.readText() }
        }.getOrNull()
    if (!exact.isNullOrBlank()) return exact

    return runCatching {
        val latestFile =
            context.assets
                .list("changelog")
                .orEmpty()
                .filter { it.endsWith(".txt") }
                .maxOrNull()
                ?: return@runCatching updateInfo.changelog
        context.assets
            .open("changelog/$latestFile")
            .bufferedReader()
            .use { it.readText() }
    }.getOrDefault(updateInfo.changelog)
}

@Composable
fun MarkdownChangelogText(markdown: String) {
    val styledText =
        remember(markdown) {
            buildAnnotatedString {
                markdown.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("#") -> {
                            val text = trimmed.trimStart('#').trim()
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("• $text")
                            }
                            append('\n')
                        }

                        trimmed.startsWith("-") || trimmed.startsWith("*") -> {
                            append(" • ")
                            parseBold(trimmed.substring(1).trim())
                            append('\n')
                        }

                        trimmed.isNotEmpty() -> {
                            parseBold(trimmed)
                            append('\n')
                        }
                    }
                }
            }
        }

    Text(
        text = styledText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

fun AnnotatedString.Builder.parseBold(text: String) {
    text.split("**").forEachIndexed { index, part ->
        if (index % 2 == 1) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(part)
            }
        } else {
            append(part)
        }
    }
}
