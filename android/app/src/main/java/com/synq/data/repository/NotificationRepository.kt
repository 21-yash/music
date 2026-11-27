package com.synq.data.repository

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.synq.core.domain.AppResult
import com.synq.data.local.NotificationPreferencesLocal
import com.synq.data.remote.api.NotificationApi
import com.synq.data.remote.dto.RegisterFcmTokenRequest
import com.synq.data.remote.dto.SportsNotificationPrefsDto
import com.synq.data.remote.dto.UnregisterFcmTokenRequest
import com.synq.data.remote.dto.UpdateNotificationPreferencesRequest
import com.synq.data.remote.safeApiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class NotificationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationApi: NotificationApi,
    private val localPrefs: NotificationPreferencesLocal,
) {
    val preferences: StateFlow<SportsNotificationPrefsDto> = localPrefs.preferences
    private val prefsMutex = Mutex()
    private var mutationSeq: Long = 0L

    suspend fun syncPreferences(): AppResult<SportsNotificationPrefsDto> {
        val seqAtStart = prefsMutex.withLock { mutationSeq }
        return when (val result = safeApiCall { notificationApi.getPreferences() }) {
            is AppResult.Success -> {
                prefsMutex.withLock {
                    // Never let an in-flight GET overwrite a newer local user toggle
                    if (mutationSeq == seqAtStart) {
                        localPrefs.savePreferences(result.data.preferences)
                    }
                    AppResult.Success(localPrefs.preferences.value)
                }
            }
            is AppResult.Error -> result
        }
    }

    suspend fun updatePreferences(
        request: UpdateNotificationPreferencesRequest,
    ): AppResult<SportsNotificationPrefsDto> {
        return prefsMutex.withLock {
            mutationSeq += 1
            val prev = localPrefs.preferences.value
            val optimistic = SportsNotificationPrefsDto(
                enabled = request.enabled ?: prev.enabled,
                matchStartingSoon = request.matchStartingSoon ?: prev.matchStartingSoon,
                matchStarted = request.matchStarted ?: prev.matchStarted,
                matchConcluded = request.matchConcluded ?: prev.matchConcluded,
                milestones = request.milestones ?: prev.milestones,
                matchUpdates = request.matchUpdates ?: prev.matchUpdates,
            )
            localPrefs.savePreferences(optimistic)

            when (val result = safeApiCall { notificationApi.updatePreferences(request) }) {
                is AppResult.Success -> {
                    localPrefs.savePreferences(result.data.preferences)
                    AppResult.Success(result.data.preferences)
                }
                is AppResult.Error -> {
                    // Roll back optimistic state if the server update failed
                    localPrefs.savePreferences(prev)
                    result
                }
            }
        }
    }

    suspend fun onSportsToggled(enabled: Boolean) {
        if (!enabled) {
            val wasEnabled = localPrefs.preferences.value.enabled
            localPrefs.setEnabledBeforeSportsOff(wasEnabled)
            if (wasEnabled) {
                updatePreferences(UpdateNotificationPreferencesRequest(enabled = false))
            }
        } else {
            val priorEnabled = localPrefs.getEnabledBeforeSportsOff()
            localPrefs.setEnabledBeforeSportsOff(null)
            if (priorEnabled == true && !localPrefs.preferences.value.enabled) {
                updatePreferences(UpdateNotificationPreferencesRequest(enabled = true))
            }
            syncFcmTokenIfAvailable()
        }
    }

    suspend fun syncFcmTokenIfAvailable() {
        val token = fetchFirebaseTokenSafely() ?: return
        uploadFcmToken(token)
    }

    suspend fun uploadFcmToken(token: String) {
        if (token.isBlank()) return
        val res = safeApiCall {
            notificationApi.registerToken(RegisterFcmTokenRequest(token = token))
        }
        if (res is AppResult.Success) {
            localPrefs.saveLastUploadedToken(token)
        }
    }

    suspend fun unregisterCurrentToken() {
        val token = localPrefs.getLastUploadedToken() ?: fetchFirebaseTokenSafely() ?: return
        safeApiCall {
            notificationApi.unregisterToken(UnregisterFcmTokenRequest(token = token))
        }
        localPrefs.saveLastUploadedToken(null)
    }

    private suspend fun fetchFirebaseTokenSafely(): String? {
        return runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) return null
            suspendCancellableCoroutine<String?> { cont ->
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { token ->
                        if (cont.isActive) cont.resume(token)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
            }
        }.getOrNull()
    }
}
