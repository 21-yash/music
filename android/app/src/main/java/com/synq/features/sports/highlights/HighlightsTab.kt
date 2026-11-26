package com.synq.features.sports.highlights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.synq.features.sports.domain.model.HighlightSource
import com.synq.features.sports.domain.model.MatchHighlight
import com.synq.features.sports.domain.model.MatchState
import com.synq.ui.SynqLoadingIndicator
import com.synq.ui.motion.MotionTokens
import com.synq.ui.motion.pressable
import com.synq.ui.theme.OnSportsAccent
import com.synq.ui.theme.SportsAccent
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * LazyList items for the Highlights tab inside MatchDetailsScreen.
 * Tapping a clip navigates to the dedicated HighlightPlayerScreen.
 */
fun LazyListScope.highlightsTabItems(
    highlights: List<MatchHighlight>,
    isLoading: Boolean,
    error: String?,
    matchState: MatchState,
    onSelectHighlight: (MatchHighlight) -> Unit,
    onRetryLoad: () -> Unit
) {
    when {
        isLoading && highlights.isEmpty() -> {
            item(key = "highlights_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SynqLoadingIndicator(size = 40.dp, barCount = 5)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Finding official match highlights…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        error != null && highlights.isEmpty() -> {
            item(key = "highlights_error") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onRetryLoad,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SportsAccent,
                                contentColor = OnSportsAccent
                            )
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }

        highlights.isEmpty() -> {
            item(key = "highlights_empty") {
                val emptyText = when (matchState) {
                    MatchState.UPCOMING -> "Highlights will appear once the match begins or finishes"
                    MatchState.LIVE -> "No highlight clips published yet — check back after the innings"
                    else -> "No official highlights available for this match"
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = emptyText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        else -> {
            items(highlights, key = { it.id }) { highlight ->
                HighlightCard(
                    highlight = highlight,
                    isSelected = false,
                    onClick = { onSelectHighlight(highlight) },
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
internal fun HighlightCard(
    highlight: MatchHighlight,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.large
    val borderColor = if (isSelected) {
        SportsAccent
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, pressedScale = MotionTokens.CardPressScale)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, shape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (!highlight.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = highlight.thumbnailUrl,
                    contentDescription = highlight.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (isSelected) 0.35f else 0.18f))
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) SportsAccent else Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play highlight",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            val sourceLabel = when (highlight.source) {
                HighlightSource.BCCI -> "BCCI.TV"
                HighlightSource.IPLT20 -> "IPLT20"
                HighlightSource.YOUTUBE -> "YOUTUBE"
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isSelected) "$sourceLabel • PLAYING" else sourceLabel,
                    color = if (isSelected) SportsAccent else Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (highlight.duration.isNotBlank() && highlight.duration != "0:00") {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = highlight.duration,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = highlight.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            val meta = listOfNotNull(
                highlight.publisher.takeIf { it.isNotBlank() },
                formatHighlightDate(highlight.publishedAt)
            ).joinToString(" • ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

internal fun formatHighlightDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val cleaned = raw.substringBefore('.').removeSuffix("Z")
        val date = parser.parse(cleaned) ?: return raw
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)
    } catch (_: Exception) {
        raw
    }
}
