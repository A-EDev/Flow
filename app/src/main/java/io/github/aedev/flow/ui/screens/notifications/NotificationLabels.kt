package io.github.aedev.flow.ui.screens.notifications

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.aedev.flow.R
import io.github.aedev.flow.utils.formatYouTubeRelativeTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@get:StringRes
internal val NotificationBucket.titleRes: Int
    get() =
        when (this) {
            NotificationBucket.NEW -> R.string.notifications_group_new
            NotificationBucket.TODAY -> R.string.time_today
            NotificationBucket.YESTERDAY -> R.string.time_yesterday
            NotificationBucket.LAST_WEEK -> R.string.notifications_group_last_week
            NotificationBucket.EARLIER -> R.string.time_earlier
        }

/** Today's date, refreshed whenever the screen resumes so an inbox left open overnight regroups. */
@Composable
internal fun rememberToday(zone: ZoneId): LocalDate {
    var today by remember(zone) { mutableStateOf(LocalDate.now(zone)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = LocalDate.now(zone) }
    return today
}

/** The time as a row shows it, in the device's locale and 12/24-hour setting. */
@Composable
internal fun NotificationTime.label(): String {
    val context = LocalContext.current
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    return when (this) {
        is NotificationTime.Relative -> {
            formatYouTubeRelativeTime(timestampMs, locale = locale)
        }

        is NotificationTime.TimeOfDay -> {
            val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
            time.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
        }

        is NotificationTime.Weekday -> {
            day.getDisplayName(TextStyle.FULL, locale)
        }

        is NotificationTime.Date -> {
            date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        }
    }
}
