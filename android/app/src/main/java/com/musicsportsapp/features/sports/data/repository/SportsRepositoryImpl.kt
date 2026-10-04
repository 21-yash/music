package com.musicsportsapp.features.sports.data.repository

import com.musicsportsapp.data.remote.safeApiCall
import com.musicsportsapp.core.domain.AppResult
import com.musicsportsapp.features.sports.data.api.SportsApi
import com.musicsportsapp.features.sports.domain.model.MatchSummary
import com.musicsportsapp.features.sports.domain.repository.SportsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SportsRepositoryImpl @Inject constructor(
    private val api: SportsApi
) : SportsRepository {

    override fun getMatches(filter: String): Flow<Result<List<MatchSummary>>> = flow {
        val result = safeApiCall { api.getMatches(filter) }
        
        when (result) {
            is AppResult.Success -> {
                val domainMatches = result.data.matches.map { it.toDomain() }
                emit(Result.success(domainMatches))
            }
            is AppResult.Error -> {
                emit(Result.failure(Exception(result.error.message)))
            }
        }
    }
}
