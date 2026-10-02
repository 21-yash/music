package com.musicsportsapp.features.sports

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Sports tab — live scores and match tracking.
 *
 * Will be populated in Phases 7-9 with real match data from the
 * cricket (and later other sports) provider. For now, shows the
 * structural layout with Live / Upcoming / Completed filter chips
 * and placeholder match cards.
 */
@Composable
fun SportsScreen() {
    var selectedFilter by remember { mutableIntStateOf(0) }
    val filters = listOf("Live", "Upcoming", "Completed")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item {
            Text(
                text = "Sports",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        // Filter chips
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filters.forEachIndexed { index, label ->
                    FilterChip(
                        selected = selectedFilter == index,
                        onClick = { selectedFilter = index },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        leadingIcon = if (index == 0 && selectedFilter == 0) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Circle,
                                    contentDescription = null,
                                    modifier = Modifier.size(8.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Placeholder match cards
        items(6) { index ->
            MatchCard(
                team1 = "Team A",
                team2 = "Team B",
                score1 = if (selectedFilter != 1) "${(index * 37 + 120) % 300}/${(index * 2 + 3) % 10}" else "—",
                score2 = if (selectedFilter == 2) "${(index * 41 + 90) % 280}/${(index * 3 + 2) % 10}" else "—",
                status = when (selectedFilter) {
                    0 -> "Live • ${index + 1}st Innings"
                    1 -> "Starts in ${index + 1}h"
                    else -> "Team A won by ${(index * 13 + 20) % 100} runs"
                },
                isLive = selectedFilter == 0,
            )
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun MatchCard(
    team1: String,
    team2: String,
    score1: String,
    score2: String,
    status: String,
    isLive: Boolean,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Status row
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isLive) {
                    Icon(
                        imageVector = Icons.Filled.Circle,
                        contentDescription = "Live",
                        modifier = Modifier.size(8.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isLive) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isLive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Team scores
            TeamRow(name = team1, score = score1)
            Spacer(modifier = Modifier.height(8.dp))
            TeamRow(name = team2, score = score2)
        }
    }
}

@Composable
private fun TeamRow(name: String, score: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Team logo placeholder
            Card(
                modifier = Modifier.size(28.dp),
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {}
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = score,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
