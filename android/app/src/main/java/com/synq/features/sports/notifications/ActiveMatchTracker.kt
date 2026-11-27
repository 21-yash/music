package com.synq.features.sports.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks active MatchDetails screens in navigation stack order so incoming notifications
 * for the currently visible match are suppressed, and popping back from Match B -> Match A
 * seamlessly keeps Match A suppressed while on screen.
 */
@Singleton
class ActiveMatchTracker @Inject constructor() {
    private val lock = Any()
    private val matchStack = mutableListOf<String>()

    private val _activeMatchId = MutableStateFlow<String?>(null)
    val activeMatchId: StateFlow<String?> = _activeMatchId.asStateFlow()

    fun enterMatch(matchId: String) {
        synchronized(lock) {
            matchStack.remove(matchId)
            matchStack.add(matchId)
            _activeMatchId.value = matchStack.lastOrNull()
        }
    }

    fun exitMatch(matchId: String) {
        synchronized(lock) {
            matchStack.remove(matchId)
            _activeMatchId.value = matchStack.lastOrNull()
        }
    }

    fun isViewingMatch(matchId: String): Boolean {
        return synchronized(lock) {
            _activeMatchId.value == matchId
        }
    }
}
