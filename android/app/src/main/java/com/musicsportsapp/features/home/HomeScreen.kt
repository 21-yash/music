package com.musicsportsapp.features.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
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
import coil.compose.AsyncImage
import java.util.Calendar
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicsportsapp.features.music.MusicViewModel
import androidx.compose.runtime.collectAsState
import com.musicsportsapp.features.music.domain.model.Song
import androidx.compose.material3.CircularProgressIndicator

// ─────────────────────────────────────────────────────────────
// UI models (move to a `model` package when you wire real data)
// ─────────────────────────────────────────────────────────────

enum class MatchStatus(
    val label: String,
    val color: Color,
    val highlightColor: Color,
    val pulses: Boolean
) {
    LIVE("LIVE", Color(0xFFFF4757), Color(0xFFFBBF24), true),
    UPCOMING("UPCOMING", Color(0xFF3B82F6), Color(0xFFA3E635), true),
    POST_MATCH("POST MATCH", Color(0xFF9CA3AF), Color(0xFFA3E635), false)
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
// Theme palette (dark + light, mirrors the prototype's CSS vars)
// ─────────────────────────────────────────────────────────────

private class HomePalette(
    val background: Color,
    val glow: Color,
    val surface: Color,
    val surfaceBorder: Color,
    val surfaceHighlight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color
)

private val AccentIndigo = Color(0xFF6366F1)
private val StadiumNavy = Color(0xFF0F172A)

private val DarkPalette = HomePalette(
    background = Color(0xFF09090B),
    glow = Color(0xFF1A1A2E),
    surface = Color.White.copy(alpha = 0.03f),
    surfaceBorder = Color.White.copy(alpha = 0.05f),
    surfaceHighlight = Color.White.copy(alpha = 0.10f),
    textPrimary = Color.White,
    textSecondary = Color(0xFFA1A1AA),
    accent = AccentIndigo
)

private val LightPalette = HomePalette(
    background = Color(0xFFF8FAFC),
    glow = Color(0xFFE2E8F0),
    surface = Color.White,
    surfaceBorder = Color.Black.copy(alpha = 0.05f),
    surfaceHighlight = Color.Black.copy(alpha = 0.10f),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    accent = AccentIndigo
)

private val LocalHomePalette = staticCompositionLocalOf { DarkPalette }

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
 * Home tab content only (the profile avatar + menu now live in MusicSportsAppRoot). The floating mini-player and bottom nav bar should live
 * in your root scaffold (MusicSportsAppRoot) so they persist across tabs;
 * [contentPadding] reserves room for them (the prototype uses 160dp).
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = androidx.compose.material3.MaterialTheme.colorScheme.background == Color(0xFF0F172A),
    recentlyPlayed: List<SongUi> = HomeSampleData.recentlyPlayed,
    contentPadding: PaddingValues = PaddingValues(bottom = 160.dp),
    onSeeAllSports: () -> Unit = {},
    onMatchClick: (String) -> Unit = {},
    onSongClick: (SongUi) -> Unit = {},
    musicViewModel: MusicViewModel = hiltViewModel(),
    sportsViewModel: com.musicsportsapp.features.sports.SportsViewModel = hiltViewModel()
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val uiState by musicViewModel.uiState.collectAsState()
    val sportsUiState by sportsViewModel.uiState.collectAsState()
    val trending = uiState.trendingSongs
    val matches = sportsUiState.matches.map { it.toMatchUi() }

    CompositionLocalProvider(LocalHomePalette provides palette) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(palette.background)
                // radial glow from the top-right corner, like the CSS background
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(palette.glow, Color.Transparent),
                            center = Offset(size.width, 0f),
                            radius = size.width * 1.3f
                        )
                    )
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                HomeHeader()
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding
                ) {
                    // Live Action
                item { SectionHeader("Live Action", actionText = "See all", onActionClick = onSeeAllSports) }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(matches) { match ->
                            MatchCard(match = match, onClick = { onMatchClick(match.id) })
                        }
                    }
                }

                // Recently Played
                item { SectionHeader("Recently Played") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(recentlyPlayed) { song ->
                            RecentSongTile(song = song, onClick = { onSongClick(song) })
                        }
                    }
                }

                // Trending Music
                item { SectionHeader("Trending Music") }
                
                if (uiState.isLoading && trending.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = palette.accent)
                        }
                    }
                }
                
                if (uiState.error != null && trending.isEmpty()) {
                    item {
                        Text(
                            text = uiState.error ?: "Failed to load trending songs",
                            color = Color(0xFFEF4444),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }

                items(trending) { song ->
                    TrendingSongRow(
                        song = song,
                        onClick = { musicViewModel.playSong(song) },
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                    )
                }
            }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Header + profile menu
// ─────────────────────────────────────────────────────────────

private fun greetingForNow(): String =
    when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

@Composable
private fun HomeHeader() {
    val palette = LocalHomePalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            // end padding leaves room for the global avatar floating in MusicSportsAppRoot
            .padding(start = 24.dp, end = 80.dp, top = 16.dp, bottom = 8.dp)
    ) {
        Text(
            text = greetingForNow(),
            color = palette.textPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(0.dp))
        Text(
            text = "Ready for some music and sports?",
            color = palette.textSecondary,
            fontSize = 14.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: () -> Unit = {}
) {
    val palette = LocalHomePalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        if (actionText != null) {
            Text(
                text = actionText,
                color = palette.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onActionClick)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Live Action: match card
// ─────────────────────────────────────────────────────────────

@Composable
private fun MatchCard(match: MatchUi, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .width(280.dp)
            .height(180.dp)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(StadiumNavy)
            .border(1.dp, Color.White.copy(alpha = 0.15f), shape)
            .clickable(onClick = onClick)
    ) {
        // Blurred stadium image (blur needs Android 12+; older versions just show it sharp)
        AsyncImage(
            model = match.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.85f,
            modifier = Modifier
                .fillMaxSize()
                .scale(1.05f)
                .blur(2.5.dp)
        )

        // Bottom gradient
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to StadiumNavy.copy(alpha = 0.6f),
                        0.9f to StadiumNavy.copy(alpha = 0.95f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom)
        ) {
            StatusBadge(match.status)

            Text(
                text = match.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )

            if (match.scores.isNotEmpty()) {
                Text(
                    text = scoreText(match.scores),
                    color = Color(0xFF94A3B8),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            } else if (match.subtitle != null) {
                Text(
                    text = match.subtitle,
                    color = Color(0xFF94A3B8),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = match.summary,
                color = match.status.highlightColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private fun scoreText(scores: List<ScoreUi>): AnnotatedString = buildAnnotatedString {
    scores.forEachIndexed { index, score ->
        if (index > 0) append("  ·  ")
        withStyle(SpanStyle(color = Color(0xFFE2E8F0), fontWeight = FontWeight.Bold)) {
            append(score.runs)
        }
        if (score.overs.isNotBlank()) {
            append(" (${score.overs})")
        }
    }
}

@Composable
private fun StatusBadge(status: MatchStatus) {
    val pillShape = RoundedCornerShape(100.dp)

    val transition = rememberInfiniteTransition(label = "badgePulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Reverse),
        label = "dotAlpha"
    )

    Row(
        modifier = Modifier
            .clip(pillShape)
            .background(status.color.copy(alpha = 0.15f))
            .border(1.dp, status.color.copy(alpha = 0.4f), pillShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .alpha(if (status.pulses) pulse else 1f)
                .clip(CircleShape)
                .background(status.color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = status.label,
            color = status.color,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Recently Played tile
// ─────────────────────────────────────────────────────────────

@Composable
private fun RecentSongTile(song: SongUi, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .width(140.dp)
            .height(160.dp)
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = song.imageUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f), Color.Black.copy(alpha = 0.9f))
                    )
                )
                .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 16.dp)
        ) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Trending Music row
// ─────────────────────────────────────────────────────────────

@Composable
private fun TrendingSongRow(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalHomePalette.current
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.surfaceBorder, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.imageUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.primaryArtistName,
                color = palette.textSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(palette.surfaceHighlight)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play ${song.title}",
                tint = palette.textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun com.musicsportsapp.features.sports.domain.model.MatchSummary.toMatchUi(): MatchUi {
    val status = when (state) {
        com.musicsportsapp.features.sports.domain.model.MatchState.LIVE -> MatchStatus.LIVE
        com.musicsportsapp.features.sports.domain.model.MatchState.UPCOMING -> MatchStatus.UPCOMING
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

    return MatchUi(
        id = id,
        title = "${team1.shortName} vs ${team2.shortName}",
        status = status,
        image = "https://images.unsplash.com/photo-1577223625816-7546f13df25d?w=800&q=80",
        scores = scores,
        subtitle = matchDesc,
        summary = this.status
    )
}