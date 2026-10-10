package com.musicsportsapp.features.music.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musicsportsapp.features.music.playback.PlaybackState
import com.musicsportsapp.ui.GlassProgressBrush
import com.musicsportsapp.ui.glassContentColors
import com.musicsportsapp.ui.glassSurface

/**
 * Floating glass mini-player (prototype style).
 *
 * [onPreviousClick] / [onNextClick] are optional so existing call sites keep compiling;
 * hook them up to your player when ready.
 */
@Composable
fun MiniPlayer(
    playbackState: PlaybackState,
    onPlayPauseClick: () -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
    onPreviousClick: () -> Unit = {},
    onNextClick: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = playbackState.currentSong != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it }
    ) {
        val song = playbackState.currentSong ?: return@AnimatedVisibility
        val colors = glassContentColors()
        val shape = RoundedCornerShape(16.dp)

        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                .glassSurface(shape)
                .clickable(onClick = onNavigateToNowPlaying)
        ) {
            // Progress line along the top edge: faint track + indigo→pink fill
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(colors.track)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(playbackState.progress.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(GlassProgressBrush)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = colors.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = song.primaryArtistName,
                        color = colors.secondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }

                // previous · play/pause · next
                IconButton(onClick = onPreviousClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = colors.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = colors.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                IconButton(onClick = onNextClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = colors.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}