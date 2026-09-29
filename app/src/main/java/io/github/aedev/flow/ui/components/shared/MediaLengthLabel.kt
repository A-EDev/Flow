package io.github.aedev.flow.ui.components.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R

private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_MINUTE = 60L

/** The running time of a list of media, as "2 hr 5 min" or "38 min"; null under a minute. */
@Composable
fun mediaLengthLabel(totalSeconds: Long): String? {
    val hours = (totalSeconds / SECONDS_PER_HOUR).toInt()
    val minutes = ((totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE).toInt()
    return when {
        hours > 0 -> stringResource(R.string.duration_hours_minutes, hours, minutes)
        minutes > 0 -> pluralStringResource(R.plurals.duration_minutes, minutes, minutes)
        else -> null
    }
}
