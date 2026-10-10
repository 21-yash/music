package com.musicsportsapp.features.music.data.dto

import com.musicsportsapp.features.music.domain.model.AlbumRef
import com.musicsportsapp.features.music.domain.model.HomeData
import com.musicsportsapp.features.music.domain.model.PlaylistRef
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistRefDto(
    val id: String,
    val title: String,
    val imageUrl: String?
) {
    fun toDomain() = PlaylistRef(id, title, imageUrl)
}

@Serializable
data class HomeDataDto(
    val featuredReleases: List<AlbumRefDto> = emptyList(),
    val topPlaylists: List<PlaylistRefDto> = emptyList(),
    val charts: List<PlaylistRefDto> = emptyList(),
    val bestOf: List<PlaylistRefDto> = emptyList(),
    val popularArtists: List<ArtistCreditDto> = emptyList()
) {
    fun toDomain() = HomeData(
        featuredReleases = featuredReleases.map { it.toDomain() },
        topPlaylists = topPlaylists.map { it.toDomain() },
        charts = charts.map { it.toDomain() },
        bestOf = bestOf.map { it.toDomain() },
        popularArtists = popularArtists.map { it.toDomain() }
    )
}
