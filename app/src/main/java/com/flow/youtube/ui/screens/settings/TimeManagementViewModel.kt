package com.flow.youtube.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.LocalDataManager
import com.flow.youtube.data.local.ViewHistory
import com.flow.youtube.notification.ReminderManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class TimeManagementViewModel @Inject constructor(
    private val viewHistory: ViewHistory,
    private val localDataManager: LocalDataManager,
    @ApplicationContext private val applicationContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(TimeManagementState())
    val uiState: StateFlow<TimeManagementState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Load Reminders Flags
            launch {
                localDataManager.bedtimeReminder.collect { enabled ->
                    _uiState.value = _uiState.value.copy(bedtimeReminderEnabled = enabled)
                }
            }
            launch {
                localDataManager.breakReminder.collect { enabled ->
                    _uiState.value = _uiState.value.copy(breakReminderEnabled = enabled)
                }
            }
            
            // Load Schedule & Frequency
            launch {
                 localDataManager.bedtimeStartHour.collect { h -> _uiState.value = _uiState.value.copy(bedtimeStartHour = h) }
            }
            launch {
                 localDataManager.bedtimeStartMinute.collect { m -> _uiState.value = _uiState.value.copy(bedtimeStartMinute = m) }
            }
            launch {
                 localDataManager.bedtimeEndHour.collect { h -> _uiState.value = _uiState.value.copy(bedtimeEndHour = h) }
            }
             launch {
                 localDataManager.bedtimeEndMinute.collect { m -> _uiState.value = _uiState.value.copy(bedtimeEndMinute = m) }
            }
            launch {
                localDataManager.breakFrequency.collect { f -> _uiState.value = _uiState.value.copy(breakFrequencyMinutes = f) }
            }

            // Load History Stats
            loadHistoryStats()
        }
    }

    private suspend fun loadHistoryStats() {
        val history = viewHistory.getAllHistory().first()
        
        // Calculate daily stats for the last 7 days
        val calendar = Calendar.getInstance()
        // Reset to midnight
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        val todayStart = calendar.timeInMillis
        
        // Map of Day Offset (0 = Today, 1 = Yesterday, etc) -> Duration in millis
        val dailyDurations = LongArray(7) { 0 }
        
        history.forEach { entry ->
            val entryCalendar = Calendar.getInstance()
            entryCalendar.timeInMillis = entry.timestamp
            entryCalendar.set(Calendar.HOUR_OF_DAY, 0)
            entryCalendar.set(Calendar.MINUTE, 0)
            entryCalendar.set(Calendar.SECOND, 0)
            entryCalendar.set(Calendar.MILLISECOND, 0)
            val entryDayStart = entryCalendar.timeInMillis
            
            val daysDiff = TimeUnit.MILLISECONDS.toDays(todayStart - entryDayStart).toInt()
            
            if (daysDiff in 0..6) {
                // It's within the last 7 days (0 is today, 6 is 6 days ago)
                dailyDurations[daysDiff] += entry.position
            }
        }
        
        val todayDuration = dailyDurations[0]
        val totalLast7Days = dailyDurations.sum()
        val avgDaily = totalLast7Days / 7
        
        // Prepare chart data (reverse order: 6 days ago -> Today)
        val chartData = (6 downTo 0).map { offset ->
             val dayCalendar = Calendar.getInstance()
             dayCalendar.add(Calendar.DAY_OF_YEAR, -offset)
             val dayName = SimpleDateFormat("EEE", Locale.getDefault()).format(dayCalendar.time)
             val durationMillis = dailyDurations[offset]
             
             DailyStat(
                 dayName = dayName,
                 durationH = durationMillis.toFloat() / (1000 * 60 * 60), // Hours
                 isToday = offset == 0
             )
        }

        _uiState.value = _uiState.value.copy(
            todayWatchTime = formatDuration(todayDuration),
            last7DaysWatchTime = formatDuration(totalLast7Days),
            dailyAverage = formatDuration(avgDaily) + " daily average",
            chartData = chartData
        )
    }

    private fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return if (hours > 0) "${hours} hr ${minutes} min" else "${minutes} min"
    }

    fun toggleBedtimeReminder(enabled: Boolean) {
        viewModelScope.launch {
            localDataManager.setBedtimeReminder(enabled)
            updateBedtimeAlarm(enabled)
        }
    }

    fun updateBedtimeSchedule(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        viewModelScope.launch {
            localDataManager.setBedtimeSchedule(startHour, startMinute, endHour, endMinute)
            // If enabled, reschedule
            if (uiState.value.bedtimeReminderEnabled) {
                updateBedtimeAlarm(true, startHour, startMinute)
            }
        }
    }
    
    private fun updateBedtimeAlarm(enabled: Boolean, hour: Int? = null, minute: Int? = null) {
        try {
            if (enabled) {
                val h = hour ?: uiState.value.bedtimeStartHour
                val m = minute ?: uiState.value.bedtimeStartMinute
                ReminderManager.scheduleBedtimeReminder(applicationContext, h, m)
            } else {
                ReminderManager.cancelBedtimeReminder(applicationContext)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleBreakReminder(enabled: Boolean) {
        viewModelScope.launch {
            localDataManager.setBreakReminder(enabled)
        }
    }
    
    fun updateBreakFrequency(minutes: Int) {
        viewModelScope.launch {
            localDataManager.setBreakFrequency(minutes)
        }
    }
}

data class TimeManagementState(
    val dailyAverage: String = "0 min daily average",
    val todayWatchTime: String = "0 min",
    val last7DaysWatchTime: String = "0 min",
    val trend: String = "",
    val chartData: List<DailyStat> = emptyList(),
    val bedtimeReminderEnabled: Boolean = false,
    val breakReminderEnabled: Boolean = false,
    val bedtimeStartHour: Int = 23,
    val bedtimeStartMinute: Int = 0,
    val bedtimeEndHour: Int = 7,
    val bedtimeEndMinute: Int = 0,
    val breakFrequencyMinutes: Int = 30
)

data class DailyStat(
    val dayName: String,
    val durationH: Float,
    val isToday: Boolean
)
