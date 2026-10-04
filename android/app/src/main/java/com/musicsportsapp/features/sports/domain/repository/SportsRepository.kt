package com.musicsportsapp.features.sports.domain.repository

import com.musicsportsapp.features.sports.domain.model.MatchSummary
import kotlinx.coroutines.flow.Flow

interface SportsRepository {
    /**
     * Fetches a list of matches based on the filter ('live', 'upcoming', 'recent').
     * Returns a Flow of Result containing the list of MatchSummary.
     */
    fun getMatches(filter: String): Flow<Result<List<MatchSummary>>>
}
