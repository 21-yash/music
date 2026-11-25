package com.synq.features.music.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synq.data.local.db.entity.PlaylistEntity
import com.synq.features.music.domain.model.Song
import com.synq.ui.isAppDark
import com.synq.ui.motion.MotionTokens
import com.synq.ui.motion.performConfirm
import com.synq.ui.motion.performToggle
import com.synq.ui.motion.pressable
import com.synq.ui.theme.PinkDeep
import com.synq.ui.theme.PinkGradientHero
import com.synq.ui.theme.PinkPrimary
import com.synq.ui.theme.Spacing

private enum class PlaylistSortMode(val label: String) {
    DEFAULT("Default"),
    NAME("Alphabetical (A-Z)"),
    SONG_COUNT("Most Songs"),
    RECENTLY_UPDATED("Recently Updated"),
    RECENTLY_CREATED("Recently Created")
}

/**
 * Add-to-Playlist bottom sheet (§3.2, §5: Reference visual north star).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    song: Song,
    playlists: List<PlaylistEntity>,
    savedPlaylistIds: Set<String> = emptySet(),
    isLiked: Boolean = false,
    onToggleLike: () -> Unit = {},
    onSelectPlaylist: (PlaylistEntity) -> Unit,
    onRemoveFromPlaylist: (PlaylistEntity) -> Unit = {},
    onCreatePlaylist: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = isAppDark()
    val haptic = LocalHapticFeedback.current
    val pinkAccent = if (isDark) PinkPrimary else PinkDeep

    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(PlaylistSortMode.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val bgCard = MaterialTheme.colorScheme.surfaceContainerHigh
    val searchBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    // Sorted and filtered playlists
    val sortedPlaylists = remember(playlists, sortMode) {
        when (sortMode) {
            PlaylistSortMode.DEFAULT -> playlists
            PlaylistSortMode.NAME -> playlists.sortedBy { it.name.lowercase() }
            PlaylistSortMode.SONG_COUNT -> playlists.sortedByDescending { it.songCount }
            PlaylistSortMode.RECENTLY_UPDATED -> playlists.sortedByDescending { it.updatedAt }
            PlaylistSortMode.RECENTLY_CREATED -> playlists.sortedByDescending { it.createdAt }
        }
    }

    val savedPlaylists = remember(sortedPlaylists, savedPlaylistIds) {
        sortedPlaylists.filter { savedPlaylistIds.contains(it.serverId) }
    }

    val unsavedPlaylists = remember(sortedPlaylists, savedPlaylistIds) {
        sortedPlaylists.filter { !savedPlaylistIds.contains(it.serverId) }
    }

    val matchesLikedSongs = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            false
        } else {
            "liked songs".contains(searchQuery.trim(), ignoreCase = true) ||
                "liked".contains(searchQuery.trim(), ignoreCase = true)
        }
    }

    val matchingSearchResults = remember(sortedPlaylists, searchQuery) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            sortedPlaylists.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = bgCard,
        shape = MaterialTheme.shapes.extraLarge,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.s)
        ) {
            // Header: Cancel (left), drag handle (center), Done (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .pressable(
                            pressedScale = 0.90f,
                            onClick = onDismiss
                        )
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cancel",
                        color = textSecondary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(textSecondary.copy(alpha = 0.4f))
                )

                Box(
                    modifier = Modifier
                        .pressable(
                            pressedScale = 0.90f,
                            onClick = {
                                haptic.performConfirm()
                                onDismiss()
                            }
                        )
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Done",
                        color = pinkAccent,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Title row: "Saved in" + "New playlist"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Saved in",
                    style = MaterialTheme.typography.headlineSmall,
                    color = textPrimary
                )

                Box(
                    modifier = Modifier
                        .pressable(
                            pressedScale = 0.90f,
                            onClick = {
                                haptic.performConfirm()
                                showCreateDialog = true
                            }
                        )
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "New playlist",
                        color = pinkAccent,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // ─── 1. SAVED ITEMS (Shown ABOVE the search bar) ───
                if (searchQuery.isBlank()) {
                    // Liked Songs row if already liked (shared key slides smoothly across search bar)
                    if (isLiked) {
                        item(key = "liked_songs") {
                            LikedSongRow(
                                isLiked = true,
                                onToggle = {
                                    haptic.performToggle(false)
                                    onToggleLike()
                                },
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                modifier = sheetItemAnimateModifier()
                            )
                        }
                    }

                    // Playlists where the song is already saved (shared key slides smoothly across search bar)
                    items(savedPlaylists, key = { "playlist_${it.serverId}" }) { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            isSaved = true,
                            onToggle = {
                                haptic.performToggle(false)
                                onRemoveFromPlaylist(playlist)
                            },
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            modifier = sheetItemAnimateModifier()
                        )
                    }
                }

                // ─── 2. SEARCH & SORT BAR ───
                item(key = "search_and_sort_bar") {
                    Column(modifier = sheetItemAnimateModifier()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(searchBg)
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = textPrimary),
                                        singleLine = true,
                                        cursorBrush = SolidColor(PinkPrimary),
                                        modifier = Modifier.weight(1f),
                                        decorationBox = { innerTextField ->
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = "Find a playlist",
                                                    color = textSecondary.copy(alpha = 0.7f),
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                    AnimatedVisibility(
                                        visible = searchQuery.isNotEmpty(),
                                        enter = scaleIn(
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        ) + fadeIn(),
                                        exit = scaleOut() + fadeOut()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .pressable(
                                                    pressedScale = MotionTokens.ChipPressScale,
                                                    onClick = { searchQuery = "" }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear search",
                                                tint = textSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Sort button
                            Box {
                                Box(
                                    modifier = Modifier
                                        .pressable(
                                            pressedScale = 0.90f,
                                            onClick = { showSortMenu = true }
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (sortMode == PlaylistSortMode.DEFAULT) "Sort" else sortMode.label.substringBefore(" "),
                                        color = if (sortMode == PlaylistSortMode.DEFAULT) textSecondary else pinkAccent,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    containerColor = bgCard
                                ) {
                                    PlaylistSortMode.values().forEach { mode ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = mode.label,
                                                    color = if (sortMode == mode) pinkAccent else textPrimary,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            },
                                            trailingIcon = if (sortMode == mode) {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = pinkAccent,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            } else null,
                                            onClick = {
                                                haptic.performConfirm()
                                                sortMode = mode
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                // ─── 3. SEARCH RESULTS OR UNSAVED ITEMS ───
                if (searchQuery.isNotBlank()) {
                    // Search mode: show matching Liked Songs if applicable
                    if (matchesLikedSongs) {
                        item(key = "liked_songs") {
                            LikedSongRow(
                                isLiked = isLiked,
                                onToggle = {
                                    haptic.performToggle(!isLiked)
                                    onToggleLike()
                                },
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                modifier = sheetItemAnimateModifier()
                            )
                        }
                    }

                    // Search mode: matching playlists
                    if (matchingSearchResults.isEmpty() && !matchesLikedSongs) {
                        item(key = "no_search_results") {
                            Box(
                                modifier = Modifier
                                    .then(sheetItemAnimateModifier())
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No playlists found",
                                    color = textSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        items(matchingSearchResults, key = { "playlist_${it.serverId}" }) { playlist ->
                            val isSaved = savedPlaylistIds.contains(playlist.serverId)
                            PlaylistRow(
                                playlist = playlist,
                                isSaved = isSaved,
                                onToggle = {
                                    haptic.performToggle(!isSaved)
                                    if (isSaved) {
                                        onRemoveFromPlaylist(playlist)
                                    } else {
                                        onSelectPlaylist(playlist)
                                    }
                                },
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                modifier = sheetItemAnimateModifier()
                            )
                        }
                    }
                } else {
                    // Default mode: Unsaved Liked Songs row (if not already liked)
                    if (!isLiked) {
                        item(key = "liked_songs") {
                            LikedSongRow(
                                isLiked = false,
                                onToggle = {
                                    haptic.performToggle(true)
                                    onToggleLike()
                                },
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                modifier = sheetItemAnimateModifier()
                            )
                        }
                    }

                    // Default mode: Unsaved playlists
                    items(unsavedPlaylists, key = { "playlist_${it.serverId}" }) { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            isSaved = false,
                            onToggle = {
                                haptic.performToggle(true)
                                onSelectPlaylist(playlist)
                            },
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            modifier = sheetItemAnimateModifier()
                        )
                    }

                    if (playlists.isEmpty() && !isLiked) {
                        item(key = "empty_playlists") {
                            Box(
                                modifier = Modifier
                                    .then(sheetItemAnimateModifier())
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No playlists yet. Create one above!",
                                    color = textSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            defaultName = "Playlist #${playlists.size + 1}",
            onDismiss = { showCreateDialog = false },
            onCreate = { name, _ ->
                onCreatePlaylist(name)
                showCreateDialog = false
            }
        )
    }
}

private fun LazyItemScope.sheetItemAnimateModifier(): Modifier =
    Modifier.animateItem(
        fadeInSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        fadeOutSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        placementSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

@Composable
private fun SavedToggleBadge(
    isSaved: Boolean,
    savedContentDescription: String,
    unsavedContentDescription: String,
    textSecondary: Color
) {
    val popScale = remember { Animatable(1f) }
    var hasRenderedInitial by remember { mutableStateOf(false) }

    LaunchedEffect(isSaved) {
        if (!hasRenderedInitial) {
            hasRenderedInitial = true
            return@LaunchedEffect
        }
        popScale.snapTo(if (isSaved) 0.78f else 0.88f)
        popScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.45f,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    val containerColor by animateColorAsState(
        targetValue = if (isSaved) PinkPrimary else Color.Transparent,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "badgeBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSaved) PinkPrimary else textSecondary.copy(alpha = 0.5f),
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "badgeBorder"
    )

    Box(
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer {
                scaleX = popScale.value
                scaleY = popScale.value
            }
            .clip(CircleShape)
            .background(containerColor)
            .border(1.5.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isSaved,
            transitionSpec = {
                (scaleIn(
                    initialScale = 0.6f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(tween(150))) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut(tween(120)))
            },
            label = "badgeIcon"
        ) { saved ->
            if (saved) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = savedContentDescription,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = unsavedContentDescription,
                    tint = textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun LikedSongRow(
    isLiked: Boolean,
    onToggle: () -> Unit,
    textPrimary: Color,
    textSecondary: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .pressable(
                pressedScale = MotionTokens.RowPressScale,
                onClick = onToggle
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small)
                .background(PinkGradientHero),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Liked Songs",
                style = MaterialTheme.typography.titleMedium,
                color = textPrimary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Auto playlist",
                style = MaterialTheme.typography.bodySmall,
                color = textSecondary
            )
        }

        SavedToggleBadge(
            isSaved = isLiked,
            savedContentDescription = "Saved in Liked Songs",
            unsavedContentDescription = "Add to Liked Songs",
            textSecondary = textSecondary
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: PlaylistEntity,
    isSaved: Boolean,
    onToggle: () -> Unit,
    textPrimary: Color,
    textSecondary: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .pressable(
                pressedScale = MotionTokens.RowPressScale,
                onClick = onToggle
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!playlist.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(playlist.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = playlist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(PinkGradientHero),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${playlist.songCount} songs",
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = textSecondary
            )
        }

        SavedToggleBadge(
            isSaved = isSaved,
            savedContentDescription = "Saved in playlist",
            unsavedContentDescription = "Add to playlist",
            textSecondary = textSecondary
        )
    }
}

val LocalAddToPlaylist = androidx.compose.runtime.compositionLocalOf<(Song) -> Unit> { {} }
