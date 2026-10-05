package com.musicsportsapp.features.sports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.musicsportsapp.features.sports.domain.model.MatchSummary

/**
 * Sports tab — live scores and match tracking.
 *
 * Layout intentionally matches the approved reference: status/urgency line sits in the
 * card header next to the series name (not below the scores) so the most time-sensitive
 * info reads first.
 */
@Composable
fun SportsScreen(
    viewModel: SportsViewModel = hiltViewModel(),
    onMatchClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFDEFF2), // soft pink, top
            Color(0xFFEDF0FF), // soft blue-lavender, middle
            Color(0xFFF8F9FE)  // near-white, bottom
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp)
            ) {
                Text(
                    text = "Sports",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1A1D2D)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Live scores  •  Latest updates  •  Cricket",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280),
                    fontWeight = FontWeight.Medium
                )
            }

            // Filter chips — horizontally scrollable
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterPill(
                    label = "Live",
                    isSelected = uiState.selectedFilter == "live",
                    onClick = { viewModel.onFilterSelected("live") },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Circle,
                            contentDescription = null,
                            modifier = Modifier.size(8.dp),
                            tint = if (uiState.selectedFilter == "live") Color.White else Color(0xFFEF4444)
                        )
                    }
                )
                FilterPill(
                    label = "Upcoming",
                    isSelected = uiState.selectedFilter == "upcoming",
                    onClick = { viewModel.onFilterSelected("upcoming") },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (uiState.selectedFilter == "upcoming") Color.White else Color(0xFF4B5563)
                        )
                    }
                )
                FilterPill(
                    label = "Recent",
                    isSelected = uiState.selectedFilter == "recent",
                    onClick = { viewModel.onFilterSelected("recent") },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (uiState.selectedFilter == "recent") Color.White else Color(0xFF4B5563)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    uiState.isLoading && uiState.matches.isEmpty() -> {
                        CircularProgressIndicator(color = Color(0xFF4338CA))
                    }
                    uiState.error != null && uiState.matches.isEmpty() -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.retry() }) { Text("Retry") }
                        }
                    }
                    uiState.matches.isEmpty() -> {
                        Text(text = "No matches found.", color = Color(0xFF6B7280))
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(
                                count = uiState.matches.size,
                                key = { index -> uiState.matches[index].id }
                            ) { index ->
                                val match = uiState.matches[index]
                                // Alternate tint for visual rhythm between cards —
                                // purely decorative, not tied to match state.
                                val tint = if (index % 2 == 0) Color(0xFFEBEEFD) else Color(0xFFFDEDF0)
                                MatchCard(
                                    match = match,
                                    backgroundTint = tint,
                                    onClick = { onMatchClick(match.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val bgModifier = if (isSelected) {
        Modifier.background(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))
            ),
            shape = RoundedCornerShape(24.dp)
        )
    } else {
        Modifier
            .background(Color.Transparent, RoundedCornerShape(24.dp))
            .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(24.dp))
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .then(bgModifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        icon()
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = if (isSelected) Color.White else Color(0xFF4B5563),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun MatchCard(
    match: MatchSummary,
    backgroundTint: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundTint),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header row: tournament/series name on the left, format badge on the right.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = match.seriesName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6B7280),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .background(Color(0xFFF3E8FF), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = match.format.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9333EA)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TeamRow(name = match.team1.name, score = match.team1Score ?: "", logoUrl = match.team1.logoUrl)
            Spacer(modifier = Modifier.height(12.dp))
            TeamRow(name = match.team2.name, score = match.team2Score ?: "", logoUrl = match.team2.logoUrl)

            // Status line — moved back to the bottom, below the scores.
            if (match.isLive && match.status.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Circle,
                        contentDescription = "Live",
                        modifier = Modifier.size(8.dp),
                        tint = Color(0xFFEF4444)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = match.status,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamRow(name: String, score: String, logoUrl: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        ) {
            Card(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6))
            ) {
                if (logoUrl != null) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = "$name logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111827),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (score.isBlank()) {
            Text(
                text = "Yet to bat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF111827)
            )
        } else {
            val scoreParts = score.split("(")
            val runsText = scoreParts[0].trim()
            val oversText = if (scoreParts.size > 1) "(${scoreParts[1]}" else ""

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = runsText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                if (oversText.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = oversText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF6B7280),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}