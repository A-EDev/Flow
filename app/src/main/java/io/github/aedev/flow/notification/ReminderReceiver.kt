package io.github.aedev.flow.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("type") ?: return
        
        when (type) {
            "bedtime" -> {
                NotificationHelper.showReminderNotification(
                    context,
                    context.getString(io.github.aedev.flow.R.string.reminder_bedtime_title),
                    context.getString(io.github.aedev.flow.R.string.reminder_bedtime_message)
                )
            }
            "break" -> {
                 NotificationHelper.showReminderNotification(
                    context,
                    context.getString(io.github.aedev.flow.R.string.reminder_break_title),
                    context.getString(io.github.aedev.flow.R.string.reminder_break_message)
                )
                
                // Reschedule if it's a repeating break reminder
                val frequency = intent.getIntExtra("frequency", -1)
                if (frequency > 0) {
                    ReminderManager.scheduleBreakReminder(context, frequency)
                }
            }
        }
    }
}
