package com.flow.youtube.notification

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
                    "It's bedtime! 😴",
                    "Time to wind down based on your schedule."
                )
            }
            "break" -> {
                 NotificationHelper.showReminderNotification(
                    context,
                    "Take a break! ☕",
                    "You've been watching for a while."
                )
            }
        }
    }
}
