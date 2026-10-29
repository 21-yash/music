package com.synq.features.music.jam

interface JamPreferencesBridge {
    fun saveLastJam(code: String)
    fun clearLastJam()
    fun rejoinCandidate(): String?
}

