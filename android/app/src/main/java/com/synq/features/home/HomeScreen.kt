package com.synq.features.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import com.synq.RootViewModel
import com.synq.ui.AppHeader
import com.synq.features.music.jam.JamIntroDialog
import com.synq.features.music.jam.JamState
import androidx.compose.material.icons.filled.Groups
import coil.compose.AsyncImage
import java.util.Calendar
import androidx.hilt.navigation.compose.hiltViewModel
import com.synq.features.music.MusicViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.synq.features.music.domain.model.Song
import com.synq.features.music.domain.model.ArtistCredit
import com.synq.ui.SynqLoadingIndicator
import com.synq.ui.components.SectionHeader
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.ReadOnlyComposable
import com.synq.features.music.components.SongItem
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import coil.request.ImageRequest
import com.synq.ui.motion.MotionTokens
import com.synq.ui.motion.performConfirm
import com.synq.ui.motion.pressable
import com.synq.ui.motion.shimmer
import com.synq.ui.motion.pressScale
import com.synq.ui.theme.PinkGradient
import com.synq.ui.theme.SemanticLive
import com.synq.ui.theme.SemanticSuccess
import com.synq.ui.theme.SemanticWarning
import com.synq.ui.theme.SportsAccent
import com.synq.ui.theme.Spacing

// ─────────────────────────────────────────────────────────────
// UI models (move to a `model` package when you wire real data)
// ─────────────────────────────────────────────────────────────

