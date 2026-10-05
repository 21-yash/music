package com.musicsportsapp.features.sports.data.api

import com.musicsportsapp.data.remote.dto.ApiResponse
import com.musicsportsapp.features.sports.data.dto.*
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Path

interface SportsApi {
    
    @GET("sports/cricket/matches")
    suspend fun getMatches(
        @Query("filter") filter: String
    ): ApiResponse<MatchesResponseDto>

    @GET("sports/cricket/matches/{matchId}")
    suspend fun getMatchDetails(
        @Path("matchId") matchId: String
    ): ApiResponse<MatchDetailsResponseDto>

    @GET("sports/cricket/matches/{matchId}/scorecard")
    suspend fun getScorecard(
        @Path("matchId") matchId: String
    ): ApiResponse<ScorecardResponseDto>

    @GET("sports/cricket/matches/{matchId}/squads")
    suspend fun getSquads(
        @Path("matchId") matchId: String
    ): ApiResponse<SquadResponseDto>
}
