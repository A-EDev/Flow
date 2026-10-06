package io.github.aedev.flow.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.aedev.flow.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val type = intent.getStringExtra("type") ?: return
        val appContext = context.applicationContext
        // Posting the notification reads DataStore, so it must not run on the receiver's main thread.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (type) {
                    "bedtime" -> {
                        NotificationHelper.showReminderNotification(
                            appContext,
                            appContext.getString(R.string.reminder_bedtime_message),
                            appContext.getString(R.string.reminder_bedtime_message_detail),
                        )
                    }

                    "break" -> {
                        NotificationHelper.showReminderNotification(
                            appContext,
                            appContext.getString(R.string.reminder_break_message),
                            appContext.getString(R.string.reminder_break_message_detail),
                        )

                        // Reschedule if it's a repeating break reminder
                        val frequency = intent.getIntExtra("frequency", -1)
                        if (frequency > 0) {
                            ReminderManager.scheduleBreakReminder(appContext, frequency)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