enum class MatchStatus(
    val label: String,
    val pulses: Boolean
) {
    LIVE("LIVE", true),
    UPCOMING("UPCOMING", true),
    POST_MATCH("POST MATCH", false);

    val color: Color
        @Composable
        @ReadOnlyComposable
        get() = when (this) {
            LIVE -> SemanticLive
            UPCOMING -> SportsAccent
            POST_MATCH -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    val highlightColor: Color
        @Composable
        @ReadOnlyComposable
        get() = when (this) {
            LIVE -> SemanticWarning
            else -> SportsAccent
        }
}

data class ScoreUi(val runs: String, val overs: String)

data class MatchUi(
    val id: String = "",
    val title: String,
    val status: MatchStatus,
    /** Any Coil model: a URL String or a drawable resource Int (R.drawable.xxx). */
    val image: Any,
    val scores: List<ScoreUi> = emptyList(),
    /** Shown instead of scores when there are none (e.g. "T20 World Cup"). */
    val subtitle: String? = null,
    val summary: String
)

data class SongUi(val title: String, val artist: String, val imageUrl: String)

// ─────────────────────────────────────────────────────────────
// Sample data (same content as the HTML prototype)
// ─────────────────────────────────────────────────────────────

object HomeSampleData {
    private const val STADIUM =
        "https://images.unsplash.com/photo-1577223625816-7546f13df25d?w=800&q=80"

    val matches = listOf<MatchUi>() // Handled dynamically now


    val recentlyPlayed = listOf(
        SongUi("Tere Hawaale", "Arijit Singh",
            "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500&q=80"),
        SongUi("Softly", "Karan Aujla",
            "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=500&q=80"),
        SongUi("Tauba Tauba", "Karan Aujla",
            "https://images.unsplash.com/photo-1516280440502-861111617266?w=500&q=80")
    )

    val trending = listOf(
        SongUi("O Maahi", "Arijit Singh",
            "https://c.saavncdn.com/955/O-Maahi-From-Dunki-Hindi-2023-20231211171007-150x150.jpg"),
        SongUi("Chaleya", "Anirudh Ravichander",
            "https://c.saavncdn.com/191/Chaleya-From-Jawan-Hindi-2023-20230814014337-150x150.jpg"),
        SongUi("Husn", "Anuv Jain",
            "https://c.saavncdn.com/712/Husn-Hindi-2023-20231129054005-150x150.jpg")
    )
}

// ─────────────────────────────────────────────────────────────
// Home screen
// ─────────────────────────────────────────────────────────────

/**
 * Home tab content only (the profile avatar + menu now live in SynqAppRoot). The floating mini-player and bottom nav bar should live
 * in your root scaffold (SynqAppRoot) so they persist across tabs;
 * [contentPadding] reserves room for them (the prototype uses 160dp).
 */
@Composable
fun HomeScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = MaterialTheme.colorScheme.background == com.synq.ui.theme.DarkBackground,
    contentPadding: PaddingValues = PaddingValues(bottom = 160.dp),
    onSeeAllSports: () -> Unit = {},
    onSeeAllRecentlyPlayed: () -> Unit = {},
    onMatchClick: (String) -> Unit = {},
    onSongClick: (SongUi) -> Unit = {},
    onNavigateToJam: () -> Unit = {},
    homeViewModel: HomeViewModel = hiltViewModel(),
    musicViewModel: MusicViewModel = hiltViewModel(),
    sportsViewModel: com.synq.features.sports.SportsViewModel = hiltViewModel(),
    rootViewModel: RootViewModel = hiltViewModel()
) {
    val uiState by musicViewModel.uiState.collectAsState()
    val sportsUiState by sportsViewModel.uiState.collectAsState()
    val realRecentlyPlayed by homeViewModel.recentlyPlayed.collectAsState()
    val jamState by rootViewModel.jamState.collectAsState()
    var showJamIntro by remember { mutableStateOf(false) }
    val trending = remember(uiState.trendingSongs) { uiState.trendingSongs.distinctBy { it.id } }
    val matches = remember(sportsUiState.matches) { sportsUiState.matches.map { it.toMatchUi() }.distinctBy { it.id } }
    val uniqueRecentlyPlayed = remember(realRecentlyPlayed) {
        realRecentlyPlayed
            .sortedByDescending { it.playedAt }
            .distinctBy { it.songId }
            .take(5)
    }
    val haptic = LocalHapticFeedback.current
    val recentlyPlayedListState = rememberLazyListState()

    val firstSongId = uniqueRecentlyPlayed.firstOrNull()?.songId
    LaunchedEffect(firstSongId) {
        if (firstSongId != null && recentlyPlayedListState.firstVisibleItemIndex == 0 && recentlyPlayedListState.firstVisibleItemScrollOffset == 0) {
            recentlyPlayedListState.requestScrollToItem(0)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(
                title = greetingForNow(),
                subtitle = "Welcome to Synq • Music & Sports",
                rootViewModel = rootViewModel,
                navController = navController,
                showBrandLogo = true
            )
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding
            ) {
                // Skeletons for matches when loading
                if (sportsUiState.isLoading && matches.isEmpty()) {
                    item {
                        SectionHeader(
                            title = "Live Action",
                            contentPadding = PaddingValues(
                                start = Spacing.screenHorizontal,
                                end = Spacing.screenHorizontal,
                                top = 8.dp,
                                bottom = Spacing.headerToContent
                            )
                        )
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = Spacing.screenHorizontal)
                                .width(264.dp)
                                .height(148.dp)
                                .clip(MaterialTheme.shapes.large)
                                .shimmer(MaterialTheme.shapes.large)
                        )
                    }
                }

                // Live Action — only shown when there are active/scheduled matches
                if (matches.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Live Action",
                            actionText = "See all",
                            onActionClick = onSeeAllSports,
                            contentPadding = PaddingValues(
                                start = Spacing.screenHorizontal,
                                end = Spacing.screenHorizontal,
                                top = 8.dp,
                                bottom = Spacing.headerToContent
                            )
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(matches, key = { it.id }) { match ->
                                MatchCard(
                                    match = match,
                                    onClick = { onMatchClick(match.id) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }

                // Recently Played
                item {
                    SectionHeader(
                        title = "Recently Played",
                        actionText = "See all",
                        onActionClick = onSeeAllRecentlyPlayed,
                        contentPadding = PaddingValues(
                            start = Spacing.screenHorizontal,
                            end = Spacing.screenHorizontal,
                            top = if (matches.isNotEmpty()) Spacing.sectionGap else 8.dp,
                            bottom = Spacing.headerToContent
                        )
                    )
                }
                item {
                    LazyRow(
                        state = recentlyPlayedListState,
                        contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (uniqueRecentlyPlayed.isNotEmpty()) {
                            items(uniqueRecentlyPlayed, key = { it.songId }) { item ->
                                val isFirst = item.songId == uniqueRecentlyPlayed.firstOrNull()?.songId
                                val song = remember(item) {
                                    Song(
                                        id = item.songId,
                                        title = item.title,
                                        artists = listOf(ArtistCredit(id = item.artist, name = item.artist, imageUrl = null)),
                                        album = null,
                                        durationSeconds = item.durationSeconds,
                                        imageUrl = item.imageUrl,
                                        year = "",
                                        language = "",
                                        hasLyrics = false,
                                        playCount = 0,
                                        label = "",
                                        streamRef = item.songId,
                                        providerId = item.providerId
                                    )
                                }
                                RecentSongTile(
                                    song = song,
                                    onClick = { homeViewModel.playSong(item) },
                                    onPlayNext = {
                                        haptic.performConfirm()
                                        musicViewModel.playNext(song)
                                    },
                                    onAddToQueue = {
                                        haptic.performConfirm()
                                        musicViewModel.addToQueue(song)
                                    },
                                    modifier = Modifier
                                        .zIndex(if (isFirst) 1f else 0f)
                                        .animateItem(
                                            fadeInSpec = null,
                                            fadeOutSpec = null,
                                            placementSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                )
                            }
                        } else {
                            itemsIndexed(HomeSampleData.recentlyPlayed.take(5), key = { _, s -> s.title }) { index, sampleSong ->
                                val isFirst = index == 0
                                val song = remember(sampleSong) {
                                    Song(
                                        id = sampleSong.title.lowercase().replace(" ", "_"),
                                        title = sampleSong.title,
                                        artists = listOf(ArtistCredit(id = sampleSong.artist, name = sampleSong.artist, imageUrl = null)),
                                        album = null,
                                        durationSeconds = 180,
                                        imageUrl = sampleSong.imageUrl,
                                        year = "",
                                        language = "",
                                        hasLyrics = false,
                                        playCount = 0,
                                        label = "",
                                        streamRef = sampleSong.title.lowercase().replace(" ", "_"),
                                        providerId = "saavn"
                                    )
                                }
                                RecentSongTile(
                                    song = song,
                                    onClick = { onSongClick(sampleSong) },
                                    onPlayNext = {
                                        haptic.performConfirm()
                                        musicViewModel.playNext(song)
                                    },
                                    onAddToQueue = {
                                        haptic.performConfirm()
                                        musicViewModel.addToQueue(song)
                                    },
                                    modifier = Modifier
                                        .zIndex(if (isFirst) 1f else 0f)
                                        .animateItem(
                                            fadeInSpec = null,
                                            fadeOutSpec = null,
                                            placementSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                )
                            }
                        }
                    }
                }

                // Jam Section
                item {
                    SectionHeader(
                        title = "Jam",
                        contentPadding = PaddingValues(
                            start = Spacing.screenHorizontal,
                            end = Spacing.screenHorizontal,
                            top = Spacing.sectionGap,
                            bottom = Spacing.headerToContent
                        )
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            JamCard(
                                jamState = jamState,
                                onClick = {
                                    if (jamState.isInJam) {
                                        onNavigateToJam()
                                    } else {
                                        showJamIntro = true
                                    }
                                }
                            )
                        }
                    }
                }

                // Trending Music
                item {
                    SectionHeader(
                        title = "Trending Music",
                        contentPadding = PaddingValues(
                            start = Spacing.screenHorizontal,
                            end = Spacing.screenHorizontal,
                            top = Spacing.sectionGap,
                            bottom = Spacing.headerToContent
                        )
                    )
                }

                if (uiState.isLoading && trending.isEmpty()) {
                    items(3) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
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

                if (uiState.error != null && trending.isEmpty()) {
                    item {
                        Text(
                            text = uiState.error ?: "Failed to load trending songs",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = Spacing.screenHorizontal)
                        )
                    }
                }

                itemsIndexed(
                    trending,
                    key = { index, song -> if (song.id.isNotBlank()) "${song.id}_$index" else "trending_$index" }
                ) { _, song ->
                    TrendingSongRow(
                        song = song,
                        onClick = { musicViewModel.playSong(song) },
                        onPlayNext = {
                            haptic.performConfirm()
                            musicViewModel.playNext(song)
                        },
                        onAddToQueue = {
                            haptic.performConfirm()
                            musicViewModel.addToQueue(song)
                        },
                        modifier = Modifier.animateItem(placementSpec = MotionTokens.ItemPlacementSpec)
                    )
                }
            }
        }

            if (showJamIntro) {
                JamIntroDialog(
                    onDismiss = { showJamIntro = false },
                    onStartJam = { onComplete ->
                        haptic.performConfirm()
                        rootViewModel.startJam(onComplete = onComplete)
                    },
                    onJoinJam = { code, onComplete ->
                        haptic.performConfirm()
                        rootViewModel.joinJam(code, onComplete = onComplete)
                    },
                    onSuccessNav = {
                        showJamIntro = false
                        onNavigateToJam()
                    }
                )
            }
        }
    }

// ─────────────────────────────────────────────────────────────
// Header greeting helper
// ─────────────────────────────────────────────────────────────

private fun greetingForNow(): String =
    when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }



