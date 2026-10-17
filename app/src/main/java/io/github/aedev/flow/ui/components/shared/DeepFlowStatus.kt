package io.github.aedev.flow.ui.components.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.DEEP_FLOW_NEVER_EXPIRES_HOURS
import java.time.Duration
import java.time.Instant

data class DeepFlowState(
    val active: Boolean = false,
    val activatedAt: Long = 0L,
    val expireHours: Int = DEFAULT_EXPIRE_HOURS,
    val saveToHistory: Boolean = false,
    val scrobble: Boolean = false,
    val showOnSearch: Boolean = false,
)

/** The Deep Flow row's supporting text while it is on, or null while it is off. */
@Composable
fun deepFlowStatus(state: DeepFlowState): String? {
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
private const val DEFAULT_EXPIRE_HOURS = 4
