package com.synq.features.music.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.synq.ui.motion.MotionTokens
import com.synq.ui.motion.pressable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.synq.features.music.domain.model.Song
import com.synq.features.music.jam.LocalCanAddSongsToJam
import com.synq.features.music.jam.LocalIsInJam
import com.synq.ui.theme.PinkPrimary
import com.synq.ui.theme.Spacing

/**
 * Standard Song row composable (§4.6, T2-1, T2-2).
 *
 * - Row container: Flat on background, 8dp vertical spacing, 20dp horizontal padding
 * - Artwork: 48dp, shapes.small (8dp radius)
 * - Title: titleMedium (15sp Medium), onSurface
 * - Subtitle: bodySmall (13sp Normal), onSurfaceVariant
 * - Trailing icon: bare 20dp icon, ≥48dp hit area, onSurfaceVariant
 */
@Composable
fun SongItem(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onLike: (() -> Unit)? = null,
    isLiked: Boolean = false,
    isInJam: Boolean = LocalIsInJam.current
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pressable(onClick = onClick, pressedScale = MotionTokens.RowPressScale)
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.imageUrl,
            contentDescription = "Album art for ${song.title}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.primaryArtistName,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))

        var showOptions by remember { mutableStateOf(false) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (onAddToPlaylist != null) {
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(48.dp)
                        .pressable(
                            onClick = onAddToPlaylist,
                            pressedScale = 0.88f
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddCircleOutline,
                        contentDescription = "Add to playlist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(34.dp)
                    .height(48.dp)
                    .pressable(
                        onClick = { showOptions = true },
                        pressedScale = 0.88f
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (showOptions) {
            SongOptionsSheet(
                song = song,
                isInJam = isInJam,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onAddToPlaylist = onAddToPlaylist,
                onLike = onLike,
                isLiked = isLiked,
                onDismiss = { showOptions = false }
            )
        }
    }
}

val LocalOnNavigateToSongDetails = androidx.compose.runtime.compositionLocalOf<(String, String?) -> Unit> { { _, _ -> } }
val LocalOnNavigateToAlbum = androidx.compose.runtime.compositionLocalOf<(String) -> Unit> { {} }
val LocalToggleLike = androidx.compose.runtime.compositionLocalOf<(Song) -> Unit> { {} }
val LocalLikedSongIds = androidx.compose.runtime.compositionLocalOf<Set<String>> { emptySet() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsSheet(
    song: Song,
    isInJam: Boolean = LocalIsInJam.current,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onLike: (() -> Unit)? = null,
    isLiked: Boolean = false,
    onSongDetails: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    onRemoveFromHistory: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val localAddToPlaylist = LocalAddToPlaylist.current
    val effectiveAddToPlaylist = onAddToPlaylist ?: { localAddToPlaylist(song) }
    val localOnNavigateToSongDetails = LocalOnNavigateToSongDetails.current
    val effectiveOnSongDetails = onSongDetails ?: { localOnNavigateToSongDetails(song.id, song.providerId) }
    val localOnNavigateToAlbum = LocalOnNavigateToAlbum.current
    val effectiveOnGoToAlbum = onGoToAlbum ?: { song.album?.id?.let { localOnNavigateToAlbum(it) } }
    val localToggleLike = LocalToggleLike.current
    val globalLikedIds = LocalLikedSongIds.current
    val isCurrentlyLiked = isLiked || globalLikedIds.contains(song.id)
    val effectiveOnLike: () -> Unit = onLike ?: { localToggleLike(song) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl, vertical = Spacing.l)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = Spacing.m)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.primaryArtistName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(Spacing.m))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(Spacing.xs))
            
            if (isInJam) {
                val canAdd = LocalCanAddSongsToJam.current
                SongOptionItem(
                    icon = Icons.Default.Groups,
                    text = "Add to Jam Queue",
                    enabled = canAdd,
                    onClick = {
                        if (!canAdd) {
                            com.synq.ui.toast.SynqToastManager.show("You don't have permission to add songs to Jam", com.synq.ui.toast.SynqToastType.WARNING)
                        } else {
                            (onAddToQueue ?: onPlayNext)?.invoke()
                            onDismiss()
                        }
                    }
                )
            } else {
                SongOptionItem(
                    icon = Icons.Default.PlayArrow,
                    text = "Play Next",
                    onClick = {
                        onPlayNext?.invoke()
                        com.synq.ui.toast.SynqToastManager.show("Added to play next", com.synq.ui.toast.SynqToastType.SUCCESS)
                        onDismiss()
                    }
                )
                SongOptionItem(
                    icon = Icons.Default.List,
                    text = "Add to Queue",
                    onClick = {
                        onAddToQueue?.invoke()
                        com.synq.ui.toast.SynqToastManager.show("Added to queue", com.synq.ui.toast.SynqToastType.SUCCESS)
                        onDismiss()
                    }
                )
            }
            SongOptionItem(
                icon = Icons.Default.PlaylistAdd,
                text = "Add to Playlist",
                onClick = {
                    effectiveAddToPlaylist()
                    onDismiss()
                }
            )
            SongOptionItem(
                icon = if (isCurrentlyLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                text = if (isCurrentlyLiked) "Remove from Liked" else "Like Song",
                onClick = {
                    effectiveOnLike()
                    onDismiss()
                }
            )
            SongOptionItem(
                icon = Icons.Default.Share,
                text = "Share Song",
                onClick = {
                    val shareUrl = "https://21yash.online/track/${song.id}"
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, shareUrl)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Song"))
                    onDismiss()
                }
            )
            SongOptionItem(
                icon = Icons.Default.Info,
                text = "Song Details",
                onClick = {
                    effectiveOnSongDetails()
                    onDismiss()
                }
            )
            if (song.album != null && song.album.id.isNotBlank() && song.providerId != "youtube") {
                SongOptionItem(
                    icon = Icons.Default.Album,
                    text = "Go to Album",
                    onClick = {
                        effectiveOnGoToAlbum()
                        onDismiss()
                    }
                )
            }
            if (onRemoveFromHistory != null) {
                SongOptionItem(
                    icon = Icons.Default.DeleteOutline,
                    text = "Remove from History",
                    onClick = {
                        onRemoveFromHistory()
                        onDismiss()
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(Spacing.s))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = "Close",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
        }
        com.synq.ui.toast.SynqToastModalOverlay(bottomOffset = 20.dp)
    }
}

@Composable
private fun SongOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.s, horizontal = Spacing.xs)
            .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = PinkPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
