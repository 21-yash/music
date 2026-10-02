package com.musicsportsapp.features.music.data.dto

import com.musicsportsapp.features.music.domain.model.Album
import com.musicsportsapp.features.music.domain.model.AlbumRef
import com.musicsportsapp.features.music.domain.model.Artist
import com.musicsportsapp.features.music.domain.model.ArtistCredit
import com.musicsportsapp.features.music.domain.model.Playlist
import com.musicsportsapp.features.music.domain.model.SearchResults
import com.musicsportsapp.features.music.domain.model.Song
import kotlinx.serialization.Serializable

@Serializable
data class SongDto(
    val id: String,
    val title: String,
    val artists: List<ArtistCreditDto> = emptyList(),
    val album: AlbumRefDto? = null,
    val duration: Int = 0,
    val imageUrl: String? = null,
    val year: String = "",
    val language: String = "",
    val hasLyrics: Boolean = false,
    val playCount: Int = 0,
    val label: String = "",
    val streamRef: String = "",
    val providerId: String = ""
) {
    fun toDomain() = Song(
        id = id,
        title = title,
        artists = artists.map { it.toDomain() },
        album = album?.toDomain(),
        durationSeconds = duration,
        imageUrl = imageUrl,
        year = year,
        language = language,
        hasLyrics = hasLyrics,
        playCount = playCount,
        label = label,
        streamRef = streamRef,
        providerId = providerId
    )
}

@Serializable
data class ArtistCreditDto(
    val id: String,
    val name: String,
    val imageUrl: String? = null
) {
    fun toDomain() = ArtistCredit(
        id = id,
        name = name,
        imageUrl = imageUrl
    )
}

@Serializable
data class AlbumRefDto(
    val id: String,
    val title: String,
    val imageUrl: String? = null
) {
    fun toDomain() = AlbumRef(
        id = id,
        title = title,
        imageUrl = imageUrl
    )
}

@Serializable
data class SearchResultsDto(
    val songs: List<SongDto> = emptyList(),
    val albums: List<AlbumRefDto> = emptyList(),
    val artists: List<ArtistCreditDto> = emptyList(),
    val totalSongs: Int = 0,
    val query: String = ""
) {
    fun toDomain() = SearchResults(
        songs = songs.map { it.toDomain() },
        albums = albums.map { it.toDomain() },
        artists = artists.map { it.toDomain() },
        totalSongs = totalSongs,
        query = query
    )
}

@Serializable
data class AlbumDto(
    val id: String,
    val title: String,
    val artists: List<ArtistCreditDto> = emptyList(),
    val imageUrl: String? = null,
    val year: String = "",
    val language: String = "",
    val songCount: Int = 0,
    val songs: List<SongDto> = emptyList(),
    val providerId: String = ""
) {
    fun toDomain() = Album(
        id = id,
        title = title,
        artists = artists.map { it.toDomain() },
        imageUrl = imageUrl,
        year = year,
        language = language,
        songCount = songCount,
        songs = songs.map { it.toDomain() },
        providerId = providerId
    )
}

@Serializable
data class ArtistDto(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val bio: String = "",
    val followerCount: Int = 0,
    val topSongs: List<SongDto> = emptyList(),
    val albums: List<AlbumRefDto> = emptyList(),
    val providerId: String = ""
) {
    fun toDomain() = Artist(
        id = id,
        name = name,
        imageUrl = imageUrl,
        bio = bio,
        followerCount = followerCount,
        topSongs = topSongs.map { it.toDomain() },
        albums = albums.map { it.toDomain() },
        providerId = providerId
    )
}

@Serializable
data class PlaylistDto(
    val id: String,
    val title: String,
    val description: String = "",
    val imageUrl: String? = null,
    val songCount: Int = 0,
    val followerCount: Int = 0,
    val songs: List<SongDto> = emptyList(),
    val providerId: String = ""
) {
    fun toDomain() = Playlist(
        id = id,
        title = title,
        description = description,
        imageUrl = imageUrl,
        songCount = songCount,
        followerCount = followerCount,
        songs = songs.map { it.toDomain() },
        providerId = providerId
    )
}

@Serializable
data class TrendingResponseDto(
    val songs: List<SongDto> = emptyList()
)
