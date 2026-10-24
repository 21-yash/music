package com.synq.data.repository

import com.synq.core.domain.AppResult
import com.synq.data.local.db.dao.FollowedTeamDao
import com.synq.data.local.db.entity.FollowedTeamEntity
import com.synq.data.remote.api.LibraryApi
import com.synq.data.remote.dto.FollowTeamRequest
import com.synq.data.remote.safeApiCall
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FollowedTeamRepository @Inject constructor(
    private val followedTeamDao: FollowedTeamDao,
    private val libraryApi: LibraryApi,
) {
    val followedTeams: Flow<List<FollowedTeamEntity>> = followedTeamDao.getAllFollowedTeams()

    fun isTeamFollowed(teamId: String): Flow<Boolean> = followedTeamDao.isTeamFollowed(teamId)

    suspend fun syncFollowedTeams(): AppResult<Unit> {
        return when (val result = safeApiCall { libraryApi.getFollowedTeams() }) {
            is AppResult.Success -> {
                val entities = result.data.map { dto ->
                    FollowedTeamEntity(
                        teamId = dto.teamId,
                        teamName = dto.teamName,
                        shortName = dto.shortName,
                        imageUrl = dto.imageUrl,
                        sport = dto.sport,
                    )
                }
                followedTeamDao.clearAll()
                if (entities.isNotEmpty()) {
                    followedTeamDao.insertAll(entities)
                }
                AppResult.Success(Unit)
            }
            is AppResult.Error -> AppResult.Error(result.error)
        }
    }

    suspend fun followTeam(
        teamId: String,
        teamName: String,
        shortName: String,
        imageUrl: String?,
        sport: String = "cricket",
    ): AppResult<Unit> {
        val entity = FollowedTeamEntity(
            teamId = teamId,
            teamName = teamName,
            shortName = shortName,
            imageUrl = imageUrl,
            sport = sport,
        )
        // Optimistic local insert
        followedTeamDao.insert(entity)

        val request = FollowTeamRequest(
            teamId = teamId,
            teamName = teamName,
            shortName = shortName,
            imageUrl = imageUrl,
            sport = sport,
        )
        return when (val result = safeApiCall { libraryApi.followTeam(request) }) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Error -> AppResult.Error(result.error)
        }
    }

    suspend fun unfollowTeam(teamId: String): AppResult<Unit> {
        followedTeamDao.deleteByTeamId(teamId)
        return when (val result = safeApiCall { libraryApi.unfollowTeam(teamId) }) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Error -> AppResult.Error(result.error)
        }
    }

    suspend fun clearLocalCache() {
        followedTeamDao.clearAll()
    }
}
