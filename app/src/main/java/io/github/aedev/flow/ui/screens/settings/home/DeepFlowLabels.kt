package io.github.aedev.flow.ui.screens.settings.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.DEEP_FLOW_NEVER_EXPIRES_HOURS
import java.time.Duration
import java.time.Instant

/** How long Deep Flow can stay on before it turns itself off. */
internal val DeepFlowDurations = listOf(DEEP_FLOW_NEVER_EXPIRES_HOURS, 1, 2, 4, 6, 8, 12, 24)

@Composable
internal fun deepFlowDurationLabel(hours: Int): String =
    when (hours) {
        DEEP_FLOW_NEVER_EXPIRES_HOURS -> stringResource(R.string.deep_flow_duration_never)
        1 -> stringResource(R.string.deep_flow_duration_1h)
        2 -> stringResource(R.string.deep_flow_duration_2h)
        4 -> stringResource(R.string.deep_flow_duration_4h)
        6 -> stringResource(R.string.deep_flow_duration_6h)
        8 -> stringResource(R.string.deep_flow_duration_8h)
        12 -> stringResource(R.string.deep_flow_duration_12h)
        24 -> stringResource(R.string.deep_flow_duration_24h)
        else -> pluralStringResource(R.plurals.deep_flow_duration_hours, hours, hours)
    }

/** The Deep Flow row's supporting text while it is on, or null while it is off. */
@Composable
internal fun deepFlowStatus(state: DeepFlowState): String? {
    if (!state.active) return null
    if (state.expireHours == DEEP_FLOW_NEVER_EXPIRES_HOURS) return stringResource(R.string.deep_flow_active_until_disabled)
    if (state.activatedAt == 0L) return null
    val remaining =
        Duration.between(
            Instant.now(),
            Instant.ofEpochMilli(state.activatedAt).plus(Duration.ofHours(state.expireHours.toLong())),
        )
    if (remaining.isNegative || remaining.isZero) return null
    val context = LocalContext.current
    val minutes = remaining.toMinutes()
    val label =
        if (minutes < MINUTES_PER_HOUR) {
            context.getString(R.string.duration_minutes_short, minutes)
        } else {
            context.getString(R.string.duration_hours_minutes_short, minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
        }
    return stringResource(R.string.deep_flow_expires_in, label)
}

private const val MINUTES_PER_HOUR = 60