// ─────────────────────────────────────────────────────────────
// Live Action: match card
// ─────────────────────────────────────────────────────────────

@Composable
private fun MatchCard(
    match: MatchUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.large
    val context = LocalContext.current
    val imageRequest = remember(match.image) {
        if (match.image is Int) match.image
        else ImageRequest.Builder(context)
            .data(match.image)
            .crossfade(true)
            .crossfade(200)
            .build()
    }

    Box(
        modifier = modifier
            .pressable(
                onClick = onClick,
                pressedScale = MotionTokens.CardPressScale
            )
            .width(264.dp)
            .height(148.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        // Stadium image (instant synchronous render if local drawable, async if URL)
        if (match.image is Int) {
            Image(
                painter = painterResource(id = match.image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.75f,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .scale(1.05f)
                    .blur(2.dp)
            )
        } else {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.75f,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .scale(1.05f)
                    .blur(2.dp)
            )
        }

        // Bottom gradient
        val bgColor = MaterialTheme.colorScheme.background
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to bgColor.copy(alpha = 0.7f),
                        0.95f to bgColor.copy(alpha = 0.95f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom)
        ) {
            StatusBadge(match.status)

            Text(
                text = match.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )

            if (match.scores.isNotEmpty()) {
                Text(
                    text = scoreText(match.scores),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            } else if (match.subtitle != null) {
                Text(
                    text = match.subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }

            Text(
                text = match.summary,
                color = match.status.highlightColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp).basicMarquee()
            )
        }
    }
}

@Composable
private fun scoreText(scores: List<ScoreUi>): AnnotatedString {
    val scoreColor = MaterialTheme.colorScheme.onSurface
    val subtextColor = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(scores, scoreColor, subtextColor) {
        buildAnnotatedString {
            scores.forEachIndexed { index, score ->
                if (index > 0) append("  ·  ")
                val isYetToBat = score.runs.contains("yet to bat", ignoreCase = true)
                if (isYetToBat) {
                    withStyle(SpanStyle(color = subtextColor, fontWeight = FontWeight.Normal, fontSize = 13.sp)) {
                        append(score.runs)
                    }
                } else {
                    withStyle(SpanStyle(color = scoreColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                        append(score.runs)
                    }
                    if (score.overs.isNotBlank()) {
                        withStyle(SpanStyle(color = subtextColor, fontWeight = FontWeight.Normal, fontSize = 12.sp)) {
                            append(" (${score.overs})")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: MatchStatus) {
    val pillShape = CircleShape

    val transition = rememberInfiniteTransition(label = "badgePulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Reverse),
        label = "dotAlpha"
    )

    val badgeColor = status.color
    Row(
        modifier = Modifier
            .clip(pillShape)
            .background(badgeColor.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .alpha(if (status.pulses) pulse else 1f)
                .clip(CircleShape)
                .background(badgeColor)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = status.label,
            color = badgeColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Recently Played tile
// ─────────────────────────────────────────────────────────────

@Composable
private fun RecentSongTile(
    song: Song,
    onClick: () -> Unit,
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showOptions by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val imageRequest = remember(song.imageUrl) {
        ImageRequest.Builder(context)
            .data(song.imageUrl)
            .crossfade(true)
            .crossfade(200)
            .build()
    }

    Column(
        modifier = modifier
            .pressable(
                onClick = onClick,
                pressedScale = MotionTokens.TilePressScale
            )
            .width(128.dp)
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(MaterialTheme.shapes.medium)
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // 3-dots button at top-end
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showOptions = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options for ${song.title}",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = song.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.primaryArtistName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (showOptions) {
        com.synq.features.music.components.SongOptionsSheet(
            song = song,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onDismiss = { showOptions = false }
        )
    }
}

@Composable
private fun JamCard(
    jamState: JamState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val jamGradient = if (jamState.isInJam) {
        Brush.verticalGradient(listOf(Color(0xFF059669), Color(0xFF022C22)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF2563EB), Color(0xFF06B6D4)))
    }

    Column(
        modifier = modifier
            .pressable(
                onClick = onClick,
                pressedScale = MotionTokens.TilePressScale
            )
            .width(128.dp)
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(jamGradient),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = "Jam",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Jam Session",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (jamState.isInJam) "${jamState.participants.size} listening" else "Start or join",
            color = if (jamState.isInJam) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Trending Music row
// ─────────────────────────────────────────────────────────────

@Composable
private fun TrendingSongRow(
    song: Song,
    onClick: () -> Unit,
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    SongItem(
        song = song,
        onClick = onClick,
        onPlayNext = onPlayNext,
        onAddToQueue = onAddToQueue,
        modifier = modifier
    )
}

private fun com.synq.features.sports.domain.model.MatchSummary.toMatchUi(): MatchUi {
    val status = when (state) {
        com.synq.features.sports.domain.model.MatchState.LIVE -> MatchStatus.LIVE
        com.synq.features.sports.domain.model.MatchState.UPCOMING -> MatchStatus.UPCOMING
        else -> MatchStatus.POST_MATCH
    }

    val scores = mutableListOf<ScoreUi>()
    if (!team1Score.isNullOrBlank()) {
        val parts = team1Score.split("(")
        scores.add(ScoreUi(parts[0].trim(), if (parts.size > 1) parts[1].removeSuffix(")") else ""))
    }
    if (!team2Score.isNullOrBlank()) {
        val parts = team2Score.split("(")
        scores.add(ScoreUi(parts[0].trim(), if (parts.size > 1) parts[1].removeSuffix(")") else ""))
    }

    val stadiumDrawables = listOf(
        com.synq.R.drawable.stadium_1,
        com.synq.R.drawable.stadium_2,
        com.synq.R.drawable.stadium_3,
        com.synq.R.drawable.stadium_4,
        com.synq.R.drawable.stadium_5
    )
    val stadiumImg = stadiumDrawables[kotlin.math.abs(id.hashCode()) % stadiumDrawables.size]

    return MatchUi(
        id = id,
        title = "${team1.shortName} vs ${team2.shortName}",
        status = status,
        image = stadiumImg,
        scores = scores,
        subtitle = matchDesc,
        summary = this.status
    )
}