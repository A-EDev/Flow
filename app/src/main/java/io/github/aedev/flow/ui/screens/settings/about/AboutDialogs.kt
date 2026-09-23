package io.github.aedev.flow.ui.screens.settings.about

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.utils.copyPlainText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AboutDialogs"
private val TextDialogMaxHeight = 400.dp

/** A long asset text, read off the main thread, in a scrolling dialog. */
@Composable
private fun AssetTextDialog(
    title: String,
    read: (Context) -> String?,
    fallback: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val loading = stringResource(R.string.loading_ellipsis)
    val failed = stringResource(fallback)
    val text by produceState(loading) {
        value =
            withContext(Dispatchers.IO) {
                runCatching { read(context) }.onFailure { Log.w(TAG, "Asset could not be read", it) }.getOrNull()
            } ?: failed
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            SelectionContainer(Modifier.heightIn(max = TextDialogMaxHeight).verticalScroll(rememberScrollState())) {
                Text(text = text, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_ok)) } },
    )
}

@Composable
internal fun LicenseDialog(onDismiss: () -> Unit) =
    AssetTextDialog(
        title = stringResource(R.string.gnu_license_full_title),
        read = {
            it.assets
                .open("license.txt")
                .bufferedReader()
                .use { reader -> reader.readText() }
        },
        fallback = R.string.error_license_load,
        onDismiss = onDismiss,
    )

@Composable
internal fun ChangelogDialog(onDismiss: () -> Unit) {
    val noChangelog = stringResource(R.string.no_changelog_found_message)
    AssetTextDialog(
        title = stringResource(R.string.about_changelog),
        read = { context ->
            val latest =
                latestChangelog(
                    context.assets
                        .list("changelog")
                        .orEmpty()
                        .toList(),
                )
            latest?.let {
                context.assets
                    .open("changelog/$it")
                    .bufferedReader()
                    .use { reader -> reader.readText() }
            } ?: noChangelog
        },
        fallback = R.string.error_changelog_load,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun DeviceInfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.about_device_info)
    val info =
        remember {
            listOf(
                context.getString(R.string.manufacturer_label, Build.MANUFACTURER),
                context.getString(R.string.model_label, Build.MODEL),
                context.getString(R.string.board_label, Build.BOARD),
                context.getString(R.string.arch_label, Build.SUPPORTED_ABIS.joinToString(", ")),
                context.getString(R.string.android_sdk_label, Build.VERSION.SDK_INT.toString()),
                context.getString(R.string.os_label, Build.VERSION.RELEASE),
                context.getString(
                    R.string.density_label,
                    Resources
                        .getSystem()
                        .displayMetrics.density
                        .toString(),
                ),
            ).joinToString("\n")
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { SelectionContainer { Text(text = info, style = MaterialTheme.typography.bodyMedium) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_ok)) } },
        dismissButton = {
            TextButton(onClick = { scope.launch { clipboard.copyPlainText(title, info) } }) { Text(stringResource(R.string.btn_copy)) }
        },
    )
}
