package com.synq.features.sports.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.synq.MainActivity
import com.synq.R
import com.synq.data.local.NotificationPreferencesLocal
import com.synq.data.local.SportsPreferences
import com.synq.data.repository.NotificationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SynqFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var notificationRepository: NotificationRepository
    @Inject lateinit var sportsPreferences: SportsPreferences
    @Inject lateinit var notificationPrefsLocal: NotificationPreferencesLocal
    @Inject lateinit var activeMatchTracker: ActiveMatchTracker

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            notificationRepository.uploadFcmToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Never surface cricket alerts if the user disabled Sports or all notifications locally
        val localPrefs = notificationPrefsLocal.preferences.value
        if (!sportsPreferences.isSportsEnabled.value || !localPrefs.enabled) {
            return
        }

        val data = message.data
        val matchId = data["matchId"]?.takeIf { it.isNotBlank() } ?: return
        val eventType = data["eventType"] ?: ""

        // Respect per-category local toggles immediately too
        val categoryAllowed = when (eventType) {
            "match_starting_soon" -> localPrefs.matchStartingSoon
            "match_started" -> localPrefs.matchStarted
            "match_concluded" -> localPrefs.matchConcluded
            "milestone" -> localPrefs.milestones
            "match_update" -> localPrefs.matchUpdates
            else -> true
        }
        if (!categoryAllowed) return

        // Suppress push if user is currently watching that exact match live in-app
        if (activeMatchTracker.isViewingMatch(matchId)) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        NotificationChannels.ensureCreated(this)

        val channelId = if (data["channelId"] == NotificationChannels.CHANNEL_MATCH_EVENTS) {
            NotificationChannels.CHANNEL_MATCH_EVENTS
        } else {
            NotificationChannels.CHANNEL_MATCH_LIFECYCLE
        }

        val title = data["title"]?.takeIf { it.isNotBlank() }
            ?: message.notification?.title
            ?: "Match Update"
        val body = data["body"]?.takeIf { it.isNotBlank() }
            ?: message.notification?.body
            ?: ""

        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            this.data = Uri.parse("synq://sports/match/$matchId")
            putExtra(EXTRA_MATCH_ID, matchId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            matchId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(
                if (channelId == NotificationChannels.CHANNEL_MATCH_LIFECYCLE) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                }
            )
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notifId = when (eventType) {
            "milestone", "match_update" -> "$matchId:$eventType:${title.take(40)}:${body.take(40)}".hashCode()
            else -> "$matchId:$eventType".hashCode()
        }
        manager.notify(notifId, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val EXTRA_MATCH_ID = "matchId"
    }
}
