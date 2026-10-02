package com.musicsportsapp.features.music.domain.model

/**
 * Normalized domain models for the Music feature.
 *
 * These represent the core business logic models used by the UI and ViewModels.
 * They are agnostic to the backend API structure (which is handled by DTOs).
 */

data class Song(
    val id: String,
    val title: String,
    val artists: List<ArtistCredit>,
    val album: AlbumRef?,
    val durationSeconds: Int,
    val imageUrl: String?,
    val year: String,
    val language: String,
    val hasLyrics: Boolean,
    val playCount: Int,
    val label: String,
    val streamRef: String,
    val providerId: String
) {
    // Helper to get formatted duration (e.g., "3:45")
    val formattedDuration: String
        get() = String.format("%d:%02d", durationSeconds / 60, durationSeconds % 60)
        
    // Helper to get primary artist name or default
    val primaryArtistName: String
        get() = artists.firstOrNull()?.name ?: "Unknown Artist"
}

data class ArtistCredit(
    val id: String,
    val name: String,
    val imageUrl: String?
)

data class AlbumRef(
    val id: String,
    val title: String,
    val imageUrl: String?
)

data class SearchResults(
    val songs: List<Song>,
    val albums: List<AlbumRef>,
    val artists: List<ArtistCredit>,
    val totalSongs: Int,
    val query: String
)

data class Album(
    val id: String,
    val title: String,
    val artists: List<ArtistCredit>,
    val imageUrl: String?,
    val year: String,
    val language: String,
    val songCount: Int,
    val songs: List<Song>,
    val providerId: String
)

data class Artist(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val bio: String,
    val followerCount: Int,
    val topSongs: List<Song>,
    val albums: List<AlbumRef>,
    val providerId: String
)

data class Playlist(
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val songCount: Int,
    val followerCount: Int,
    val songs: List<Song>,
    val providerId: String
)
