package com.synq.features.music

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synq.RootViewModel
import com.synq.features.music.domain.model.AlbumRef
import com.synq.features.music.domain.model.PlaylistRef
import com.synq.ui.AppHeader
import com.synq.ui.components.SectionHeader
import com.synq.ui.motion.MotionTokens
import com.synq.ui.motion.pressable
import com.synq.ui.motion.shimmer
import com.synq.ui.rememberArtworkTint
import com.synq.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicScreen(
    navController: NavController,
    viewModel: MusicViewModel = hiltViewModel(),
    rootViewModel: RootViewModel = hiltViewModel(),
    onNavigateToSearch: () -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToPlaylist: (String) -> Unit = {},
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToPopularArtists: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberLazyListState()
    val context = LocalContext.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    val animatorScale = remember {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        } catch (_: Exception) {
            1f
        }
    }

    val isSearchStuck by remember {
        derivedStateOf { scrollState.firstVisibleItemIndex > 0 }
    }
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    val genreList = remember {
        listOf("Pop", "Hip-Hop", "Bollywood", "Punjabi", "Rock", "Indie", "Dance", "Lo-Fi", "Classical")
    }

    val mockFeatured = remember {
        listOf(
            AlbumRef(id = "mock_1", title = "Making Memories", imageUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=400&q=80"),
            AlbumRef(id = "mock_2", title = "Late Night Chill Vibes", imageUrl = "https://images.unsplash.com/photo-1516280440502-861111617266?w=400&q=80")
        )
    }

    val fallbackPlaylists = remember {
        listOf(
            PlaylistRef(id = "fb_1", title = "Top JioSaavn Hindi", imageUrl = "https://c.saavncdn.com/editorial/TopJioSaavnHindi_20240322135008.jpg"),
            PlaylistRef(id = "fb_2", title = "Punjabi Hits", imageUrl = "https://c.saavncdn.com/editorial/PunjabiHits_20240322135008.jpg"),
            PlaylistRef(id = "fb_3", title = "Romance Forever", imageUrl = "https://c.saavncdn.com/editorial/BestOfRomanceHindi_20260901103522_500x500.jpg"),
            PlaylistRef(id = "fb_4", title = "Retro Classics", imageUrl = "https://c.saavncdn.com/editorial/BestOfRetroHindi_20230913103522_500x500.jpg")
        ).chunked(2)
    }

    val mockBestOf = remember {
        listOf(
            PlaylistRef(id = "mo_1", title = "Best of Romance", imageUrl = "https://c.saavncdn.com/editorial/BestOfRomanceHindi_20260901103522_500x500.jpg"),
            PlaylistRef(id = "mo_2", title = "Best of Retro", imageUrl = "https://c.saavncdn.com/editorial/BestOfRetroHindi_20230913103522_500x500.jpg"),
            PlaylistRef(id = "mo_3", title = "Best of 2000s", imageUrl = "https://c.saavncdn.com/editorial/BestOf2000sHindi_20230913103522_500x500.jpg")
        )
    }

    val fallbackArtists = remember {
        listOf(
            Pair("Arijit Singh", "https://c.saavncdn.com/artists/Arijit_Singh_004_20241118063717_500x500.jpg"),
            Pair("Pritam", "https://c.saavncdn.com/artists/Pritam_Chakraborty-20170711073326_500x500.jpg"),
            Pair("KK", "https://c.saavncdn.com/artists/KK_500x500.jpg"),
            Pair("Shreya Ghoshal", "https://c.saavncdn.com/artists/Shreya_Ghoshal_007_20241101074144_500x500.jpg")
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            // ─── 1. AppHeader (scrolls away with content) ───
            item(key = "app_header") {
                AppHeader(
                    title = "Discover Music",
                    subtitle = "Explore tracks, artists & playlists",
                    rootViewModel = rootViewModel,
                    navController = navController
                )
            }

            // ─── 2. Search Box (stickyHeader pinned to top) ───
            stickyHeader(key = "sticky_search") {
                val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(top = if (isSearchStuck) statusBarTop else 0.dp)
                        .drawBehind {
                            if (isSearchStuck) {
                                drawLine(
                                    color = outlineVariantColor,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .pressable(
                                onClick = onNavigateToSearch,
                                pressedScale = MotionTokens.CardPressScale
                            )
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                shape = MaterialTheme.shapes.large
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Search JioSaavn or YouTube...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // ─── 3. Genre Chips (single row, scrollable, neutral style) ───
            item(key = "genre_chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(genreList, key = { it }) { genre ->
                        NeutralGenreChip(
                            text = genre,
                            onClick = onNavigateToSearch
                        )
                    }
                }
            }

            // ─── Skeletons when loading with no cached data ───
            if (uiState.isLoading && uiState.homeData == null) {
                item(key = "skel_featured") {
                    SectionHeader(
                        title = "Featured Releases",
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 16.dp,
                            bottom = Spacing.headerToContent
                        )
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .width(minOf(300.dp, screenWidth - 60.dp))
                            .height(150.dp)
                            .clip(MaterialTheme.shapes.large)
                            .shimmer(MaterialTheme.shapes.large)
                    )
                }
                item(key = "skel_playlists") {
                    SectionHeader(
                        title = "Top Playlists",
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = Spacing.sectionGap,
                            bottom = Spacing.headerToContent
                        )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(MaterialTheme.shapes.small)
                                .shimmer(MaterialTheme.shapes.small)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(MaterialTheme.shapes.small)
                                .shimmer(MaterialTheme.shapes.small)
                        )
                    }
                }
            }

            // ─── 4. Featured Releases (Hero Carousel with Artwork Tint & Parallax) ───
            val featured = uiState.homeData?.featuredReleases
            if (!featured.isNullOrEmpty()) {
                item(key = "featured_section") {
                    SectionHeader(
                        title = "Featured Releases",
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 16.dp,
                            bottom = Spacing.headerToContent
                        )
                    )
                    FeaturedHeroCarousel(
                        albums = featured,
                        cardWidth = minOf(300.dp, screenWidth - 60.dp),
                        animatorScale = animatorScale,
                        onAlbumClick = onNavigateToAlbum
                    )
                }
            } else if (!uiState.isLoading) {
                item(key = "featured_fallback") {
                    SectionHeader(
                        title = "Featured Releases",
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 16.dp,
                            bottom = Spacing.headerToContent
                        )
                    )
                    FeaturedHeroCarousel(
                        albums = mockFeatured,
                        cardWidth = minOf(300.dp, screenWidth - 60.dp),
                        animatorScale = animatorScale,
                        onAlbumClick = {}
                    )
                }
            }

            // ─── 5. Top Playlists (2-Column Compact Grid, chunked(2), no cap) ───
            val topPlaylists = uiState.homeData?.topPlaylists
            if (!topPlaylists.isNullOrEmpty()) {
                item(key = "top_playlists_header") {
                    SectionHeader(
                        title = "Top Playlists",
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = Spacing.sectionGap,
                            bottom = Spacing.headerToContent
                        )
                    )
                }
                val playlistPairs = topPlaylists.chunked(2)
                items(playlistPairs, key = { pair -> pair.map { it.id }.joinToString("_") }) { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompactPlaylistTile(
                            title = pair[0].title,
                            imageUrl = pair[0].imageUrl ?: "",
                            onClick = { onNavigateToPlaylist(pair[0].id) },
                            modifier = Modifier
                                .weight(1f)
                                .animateItem()
                        )
                        if (pair.size > 1) {
                            CompactPlaylistTile(
                                title = pair[1].title,
                                imageUrl = pair[1].imageUrl ?: "",
                                onClick = { onNavigateToPlaylist(pair[1].id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .animateItem()
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else if (!uiState.isLoading) {
                item(key = "top_playlists_fallback_header") {
                    SectionHeader(
                        title = "Top Playlists",
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = Spacing.sectionGap,
                            bottom = Spacing.headerToContent
                        )
                    )
                }
                items(fallbackPlaylists, key = { pair -> pair.map { it.id }.joinToString("_") }) { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompactPlaylistTile(
                            title = pair[0].title,
                            imageUrl = pair[0].imageUrl ?: "",
                            onClick = {},
                            modifier = Modifier.weight(1f)
                        )
                        if (pair.size > 1) {
                            CompactPlaylistTile(
                                title = pair[1].title,
                                imageUrl = pair[1].imageUrl ?: "",
                                onClick = {},
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // ─── 6. Popular Artists (80dp Avatars with Faint Pink Ring) ───
            val popularArtists = uiState.homeData?.popularArtists
            if (!popularArtists.isNullOrEmpty()) {
                item(key = "popular_artists_section") {
                    SectionHeader(
                        title = "Popular Artists",
                        actionText = if (popularArtists.size > 8) "See all" else null,
                        onActionClick = onNavigateToPopularArtists,
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = Spacing.sectionGap,
                            bottom = Spacing.headerToContent
                        )
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        val initialArtists = popularArtists.take(10)
                        items(initialArtists, key = { it.id }) { artist ->
                            RingedArtistAvatar(
                                name = artist.name,
                                imageUrl = artist.imageUrl,
                                onClick = { onNavigateToArtist(artist.id) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            } else if (!uiState.isLoading) {
                item(key = "popular_artists_fallback") {
                    SectionHeader(
                        title = "Popular Artists",
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = Spacing.sectionGap,
                            bottom = Spacing.headerToContent
                        )
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(fallbackArtists, key = { it.first }) { artist ->
                            RingedArtistAvatar(
                                name = artist.first,
                                imageUrl = artist.second,
                                onClick = { onNavigateToArtist(artist.first) }
                            )
                        }
                    }
                }
            }

            // ─── 7. Best Of (Unchanged per instructions) ───
            val bestOfList = uiState.homeData?.bestOf
            if (!bestOfList.isNullOrEmpty()) {
                item(key = "best_of_section") {
                    SectionHeader(
                        title = "Best Of",
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = Spacing.sectionGap,
                            bottom = Spacing.headerToContent
                        )
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(bestOfList, key = { it.id }) { playlist ->
                            SquareCard(
                                title = playlist.title,
                                imageUrl = playlist.imageUrl ?: "",
                                onClick = { onNavigateToPlaylist(playlist.id) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            } else if (!uiState.isLoading) {
                item(key = "best_of_fallback") {
                    SectionHeader(
                        title = "Best Of",
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = Spacing.sectionGap,
                            bottom = Spacing.headerToContent
                        )
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(mockBestOf, key = { it.id }) { playlist ->
                            SquareCard(
                                title = playlist.title,
                                imageUrl = playlist.imageUrl ?: "",
                                onClick = {}
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Neutral Genre Chip (32dp tall, surfaceContainerHighest, 12sp Medium)
// ─────────────────────────────────────────────────────────────

@Composable
private fun NeutralGenreChip(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .pressable(
                onClick = onClick,
                pressedScale = MotionTokens.ChipPressScale
            )
            .height(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Featured Hero Carousel with Snap, Artwork Tint & Parallax
// ─────────────────────────────────────────────────────────────

@Composable
private fun FeaturedHeroCarousel(
    albums: List<AlbumRef>,
    cardWidth: androidx.compose.ui.unit.Dp,
    animatorScale: Float,
    onAlbumClick: (String) -> Unit
) {
    val carouselState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = carouselState)
    var hasPlayedEntrance by rememberSaveable { mutableStateOf(false) }

    LazyRow(
        state = carouselState,
        flingBehavior = flingBehavior,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(albums.size, key = { index -> albums[index].id }) { index ->
            val album = albums[index]
            HeroAlbumCard(
                album = album,
                cardWidth = cardWidth,
                index = index,
                hasPlayedEntrance = hasPlayedEntrance,
                onEntrancePlayed = { hasPlayedEntrance = true },
                carouselState = carouselState,
                animatorScale = animatorScale,
                onClick = { onAlbumClick(album.id) }
            )
        }
    }
}

@Composable
private fun HeroAlbumCard(
    album: AlbumRef,
    cardWidth: androidx.compose.ui.unit.Dp,
    index: Int,
    hasPlayedEntrance: Boolean,
    onEntrancePlayed: () -> Unit,
    carouselState: androidx.compose.foundation.lazy.LazyListState,
    animatorScale: Float,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(album.imageUrl) {
        ImageRequest.Builder(context)
            .data(album.imageUrl)
            .crossfade(true)
            .crossfade(200)
            .build()
    }

    // Dynamic Artwork Tint (blended with surfaceContainerLow for readability)
    val baseSurface = MaterialTheme.colorScheme.surfaceContainerLow
    val rawTint by rememberArtworkTint(album.imageUrl)
    val blendedTint = remember(rawTint, baseSurface) {
        rawTint.copy(alpha = 0.32f).compositeOver(baseSurface)
    }
    val cardGradient = remember(blendedTint, baseSurface) {
        Brush.horizontalGradient(listOf(blendedTint, baseSurface))
    }

    // Entrance Motion: Fade + 12dp slide, 60ms stagger, 220ms duration (first-time only)
    val entranceAlpha = remember { Animatable(if (hasPlayedEntrance || animatorScale == 0f) 1f else 0f) }
    val entranceSlideY = remember { Animatable(if (hasPlayedEntrance || animatorScale == 0f) 0f else 12f) }

    LaunchedEffect(Unit) {
        if (!hasPlayedEntrance && animatorScale > 0f) {
            delay(index * 60L)
            launch {
                entranceAlpha.animateTo(1f, animationSpec = tween(220, easing = LinearOutSlowInEasing))
            }
            launch {
                entranceSlideY.animateTo(0f, animationSpec = tween(220, easing = FastOutSlowInEasing))
            }
            if (index == 0) {
                onEntrancePlayed()
            }
        }
    }

    Box(
        modifier = Modifier
            .pressable(
                onClick = onClick,
                pressedScale = 0.98f
            )
            .graphicsLayer {
                alpha = entranceAlpha.value
                translationY = entranceSlideY.value.dp.toPx()
            }
            .width(cardWidth)
            .height(150.dp)
            .clip(MaterialTheme.shapes.large)
            .background(cardGradient)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: NEW pill at top, Title + Subtitle at bottom
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // NEW pill (uppercase, white 11sp SemiBold, letterSpacing 0.4sp)
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(PinkPrimary)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "NEW",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = album.title,
                        // 18sp SemiBold: MaterialTheme.typography.titleLarge is 22sp, copying with 18sp SemiBold matches hero card hierarchy without overflowing 2 lines
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Album",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right: 118dp square cover with soft shadow and scroll parallax
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        // Hero cover parallax: shift cover horizontally by at most 16dp relative to card
                        if (animatorScale > 0f && carouselState.layoutInfo.viewportSize.width > 0) {
                            val itemInfo = carouselState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                            if (itemInfo != null) {
                                val itemCenter = itemInfo.offset + itemInfo.size / 2f
                                val viewportCenter = carouselState.layoutInfo.viewportSize.width / 2f
                                val fraction = ((itemCenter - viewportCenter) / (carouselState.layoutInfo.viewportSize.width / 2f)).coerceIn(-1f, 1f)
                                translationX = -fraction * 16.dp.toPx()
                            }
                        }
                    }
                    .size(118.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = MaterialTheme.shapes.medium,
                        spotColor = Color.Black.copy(alpha = 0.35f)
                    )
                    .clip(MaterialTheme.shapes.medium)
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = album.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Compact Playlist Tile (52dp tall, flush cover, 2-column grid item)
// ─────────────────────────────────────────────────────────────

@Composable
private fun CompactPlaylistTile(
    title: String,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .crossfade(200)
            .build()
    }

    Row(
        modifier = modifier
            .pressable(
                onClick = onClick,
                pressedScale = 0.97f
            )
            .height(52.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 17.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Popular Artists: 80dp Avatar with 2dp gap and 1.5dp PinkPrimary ring
// ─────────────────────────────────────────────────────────────

@Composable
private fun RingedArtistAvatar(
    name: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .crossfade(200)
            .build()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(88.dp)
            .pressable(
                onClick = onClick,
                pressedScale = 0.96f
            )
    ) {
        // Outer container: 87dp with 1.5dp PinkPrimary ring (35% alpha) + 2dp gap to 80dp avatar
        Box(
            modifier = Modifier
                .size(87.dp)
                .border(
                    width = 1.5.dp,
                    color = PinkPrimary.copy(alpha = 0.35f),
                    shape = CircleShape
                )
                .padding(3.5.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Best Of SquareCard (Unchanged layout per prompt instruction)
// ─────────────────────────────────────────────────────────────

@Composable
private fun SquareCard(
    title: String,
    imageUrl: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .crossfade(200)
            .build()
    }
    Column(
        modifier = modifier
            .width(140.dp)
            .pressable(
                onClick = onClick,
                pressedScale = MotionTokens.CardPressScale
            )
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(MaterialTheme.shapes.medium)
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .height(40.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
