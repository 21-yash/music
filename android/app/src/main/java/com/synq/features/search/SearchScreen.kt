package com.synq.features.search

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synq.R
import com.synq.RootViewModel
import com.synq.features.music.components.LocalAddToPlaylist
import com.synq.features.music.components.SongItem
import com.synq.features.music.domain.model.AlbumRef
import com.synq.features.music.domain.model.ArtistCredit
import com.synq.features.music.domain.model.SearchResults
import com.synq.features.music.domain.model.Song
import com.synq.ui.components.SectionHeader
import com.synq.ui.motion.MotionTokens
import com.synq.ui.motion.pressable
import com.synq.ui.motion.shimmer
import com.synq.ui.rememberArtworkTint
import com.synq.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    rootViewModel: RootViewModel = hiltViewModel(),
    initialQuery: String? = null,
    onBackClick: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val jamState by rootViewModel.jamState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

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

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            viewModel.updateQuery(initialQuery)
        } else {
            kotlinx.coroutines.delay(200)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val isJioSaavn = uiState.provider == "jiosaavn"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // ─── Top Bar: Back Button, Search Field & Provider Sliding Toggle ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        shape = MaterialTheme.shapes.large
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = uiState.query,
                        onValueChange = { viewModel.updateQuery(it) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 15.sp
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(PinkPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        decorationBox = { innerTextField ->
                            if (uiState.query.isEmpty()) {
                                Text(
                                    text = if (isJioSaavn) "Search JioSaavn" else "Search YouTube",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 15.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (uiState.query.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.updateQuery("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // ─── Provider Sliding Toggle (76x38dp Pill, 48dp hit area) ───
            ProviderSlidingToggle(
                isJioSaavn = isJioSaavn,
                onToggle = {
                    viewModel.updateProvider(if (isJioSaavn) "youtube" else "jiosaavn")
                }
            )
        }

        // ─── Main Content: Idle Screen vs Results Screen ───
        if (uiState.query.isBlank()) {
            IdleSearchContent(
                recentSearches = uiState.recentSearches,
                onRecentClick = { viewModel.onRecentSearchClicked(it) },
                onRemoveRecent = { viewModel.removeRecentSearch(it) },
                onClearAllRecent = { viewModel.clearRecentSearches() }
            )
        } else {
            SearchResultsContent(
                uiState = uiState,
                animatorScale = animatorScale,
                onSongClick = { song ->
                    if (jamState.isInJam) {
                        rootViewModel.addSongToJam(song)
                    } else {
                        viewModel.playSong(song)
                    }
                },
                onPlayNext = { song ->
                    if (jamState.isInJam) {
                        rootViewModel.addSongToJam(song, playNext = true)
                    } else {
                        viewModel.playNext(song)
                    }
                },
                onAddToQueue = { song ->
                    if (jamState.isInJam) {
                        rootViewModel.addSongToJam(song)
                    } else {
                        viewModel.addToQueue(song)
                    }
                },
                onArtistClick = onArtistClick,
                onAlbumClick = onAlbumClick
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 1. Provider Sliding Toggle (80x40dp Pill with Slower Rolling Active Thumb)
// ─────────────────────────────────────────────────────────────

@Composable
private fun ProviderSlidingToggle(
    isJioSaavn: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // Slower, graceful rolling spring animation (~420ms)
    val rollSpec = remember {
        spring<Float>(
            dampingRatio = 0.76f,
            stiffness = 320f
        )
    }
    val offsetSpec = remember {
        spring<androidx.compose.ui.unit.Dp>(
            dampingRatio = 0.76f,
            stiffness = 320f
        )
    }

    val thumbOffset by animateDpAsState(
        targetValue = if (isJioSaavn) 0.dp else 40.dp,
        animationSpec = offsetSpec,
        label = "providerThumbOffset"
    )

    val rollAngle by animateFloatAsState(
        targetValue = if (isJioSaavn) 0f else 360f,
        animationSpec = rollSpec,
        label = "providerRollAngle"
    )

    Box(
        modifier = modifier
            .height(48.dp)
            .pressable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
                pressedScale = 0.96f
            )
            .semantics {
                role = Role.Switch
                contentDescription = "Search source"
                stateDescription = if (isJioSaavn) "JioSaavn" else "YouTube"
            },
        contentAlignment = Alignment.Center
    ) {
        // Pill track: 80dp wide, 40dp tall
        Box(
            modifier = Modifier
                .size(width = 80.dp, height = 40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    shape = CircleShape
                )
                .padding(3.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // Inactive JioSaavn placeholder (small 16dp icon at 25% alpha)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .graphicsLayer { alpha = if (isJioSaavn) 0f else 0.25f },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.saavn),
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                )
            }

            // Inactive YouTube placeholder (small 16dp icon at 25% alpha)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(34.dp)
                    .graphicsLayer { alpha = if (!isJioSaavn) 0f else 0.25f },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.yt),
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                )
            }

            // Active rolling thumb (prominent 34dp circle with 26dp icon, elevated & 100% active)
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .graphicsLayer { rotationZ = rollAngle }
                    .size(34.dp)
                    .shadow(elevation = 3.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                val activeIconRes = if (rollAngle < 180f) R.drawable.saavn else R.drawable.yt
                Image(
                    painter = painterResource(id = activeIconRes),
                    contentDescription = if (isJioSaavn) "JioSaavn" else "YouTube",
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 2. Idle Screen: "Recent" FlowRow Chips + "Browse all" Tiles
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdleSearchContent(
    recentSearches: List<String>,
    onRecentClick: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearAllRecent: () -> Unit
) {
    val categories = remember {
        listOf(
            "Bollywood", "Pop", "Rock", "Hip-Hop",
            "Classical", "Jazz", "EDM", "Indie"
        )
    }
    val uniqueRecentSearches = remember(recentSearches) { recentSearches.distinct() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 164.dp)
    ) {
        // ─── Recent Searches (FlowRow Chips capped visually to 2 lines) ───
        if (uniqueRecentSearches.isNotEmpty()) {
            item(key = "recent_header") {
                SectionHeader(
                    title = "Recent",
                    actionText = "Clear all",
                    onActionClick = onClearAllRecent,
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 8.dp,
                        bottom = Spacing.headerToContent
                    )
                )
            }

            item(key = "recent_flow") {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxLines = 2
                ) {
                    uniqueRecentSearches.forEach { query ->
                        RecentSearchChip(
                            text = query,
                            onClick = { onRecentClick(query) },
                            onRemove = { onRemoveRecent(query) }
                        )
                    }
                }
            }

            item(key = "recent_divider") {
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
            }
        }

        // ─── Browse All (2-Column Grid of 72dp Tonal Tiles with Decorative Circle) ───
        item(key = "browse_all_header") {
            SectionHeader(
                title = "Browse all",
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = if (uniqueRecentSearches.isNotEmpty()) 20.dp else 8.dp,
                    bottom = Spacing.headerToContent
                )
            )
        }

        val categoryPairs = categories.chunked(2)
        items(categoryPairs.size, key = { "cat_row_$it" }) { rowIndex ->
            val pair = categoryPairs[rowIndex]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val idx1 = rowIndex * 2
                CategoryTile(
                    name = pair[0],
                    accentColor = CategoryColors[idx1 % CategoryColors.size],
                    onClick = { onRecentClick(pair[0]) },
                    modifier = Modifier.weight(1f)
                )
                if (pair.size > 1) {
                    val idx2 = idx1 + 1
                    CategoryTile(
                        name = pair[1],
                        accentColor = CategoryColors[idx2 % CategoryColors.size],
                        onClick = { onRecentClick(pair[1]) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RecentSearchChip(
    text: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(32.dp)
            .pressable(
                onClick = onClick,
                pressedScale = MotionTokens.ChipPressScale
            )
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onRemove
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove $text",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun CategoryTile(
    name: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baseSurface = MaterialTheme.colorScheme.surfaceContainerLow
    val tileGradient = remember(accentColor, baseSurface) {
        Brush.linearGradient(
            listOf(
                accentColor.copy(alpha = 0.38f),
                baseSurface
            )
        )
    }

    Box(
        modifier = modifier
            .height(72.dp)
            .pressable(
                onClick = onClick,
                pressedScale = MotionTokens.TilePressScale
            )
            .clip(MaterialTheme.shapes.medium)
            .background(tileGradient)
    ) {
        // Decorative circle (56dp, subtle accent color at ~20% alpha) clipped at bottom-right
        Box(
            modifier = Modifier
                .size(56.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 12.dp, y = 12.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.20f))
        )

        // Label 15sp SemiBold bottom-start
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 3. Results Content: Filter Chips, Top Result, Artists, Albums, Songs
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchResultsContent(
    uiState: SearchUiState,
    animatorScale: Float = 1f,
    onSongClick: (Song) -> Unit,
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onAddToPlaylist: ((Song) -> Unit)? = null,
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {}
) {
    val localAddToPlaylist = LocalAddToPlaylist.current
    val handleAddToPlaylist = onAddToPlaylist ?: { song -> localAddToPlaylist(song) }

    // Loading State: 6 skeleton rows
    if (uiState.isLoading) {
        SearchSkeletonList()
        return
    }

    // Error State: Centered semantic error icon + message
    if (uiState.error != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = uiState.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val results = uiState.results
    if (results == null || (results.songs.isEmpty() && results.albums.isEmpty() && results.artists.isEmpty())) {
        // Empty State: Centered pink search icon + 2 descriptive lines
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = PinkPrimary.copy(alpha = 0.70f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No results for \"${uiState.query}\"",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Try another spelling, or switch source.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    // Section presence flags & deduplicated collections (guards against duplicate keys from providers)
    val uniqueSongs = remember(results.songs) {
        val seen = HashSet<String>()
        results.songs.filter { song ->
            val id = song.id.ifBlank { song.title }
            seen.add(id)
        }
    }
    val uniqueArtists = remember(results.artists) {
        val seen = HashSet<String>()
        results.artists.filter { artist ->
            val id = artist.id.ifBlank { artist.name }
            seen.add(id)
        }
    }
    val uniqueAlbums = remember(results.albums) {
        val seen = HashSet<String>()
        results.albums.filter { album ->
            val id = album.id.ifBlank { album.title }
            seen.add(id)
        }
    }

    val hasSongs = uniqueSongs.isNotEmpty()
    val hasArtists = uniqueArtists.isNotEmpty()
    val hasAlbums = uniqueAlbums.isNotEmpty()

    // Filter Chips: All, Songs, Artists, Albums (only shown if multi-category results exist)
    val availableFilters = remember(hasSongs, hasArtists, hasAlbums) {
        if (hasArtists || hasAlbums) {
            listOfNotNull(
                "All",
                if (hasSongs) "Songs" else null,
                if (hasArtists) "Artists" else null,
                if (hasAlbums) "Albums" else null
            )
        } else {
            emptyList()
        }
    }
    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    if (selectedFilter !in availableFilters && availableFilters.isNotEmpty()) {
        selectedFilter = "All"
    }

    // Entrance Motion: Fade in on query results update
    var hasPlayedEntrance by rememberSaveable { mutableStateOf(false) }
    val entranceAlpha = remember { Animatable(if (hasPlayedEntrance || animatorScale == 0f) 1f else 0f) }
    LaunchedEffect(uiState.query) {
        if (!hasPlayedEntrance && animatorScale > 0f) {
            entranceAlpha.animateTo(1f, animationSpec = tween(220, easing = LinearOutSlowInEasing))
            hasPlayedEntrance = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = entranceAlpha.value }
    ) {
        // Filter Chips Row
        if (availableFilters.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableFilters, key = { it }) { filter ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .pressable(
                                onClick = { selectedFilter = filter },
                                pressedScale = MotionTokens.ChipPressScale
                            )
                            .clip(CircleShape)
                            .background(
                                if (isSelected) PinkPrimary else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 164.dp)
        ) {
            // ─── 1. Top Result Card (First song in "All" view) ───
            if (selectedFilter == "All" && hasSongs) {
                val firstSong = uniqueSongs.first()
                item(key = "top_result_card") {
                    TopResultCard(
                        song = firstSong,
                        onClick = { onSongClick(firstSong) }
                    )
                }
            }

            // ─── 2. Artists Row ───
            if ((selectedFilter == "All" || selectedFilter == "Artists") && hasArtists) {
                item(key = "artists_header") {
                    SectionHeader(
                        title = "Artists",
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 16.dp,
                            bottom = Spacing.headerToContent
                        )
                    )
                }
                item(key = "artists_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(uniqueArtists, key = { index, artist ->
                            val id = artist.id.ifBlank { artist.name }
                            "artist_${id}_$index"
                        }) { _, artist ->
                            SearchArtistCircleItem(
                                artist = artist,
                                onClick = { onArtistClick(artist.id) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }

            // ─── 3. Albums Row ───
            if ((selectedFilter == "All" || selectedFilter == "Albums") && hasAlbums) {
                item(key = "albums_header") {
                    SectionHeader(
                        title = "Albums",
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 20.dp,
                            bottom = Spacing.headerToContent
                        )
                    )
                }
                item(key = "albums_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(uniqueAlbums, key = { index, album ->
                            val id = album.id.ifBlank { album.title }
                            "album_${id}_$index"
                        }) { _, album ->
                            SearchAlbumTileItem(
                                album = album,
                                onClick = { onAlbumClick(album.id) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }

            // ─── 4. Songs Section ───
            if ((selectedFilter == "All" || selectedFilter == "Songs") && hasSongs) {
                item(key = "songs_header") {
                    SectionHeader(
                        title = "Songs",
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 20.dp,
                            bottom = Spacing.headerToContent
                        )
                    )
                }
                itemsIndexed(uniqueSongs, key = { index, song ->
                    val id = song.id.ifBlank { song.title }
                    "song_${id}_$index"
                }) { _, song ->
                    SongItem(
                        song = song,
                        onClick = { onSongClick(song) },
                        onPlayNext = { onPlayNext(song) },
                        onAddToQueue = { onAddToQueue(song) },
                        onAddToPlaylist = { handleAddToPlaylist(song) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Top Result Card (Cover with Soft Shadow, Tint Gradient, PinkGlow Label)
// ─────────────────────────────────────────────────────────────

@Composable
private fun TopResultCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = remember(song.imageUrl) {
        ImageRequest.Builder(context)
            .data(song.imageUrl)
            .crossfade(true)
            .crossfade(200)
            .build()
    }
    val baseSurface = MaterialTheme.colorScheme.surfaceContainerLow
    val rawTint by rememberArtworkTint(song.imageUrl)
    val blendedTint = remember(rawTint, baseSurface) {
        rawTint.copy(alpha = 0.32f).compositeOver(baseSurface)
    }
    val cardGradient = remember(blendedTint, baseSurface) {
        Brush.horizontalGradient(listOf(blendedTint, baseSurface))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .pressable(
                onClick = onClick,
                pressedScale = MotionTokens.CardPressScale
            )
            .clip(MaterialTheme.shapes.large)
            .background(cardGradient)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(
                        elevation = 4.dp,
                        shape = MaterialTheme.shapes.medium,
                        spotColor = Color.Black.copy(alpha = 0.35f)
                    )
                    .clip(MaterialTheme.shapes.medium)
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TOP RESULT",
                    color = PinkGlow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.primaryArtistName,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Search Artist Circle Item (64dp Plain Circle, 12sp Medium Name)
// ─────────────────────────────────────────────────────────────

@Composable
private fun SearchArtistCircleItem(
    artist: ArtistCredit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = remember(artist.imageUrl) {
        ImageRequest.Builder(context)
            .data(artist.imageUrl)
            .crossfade(true)
            .crossfade(200)
            .build()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(72.dp)
            .pressable(
                onClick = onClick,
                pressedScale = MotionTokens.TilePressScale
            )
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = artist.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Search Album Tile Item (128dp Square, 14sp Medium, Text Below)
// ─────────────────────────────────────────────────────────────

@Composable
private fun SearchAlbumTileItem(
    album: AlbumRef,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = remember(album.imageUrl) {
        ImageRequest.Builder(context)
            .data(album.imageUrl)
            .crossfade(true)
            .crossfade(200)
            .build()
    }

    Column(
        modifier = modifier
            .width(128.dp)
            .pressable(
                onClick = onClick,
                pressedScale = MotionTokens.TilePressScale
            )
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(MaterialTheme.shapes.medium)
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Album",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 4. Skeleton Shimmer Rows for Search Loading State
// ─────────────────────────────────────────────────────────────

@Composable
private fun SearchSkeletonList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .shimmer(MaterialTheme.shapes.small)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                            .clip(MaterialTheme.shapes.small)
                            .shimmer(MaterialTheme.shapes.small)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(12.dp)
                            .clip(MaterialTheme.shapes.small)
                            .shimmer(MaterialTheme.shapes.small)
                    )
                }
            }
        }
    }
}
