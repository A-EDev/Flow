package io.github.aedev.flow.utils

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

suspend fun Clipboard.copyPlainText(
    label: String,
    text: String,
    sensitive: Boolean = false,
) {
    val clip = ClipData.newPlainText(label, text)
    if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras =
            PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
    }
    setClipEntry(ClipEntry(clip))
}

suspend fun Clipboard.readPlainText(context: Context): String? {
    val clip = getClipEntry()?.clipData ?: return null
    if (clip.itemCount == 0) return null
    return clip
        .getItemAt(0)
        .coerceToText(context)
        .toString()
        .takeIf { it.isNotBlank() }
}
