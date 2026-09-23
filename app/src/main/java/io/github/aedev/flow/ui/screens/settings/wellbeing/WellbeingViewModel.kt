package io.github.aedev.flow.ui.screens.settings.wellbeing

import android.content.Context
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.LocalDataManager
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.notification.ReminderManager
import io.github.aedev.flow.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class WellbeingViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val viewHistory: ViewHistory,
        private val localData: LocalDataManager,
    ) : SettingsViewModel() {
        /** Measured once per visit: history only grows while the page is closed, not while it is read. */
        val summary =
            flow {
                val records = viewHistory.getAllHistory().first().map { WatchRecord(it.timestamp, it.position) }
                emit(summarizeWatchTime(records, LocalDate.now(), ZoneId.systemDefault()))
            }.flowOn(Dispatchers.Default)
                .asState(null)

        val bedtimeEnabled = localData.bedtimeReminder.asState(false)
        val breakEnabled = localData.breakReminder.asState(false)
        val bedtimeStart = timeOf(localData.bedtimeStartHour, localData.bedtimeStartMinute, DEFAULT_BEDTIME)
        val bedtimeEnd = timeOf(localData.bedtimeEndHour, localData.bedtimeEndMinute, DEFAULT_WAKE)
        val breakMinutes = localData.breakFrequency.asState(DEFAULT_BREAK_MINUTES)

        fun setBedtimeEnabled(enabled: Boolean) =
            write {
                localData.setBedtimeReminder(enabled)
                scheduleBedtime(enabled, bedtimeStart.value)
            }

        fun setBedtimeStart(time: LocalTime) =
            write {
                val end = bedtimeEnd.value
                localData.setBedtimeSchedule(time.hour, time.minute, end.hour, end.minute)
                if (bedtimeEnabled.value) scheduleBedtime(true, time)
            }

        fun setBedtimeEnd(time: LocalTime) =
            write {
                val start = bedtimeStart.value
                localData.setBedtimeSchedule(start.hour, start.minute, time.hour, time.minute)
            }

        fun setBreakEnabled(enabled: Boolean) =
            write {
                localData.setBreakReminder(enabled)
                scheduleBreak(enabled, breakMinutes.value)
            }

        fun setBreakMinutes(minutes: Int) =
            write {
                localData.setBreakFrequency(minutes)
                if (breakEnabled.value) scheduleBreak(true, minutes)
            }

        private fun timeOf(
            hour: Flow<Int>,
            minute: Flow<Int>,
            initial: LocalTime,
        ) = combine(hour, minute) { h, m -> LocalTime.of(h.coerceIn(0, 23), m.coerceIn(0, 59)) }
            .asState(initial)

        private fun scheduleBedtime(
            enabled: Boolean,
            start: LocalTime,
        ) = runCatching {
            if (enabled) {
                ReminderManager.scheduleBedtimeReminder(context, start.hour, start.minute)
            } else {
                ReminderManager.cancelBedtimeReminder(context)
            }
        }.onFailure { Log.w(TAG, "Bedtime reminder could not be scheduled", it) }

        private fun scheduleBreak(
            enabled: Boolean,
            minutes: Int,
        ) = runCatching {
            if (enabled) ReminderManager.scheduleBreakReminder(context, minutes) else ReminderManager.cancelBreakReminder(context)
        }.onFailure { Log.w(TAG, "Break reminder could not be scheduled", it) }

        private companion object {
            const val TAG = "WellbeingViewModel"
            const val DEFAULT_BREAK_MINUTES = 30
            val DEFAULT_BEDTIME: LocalTime = LocalTime.of(23, 0)
            val DEFAULT_WAKE: LocalTime = LocalTime.of(7, 0)
        }
    }
