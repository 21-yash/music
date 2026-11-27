package com.synq.data.remote.api

import com.synq.data.remote.dto.ApiResponse
import com.synq.data.remote.dto.NotificationPreferencesResponse
import com.synq.data.remote.dto.RegisterFcmTokenRequest
import com.synq.data.remote.dto.RegisterFcmTokenResponse
import com.synq.data.remote.dto.UnregisterFcmTokenRequest
import com.synq.data.remote.dto.UnregisterFcmTokenResponse
import com.synq.data.remote.dto.UpdateNotificationPreferencesRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface NotificationApi {

    @GET("notifications/preferences")
    suspend fun getPreferences(): ApiResponse<NotificationPreferencesResponse>

    @PUT("notifications/preferences")
    suspend fun updatePreferences(
        @Body request: UpdateNotificationPreferencesRequest,
    ): ApiResponse<NotificationPreferencesResponse>

    @PUT("notifications/token")
    suspend fun registerToken(
        @Body request: RegisterFcmTokenRequest,
    ): ApiResponse<RegisterFcmTokenResponse>

    @POST("notifications/token/unregister")
    suspend fun unregisterToken(
        @Body request: UnregisterFcmTokenRequest,
    ): ApiResponse<UnregisterFcmTokenResponse>
}
