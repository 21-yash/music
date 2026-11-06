package com.synq.features.music.jam

interface JamPreferencesBridge {
    fun saveLastJam(code: String, avatarUrl: String? = null)
    fun clearLastJam()
    fun rejoinCandidate(): String?
    fun getLastAvatarUrl(): String? = null
}

