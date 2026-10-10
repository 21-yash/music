package com.musicsportsapp.features.music.domain.model

data class PlaylistRef(
    val id: String,
    val title: String,
    val imageUrl: String?
)

data class HomeData(
    val featuredReleases: List<AlbumRef> = emptyList(),
    val topPlaylists: List<PlaylistRef> = emptyList(),
    val charts: List<PlaylistRef> = emptyList(),
    val bestOf: List<PlaylistRef> = emptyList(),
    val popularArtists: List<ArtistCredit> = emptyList()
)
