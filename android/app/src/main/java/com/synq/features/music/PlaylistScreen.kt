package com.synq.features.music

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.synq.RootViewModel
import com.synq.features.music.components.SongItem
import com.synq.features.music.domain.model.Song
import com.synq.features.music.jam.LocalIsInJam
import com.synq.ui.SynqLoadingIndicator
import com.synq.ui.motion.MotionTokens
import com.synq.ui.motion.pressable
import com.synq.ui.theme.PinkGradient
import com.synq.ui.theme.PinkPrimary

private val PinkAccent = PinkPrimary

private val PlaylistAccentGradient = PinkGradient

private fun getCreatorAvatarGradient(name: String): Brush {
    val nonPinkGradients = listOf(
        listOf(Color(0xFF1E88E5), Color(0xFF1565C0)), // Royal Blue
        listOf(Color(0xFF00ACC1), Color(0xFF00897B)), // Cyan / Teal
        listOf(Color(0xFF7C4DFF), Color(0xFF651FFF)), // Deep Purple
        listOf(Color(0xFF43A047), Color(0xFF2E7D32)), // Forest Green
        listOf(Color(0xFFF59E0B), Color(0xFFD97706)), // Amber Gold
        listOf(Color(0xFFE65100), Color(0xFFBF360C)), // Warm Saffron
        listOf(Color(0xFF3949AB), Color(0xFF283593)), // Cobalt / Navy
        listOf(Color(0xFF00897B), Color(0xFF00695C)), // Mint / Teal
    )
    if (name.equals("Curated", ignoreCase = true)) {
        return Brush.linearGradient(nonPinkGradients[0])
    }
    val hash = kotlin.math.abs(name.lowercase().hashCode())
    return Brush.linearGradient(nonPinkGradients[hash % nonPinkGradients.size])
}

