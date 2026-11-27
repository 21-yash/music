package com.synq.features.sports.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val CHANNEL_MATCH_LIFECYCLE = "match_lifecycle"
    const val CHANNEL_MATCH_EVENTS = "match_events"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val lifecycleChannel = NotificationChannel(
            CHANNEL_MATCH_LIFECYCLE,
            "Match Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Match starting soon, first ball, toss, breaks, stumps & results"
        }

        val eventsChannel = NotificationChannel(
            CHANNEL_MATCH_EVENTS,
            "Milestones",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Batter 50 & 100 milestones for your followed cricket teams"
        }

        manager.createNotificationChannel(lifecycleChannel)
        manager.createNotificationChannel(eventsChannel)
    }
}
