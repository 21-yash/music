package com.musicsportsapp.features.sports.data.api

import com.musicsportsapp.data.remote.dto.ApiResponse
import com.musicsportsapp.features.sports.data.dto.MatchesResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface SportsApi {
    
    @GET("sports/cricket/matches")
    suspend fun getMatches(
        @Query("filter") filter: String
    ): ApiResponse<MatchesResponseDto>

    // We will add Match Details, Scorecard, and Squads later as we build those UI screens.
}