enum class PlaylistSortOrder(val label: String) {
    NORMAL("Normal order"),
    DATE_ADDED_DESC("Date added (newest)"),
    DATE_ADDED_ASC("Date added (oldest)"),
    TITLE_ASC("Title (A-Z)"),
    TITLE_DESC("Title (Z-A)")
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(
    playlistId: String,
    viewModel: PlaylistViewModel = hiltViewModel(),
    musicViewModel: MusicViewModel = hiltViewModel(),
    rootViewModel: RootViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToSearch: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val currentUser by rootViewModel.user.collectAsState()
    val isInJam = LocalIsInJam.current
    val listState = rememberLazyListState()

    var showEditDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedSort by remember { mutableStateOf(PlaylistSortOrder.NORMAL) }

    // Search inside playlist
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
        }
    }

    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }

    // Edit mode (drag-to-reorder and remove)
    var isEditMode by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var stagedSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    // Notify RootViewModel to hide miniplayer during edit mode
    LaunchedEffect(isEditMode) {
        rootViewModel.setPlaylistEditing(isEditMode)
    }
    DisposableEffect(Unit) {
        onDispose {
            rootViewModel.setPlaylistEditing(false)
        }
    }

    BackHandler(enabled = isEditMode) {
        isEditMode = false
        stagedSongs = uiState.playlist?.songs ?: emptyList()
    }

    // Drag tracking state
    var draggingSongId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 68.dp.toPx() }

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    // Keep stagedSongs synced when not in edit mode
    val originalPlaylist = uiState.playlist
    LaunchedEffect(originalPlaylist?.songs, isEditMode) {
        if (!isEditMode && originalPlaylist != null) {
            stagedSongs = originalPlaylist.songs
        }
    }

    val initialSongIds = remember(originalPlaylist?.songs) {
        originalPlaylist?.songs?.map { it.id } ?: emptyList()
    }

    val hasChanges = remember(stagedSongs, initialSongIds) {
        stagedSongs.map { it.id } != initialSongIds
    }

    val coroutineScope = rememberCoroutineScope()

    // Detected if scrolled past top header to show compact top-bar title and play button
    val isScrolledPastHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 3 || (listState.firstVisibleItemIndex == 3 && listState.firstVisibleItemScrollOffset > 60)
        }
    }

    // Detected if scrolled down past a limit (past header & top tracks) to show "Move to top" button
    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex >= 7
        }
    }

    val playbackState by musicViewModel.playerManager.playbackState.collectAsState()
    val isMiniPlayerPresent = playbackState.currentSong != null && !isEditMode

    val scrollToTopBottomPadding by animateDpAsState(
        targetValue = if (isMiniPlayerPresent) 88.dp else 16.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "scrollToTopBottomPadding"
    )

    val lazyBottomPadding by animateDpAsState(
        targetValue = if (isMiniPlayerPresent) 180.dp else 110.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lazyBottomPadding"
    )

    if (showEditDialog && originalPlaylist != null && !originalPlaylist.id.equals("liked", ignoreCase = true)) {
        EditPlaylistDialog(
            initialName = originalPlaylist.title,
            initialDescription = originalPlaylist.description,
            initialImageUrl = originalPlaylist.imageUrl,
            onDismiss = { showEditDialog = false },
            onSave = { name, desc, imgUrl ->
                viewModel.updatePlaylistDetails(name, desc, imgUrl) { success ->
                    Toast.makeText(
                        context,
                        if (success) "Playlist updated" else "Failed to update playlist",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (uiState.isLoading) {
            SynqLoadingIndicator(
                modifier = Modifier.align(Alignment.Center),
                size = 48.dp,
                barCount = 5
            )
        } else if (uiState.error != null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Failed to load playlist: ${uiState.error}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.loadPlaylist(playlistId) },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                ) {
                    Text("Retry")
                }
            }
        } else if (originalPlaylist != null) {
            val isUserMade = originalPlaylist.providerId == "custom"

            // Compute displayed songs:
            // In Edit Mode: always shows stagedSongs in their staged order.
            // In Normal Mode: visual sort only.
            val currentBaseSongs = if (isEditMode) stagedSongs else originalPlaylist.songs

            val displayedSongs = remember(currentBaseSongs, selectedSort, searchQuery, isEditMode) {
                val sorted = if (isEditMode) {
                    currentBaseSongs
                } else {
                    when (selectedSort) {
                        PlaylistSortOrder.NORMAL -> currentBaseSongs
                        PlaylistSortOrder.DATE_ADDED_DESC -> currentBaseSongs.sortedByDescending { it.addedAt ?: 0L }
                        PlaylistSortOrder.DATE_ADDED_ASC -> currentBaseSongs.sortedBy { it.addedAt ?: 0L }
                        PlaylistSortOrder.TITLE_ASC -> currentBaseSongs.sortedBy { it.title.lowercase() }
                        PlaylistSortOrder.TITLE_DESC -> currentBaseSongs.sortedByDescending { it.title.lowercase() }
                    }
                }
                if (searchQuery.isBlank()) {
                    sorted
                } else {
                    sorted.filter {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                        it.primaryArtistName.contains(searchQuery, ignoreCase = true)
                    }
                }
            }

            // Total duration in 5h33m or 34m33s format
            val totalSeconds = remember(originalPlaylist.songs) {
                originalPlaylist.songs.sumOf { it.durationSeconds }
            }
            val formattedHms = remember(totalSeconds) {
                val hh = totalSeconds / 3600
                val mm = (totalSeconds % 3600) / 60
                val ss = totalSeconds % 60
                if (hh > 0) "${hh}h${mm}m" else "${mm}m${ss}s"
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Top App Bar
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(20f)
                ) {
                    if (isSearchActive) {
                        // Full search mode (Spotify Screenshot 4)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 15.sp
                                        ),
                                        cursorBrush = SolidColor(PinkPrimary),
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(searchFocusRequester),
                                        decorationBox = { innerTextField ->
                                            Box(contentAlignment = Alignment.CenterStart) {
                                                if (searchQuery.isEmpty()) {
                                                    Text(
                                                        text = "Find in playlist",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 15.sp
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        }
                                    )
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Standard top bar with back arrow and collapsed header (Spotify Screenshot 3)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Title fades in when scrolled past top artwork
                            val scrolledTitleAlpha by animateFloatAsState(
                                targetValue = if (isScrolledPastHeader) 1f else 0f,
                                animationSpec = tween(durationMillis = 200),
                                label = "scrolledTitleAlpha"
                            )
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = originalPlaylist.title,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.graphicsLayer { alpha = scrolledTitleAlpha }
                                )
                            }

                            // Small play button in top bar when scrolled (Spotify Screenshot 3)
                            AnimatedVisibility(
                                visible = isScrolledPastHeader,
                                enter = scaleIn() + fadeIn(),
                                exit = scaleOut() + fadeOut()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(48.dp)
                                        .pressable(
                                            onClick = {
                                                if (originalPlaylist.songs.isNotEmpty()) {
                                                    musicViewModel.playQueue(originalPlaylist.songs, 0)
                                                }
                                            },
                                            pressedScale = MotionTokens.ButtonPressScale
                                        )
                                        .clip(CircleShape)
                                        .background(PinkGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Main Content
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = lazyBottomPadding)
                ) {
                    // Item 0: "Find in playlist" search bar (Spotify Screenshot 2)
                    if (!isSearchActive) {
                        item(key = "search_bar_trigger") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 6.dp)
                                    .height(40.dp)
                                    .pressable(
                                        onClick = { isSearchActive = true },
                                        pressedScale = MotionTokens.RowPressScale
                                    )
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Find in playlist",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Item 1: Centered Square Artwork (Spotify Screenshot 1 & 2)
                    if (!isSearchActive) {
                        item(key = "playlist_artwork") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                PlaylistCoverArtwork(
                                    imageUrl = originalPlaylist.imageUrl,
                                    songs = originalPlaylist.songs,
                                    isLikedPlaylist = originalPlaylist.id.equals("liked", ignoreCase = true)
                                )
                            }
                        }
                    }

                    // Item 2: Title, Creator, Duration/Song count (Spotify Screenshot 1)
                    if (!isSearchActive) {
                        item(key = "playlist_meta") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                            ) {
                                Text(
                                    text = originalPlaylist.title,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (originalPlaylist.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = originalPlaylist.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Creator Row (Avatar + Name)
                                val isUserPlaylist = originalPlaylist.id.equals("liked", ignoreCase = true) || isUserMade
                                val creatorName = if (isUserPlaylist) {
                                    currentUser?.username?.takeIf { it.isNotBlank() } ?: "You"
                                } else {
                                    "Curated"
                                }
                                val creatorInitial = creatorName.firstOrNull()?.uppercase() ?: if (isUserPlaylist) "Y" else "C"
                                val creatorGradient = remember(creatorName) { getCreatorAvatarGradient(creatorName) }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(creatorGradient),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = creatorInitial,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = creatorName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Lock icon + Song count & HH:MM:SS duration
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${originalPlaylist.songs.size} songs • $formattedHms",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }

                    // Item 3: Actions Row: Share, Shuffle, Big Play Button (Spotify Screenshot 1)
                    if (!isSearchActive) {
                        item(key = "playlist_action_row") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    val shareUrl = "https://21yash.online/playlist/${originalPlaylist.id}"
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, shareUrl)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Playlist"))
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(onClick = {
                                    if (originalPlaylist.songs.isNotEmpty()) {
                                        musicViewModel.setShuffle(true)
                                        musicViewModel.playQueue(originalPlaylist.songs.shuffled(), 0)
                                        Toast.makeText(context, "Shuffle on", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                // Big Pink Play Button
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .pressable(
                                            onClick = {
                                                if (originalPlaylist.songs.isNotEmpty()) {
                                                    musicViewModel.playQueue(originalPlaylist.songs, 0)
                                                }
                                            },
                                            pressedScale = MotionTokens.ButtonPressScale
                                        )
                                        .clip(CircleShape)
                                        .background(PinkGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Item 4: Sticky Horizontal Pill Row (Spotify Screenshot 1 & 3)
                    if (!isSearchActive) {
                        stickyHeader(key = "sticky_pill_row") {
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(15f)
                            ) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. + Add pill (only for user made)
                                    if (isUserMade) {
                                        item {
                                            PillButton(
                                                icon = Icons.Default.Add,
                                                label = "Add",
                                                onClick = onNavigateToSearch
                                            )
                                        }
                                    }

                                    // 2. Edit / Cancel pill (only for user made)
                                    if (isUserMade) {
                                        item {
                                            PillButton(
                                                icon = if (isEditMode) Icons.Default.Close else Icons.Default.Menu,
                                                label = if (isEditMode) "Cancel" else "Edit",
                                                isSelected = isEditMode,
                                                onClick = {
                                                    if (isEditMode) {
                                                        // Cancel edits and revert
                                                        isEditMode = false
                                                        stagedSongs = originalPlaylist.songs
                                                    } else {
                                                        isEditMode = true
                                                        stagedSongs = originalPlaylist.songs
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    // 3. Sort pill with dropdown
                                    item {
                                        Box {
                                            PillButton(
                                                icon = Icons.Default.SwapVert,
                                                label = when (selectedSort) {
                                                    PlaylistSortOrder.NORMAL -> "Sort"
                                                    else -> selectedSort.label
                                                },
                                                isSelected = selectedSort != PlaylistSortOrder.NORMAL,
                                                onClick = { showSortMenu = true }
                                            )

                                            DropdownMenu(
                                                expanded = showSortMenu,
                                                onDismissRequest = { showSortMenu = false },
                                                shape = MaterialTheme.shapes.medium
                                            ) {
                                                PlaylistSortOrder.entries.forEach { order ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                text = order.label,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = if (selectedSort == order) FontWeight.SemiBold else FontWeight.Normal
                                                            )
                                                        },
                                                        trailingIcon = {
                                                            if (selectedSort == order) {
                                                                Icon(
                                                                    Icons.Default.Check,
                                                                    contentDescription = null,
                                                                    tint = PinkPrimary,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                        },
                                                        onClick = {
                                                            selectedSort = order
                                                            showSortMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 4. Name and details pill (only for user made)
                                    if (isUserMade) {
                                        item {
                                            PillButton(
                                                icon = Icons.Default.Edit,
                                                label = "Name and details",
                                                onClick = { showEditDialog = true }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Empty State
                    if (displayedSongs.isEmpty()) {
                        item(key = "empty_state") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp, horizontal = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = PinkPrimary,
                                    modifier = Modifier.size(42.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No songs match \"$searchQuery\"" else "No songs in this playlist yet",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (searchQuery.isEmpty() && isUserMade) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = onNavigateToSearch,
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Add songs", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    } else {
                        // Song list items
                        items(
                            count = displayedSongs.size,
                            key = { index -> displayedSongs[index].id }
                        ) { index ->
                            val song = displayedSongs[index]

                            if (isEditMode) {
                                val isDragging = song.id == draggingSongId

                                EditModeSongRow(
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        fadeOutSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        placementSpec = if (isDragging) null else spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    ),
                                    song = song,
                                    isDragging = isDragging,
                                    dragOffsetY = if (isDragging) dragOffsetY else 0f,
                                    onRemove = {
                                        stagedSongs = stagedSongs.filterNot { it.id == song.id }
                                    },
                                    onDragStart = {
                                        draggingSongId = song.id
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { deltaY ->
                                        dragOffsetY += deltaY
                                        val currentIdx = stagedSongs.indexOfFirst { it.id == draggingSongId }
                                        if (currentIdx != -1) {
                                            if (dragOffsetY > rowHeightPx * 0.6f && currentIdx < stagedSongs.lastIndex) {
                                                val mutable = stagedSongs.toMutableList()
                                                val item = mutable.removeAt(currentIdx)
                                                mutable.add(currentIdx + 1, item)
                                                stagedSongs = mutable
                                                dragOffsetY -= rowHeightPx
                                            } else if (dragOffsetY < -rowHeightPx * 0.6f && currentIdx > 0) {
                                                val mutable = stagedSongs.toMutableList()
                                                val item = mutable.removeAt(currentIdx)
                                                mutable.add(currentIdx - 1, item)
                                                stagedSongs = mutable
                                                dragOffsetY += rowHeightPx
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggingSongId = null
                                        dragOffsetY = 0f
                                    }
                                )
                            } else {
                                SongItem(
                                    song = song,
                                    onClick = {
                                        if (isInJam) {
                                            musicViewModel.playSong(song)
                                        } else {
                                            musicViewModel.playQueue(displayedSongs, index)
                                        }
                                    },
                                    onPlayNext = { musicViewModel.playNext(song) },
                                    onAddToQueue = { musicViewModel.addToQueue(song) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // Floating Save Button (Visible only when in Edit Mode)
            AnimatedVisibility(
                visible = isEditMode,
                enter = slideInVertically(
                    initialOffsetY = { it * 2 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn() + scaleIn(initialScale = 0.85f),
                exit = slideOutVertically(
                    targetOffsetY = { it * 2 },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
            ) {
                val saveButtonScale by animateFloatAsState(
                    targetValue = if (hasChanges && !isSaving) 1f else 0.94f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "saveButtonScale"
                )
                val buttonContainerColor by animateColorAsState(
                    targetValue = if (hasChanges && !isSaving) PinkPrimary else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                    animationSpec = tween(durationMillis = 200),
                    label = "saveButtonColor"
                )
                val saveInteractionSource = remember { MutableInteractionSource() }
                val isSavePressed by saveInteractionSource.collectIsPressedAsState()
                val savePressScale by animateFloatAsState(
                    targetValue = if (isSavePressed) 0.90f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = if (isSavePressed) Spring.DampingRatioNoBouncy else 0.45f,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "savePressScale"
                )

                Button(
                    onClick = {
                        if (hasChanges && !isSaving) {
                            isSaving = true
                            val removedIds = initialSongIds.filterNot { id ->
                                stagedSongs.any { it.id == id }
                            }
                            val remainingIds = stagedSongs.map { it.id }

                            viewModel.savePlaylistChanges(
                                playlistId = originalPlaylist.id,
                                removedSongIds = removedIds,
                                remainingSongIds = remainingIds
                            ) { success ->
                                isSaving = false
                                if (success) {
                                    isEditMode = false
                                    Toast.makeText(context, "Playlist saved", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to save changes", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = hasChanges && !isSaving,
                    interactionSource = saveInteractionSource,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonContainerColor,
                        contentColor = Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 8.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    modifier = Modifier
                        .graphicsLayer {
                            val totalScale = saveButtonScale * savePressScale
                            scaleX = totalScale
                            scaleY = totalScale
                        }
                ) {
                    AnimatedContent(
                        targetState = isSaving,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.85f)) togetherWith
                            (fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.85f))
                        },
                        label = "saveButtonContent"
                    ) { saving ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (saving) {
                                SynqLoadingIndicator(
                                    size = 18.dp,
                                    barCount = 3,
                                    colors = listOf(Color.White.copy(alpha = 0.75f), Color.White)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Saving...", style = MaterialTheme.typography.labelLarge, color = Color.White)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save changes", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            // Floating "Move to top" Button (Visible when scrolled down and not in edit mode)
            AnimatedVisibility(
                visible = showScrollToTop && !isEditMode,
                enter = slideInVertically(
                    initialOffsetY = { it * 2 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn() + scaleIn(initialScale = 0.8f),
                exit = slideOutVertically(
                    targetOffsetY = { it * 2 },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = scrollToTopBottomPadding)
            ) {
                val scrollToTopInteraction = remember { MutableInteractionSource() }
                val isScrollPressed by scrollToTopInteraction.collectIsPressedAsState()
                val scrollBounceScale by animateFloatAsState(
                    targetValue = if (isScrollPressed) 0.90f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = if (isScrollPressed) Spring.DampingRatioNoBouncy else 0.45f,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "scrollBounceScale"
                )

                Surface(
                    onClick = {
                        coroutineScope.launch {
                            if (!listState.isScrollInProgress) {
                                if (listState.firstVisibleItemIndex > 6) {
                                    listState.scrollToItem(6)
                                }
                                listState.animateScrollToItem(0)
                            }
                        }
                    },
                    interactionSource = scrollToTopInteraction,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shadowElevation = 6.dp,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.graphicsLayer {
                        scaleX = scrollBounceScale
                        scaleY = scrollBounceScale
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = PinkPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Move to top",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Components
// ─────────────────────────────────────────────────────────────

@Composable
private fun PlaylistCoverArtwork(
    imageUrl: String?,
    songs: List<Song>,
    isLikedPlaylist: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(200.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (isLikedPlaylist) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PinkGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Liked Songs",
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }
        } else if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Playlist Cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val songImages = songs.mapNotNull { it.imageUrl }.filter { it.isNotBlank() }.take(4)
            if (songImages.size >= 4) {
                // 2x2 collage like Spotify (Screenshot 1 & 2)
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        AsyncImage(
                            model = songImages[0],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        AsyncImage(
                            model = songImages[1],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        AsyncImage(
                            model = songImages[2],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        AsyncImage(
                            model = songImages[3],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            } else if (songImages.isNotEmpty()) {
                AsyncImage(
                    model = songImages.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PlaylistAccentGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    enabled: Boolean = true,
    isAccent: Boolean = false
) {
    val containerColor = when {
        isAccent && enabled -> PinkPrimary
        isAccent && !enabled -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        isSelected -> PinkPrimary
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val contentColor = when {
        isAccent && enabled -> Color.White
        isAccent && !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        isSelected -> Color.White
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .height(32.dp)
            .pressable(
                onClick = if (enabled) onClick else null,
                pressedScale = MotionTokens.ChipPressScale
            )
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun EditModeSongRow(
    song: Song,
    isDragging: Boolean,
    dragOffsetY: Float,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleColor = MaterialTheme.colorScheme.onSurface
    val artistColor = MaterialTheme.colorScheme.onSurfaceVariant

    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "editSongScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "editSongElevation"
    )
    val handleScale by animateFloatAsState(
        targetValue = if (isDragging) 1.2f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "editSongHandleScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 10f else 1f)
            .graphicsLayer {
                translationY = dragOffsetY
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation.toPx()
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Red minus icon for removing song
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .size(36.dp)
                .pressable(pressedScale = 0.8f)
        ) {
            Icon(
                imageVector = Icons.Default.RemoveCircle,
                contentDescription = "Remove song",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        AsyncImage(
            model = song.imageUrl,
            contentDescription = "Album art for ${song.title}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.primaryArtistName,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = artistColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Drag handle indicator (three dots replaced with reorder handle in edit mode)
        Box(
            modifier = Modifier
                .size(42.dp)
                .pointerInput(song.id) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.y)
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Reorder song",
                tint = if (isDragging) PinkPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = handleScale
                        scaleY = handleScale
                    }
            )
        }
    }
}

@Composable
private fun EditPlaylistDialog(
    initialName: String,
    initialDescription: String,
    initialImageUrl: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, imageUrl: String?) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var imageUrl by remember { mutableStateOf(initialImageUrl ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Playlist",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )

                OutlinedTextField(
                    value = imageUrl,
                    onValueChange = { imageUrl = it },
                    label = { Text("Cover Image URL (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )

                if (imageUrl.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cover preview:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "Cover preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name.trim(), description.trim(), imageUrl.trim().ifBlank { null })
                    onDismiss()
                },
                enabled = name.isNotBlank(),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                modifier = Modifier.pressable(pressedScale = MotionTokens.ButtonPressScale)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
