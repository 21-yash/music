package com.synq

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.synq.navigation.AppBottomNavBar
import com.synq.navigation.AppNavHost
import com.synq.navigation.Screen
import com.synq.features.music.components.MiniPlayer
import com.synq.features.music.jam.JamClientState
import com.synq.features.music.jam.JamUiEvent
import com.synq.features.music.jam.LocalCanAddSongsToJam
import com.synq.ui.theme.PinkPrimary
import com.synq.features.music.jam.LocalIsInJam

/**
 * Root composable for the app — the main shell.
 *
 * Hosts the floating profile avatar (with its dropdown menu), bottom navigation
 * bar, mini player, and the navigation host. The bottom bar is hidden on the
 * Profile screen (since it's not a bottom-nav tab). The avatar floats at the
 * top-end of every screen except MatchDetails and Profile.
 */
@Composable
fun SynqAppRoot(
    rootViewModel: RootViewModel = hiltViewModel()
) {
    val isLoggedIn by rootViewModel.isLoggedIn.collectAsState()

    when (isLoggedIn) {
        null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }
        false -> {
            com.synq.features.auth.AuthScreen(
                showBackButton = false,
                onAuthSuccess = {
                    rootViewModel.loadUserProfile()
                }
            )
        }
        true -> {
            MainAppContent(rootViewModel = rootViewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppContent(
    rootViewModel: RootViewModel
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val playbackState by rootViewModel.playbackState.collectAsState()
    val jamState by rootViewModel.jamState.collectAsState()
    val jamUiState by rootViewModel.jamUiState.collectAsState()
    val jamClientState by rootViewModel.jamClientState.collectAsState()
    val jamLocalPlaybackState by rootViewModel.jamLocalPlaybackState.collectAsState()
    val recommendedSongIds by rootViewModel.recommendedSongIds.collectAsState()
    val playlists by rootViewModel.playlists.collectAsState()
    val songForPlaylistAdd by rootViewModel.songForPlaylistAdd.collectAsState()
    val likedSongIds by rootViewModel.likedSongIds.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    var showFullPlayer by remember { mutableStateOf(false) }

    LaunchedEffect(playbackState.currentSong) {
        if (playbackState.currentSong == null) {
            showFullPlayer = false
        }
    }

    // D6: Rejoin candidate dialog on app start
    var rejoinCandidateCode by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val candidate = rootViewModel.jamPreferences.rejoinCandidate()
        if (candidate != null && !jamState.isInJam) {
            rejoinCandidateCode = candidate
        }
    }

    LaunchedEffect(jamState.isInJam, jamClientState) {
        if (jamState.isInJam || jamClientState is JamClientState.Active || jamClientState is JamClientState.Connecting || jamClientState is JamClientState.Reconnecting) {
            rejoinCandidateCode = null
        }
    }

    val isReconnectingOrInJam = jamState.isInJam ||
            jamClientState is JamClientState.Active ||
            jamClientState is JamClientState.Connecting ||
            jamClientState is JamClientState.Reconnecting

    if (rejoinCandidateCode != null && !isReconnectingOrInJam) {
        val codeToRejoin = rejoinCandidateCode!!
        AlertDialog(
            onDismissRequest = {
                rootViewModel.jamPreferences.clearLastJam()
                rejoinCandidateCode = null
            },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Rejoin Jam $codeToRejoin?",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "You were previously connected to this Jam session. Would you like to rejoin?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        rejoinCandidateCode = null
                        rootViewModel.joinJam(codeToRejoin, avatarUrl = rootViewModel.jamPreferences.getLastAvatarUrl())
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary)
                ) {
                    Text(
                        text = "Rejoin",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        rootViewModel.jamPreferences.clearLastJam()
                        rejoinCandidateCode = null
                    }
                ) {
                    Text(
                        text = "Dismiss",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    // Jam UI Event toasts (single source of truth: JamStateStore -> JamUiEvent -> this collector)
    LaunchedEffect(Unit) {
        rootViewModel.jamUiEvents.collect { event ->
            when (event) {
                is JamUiEvent.MemberJoined -> {
                    com.synq.ui.toast.SynqToastManager.show("${event.username} joined the Jam", com.synq.ui.toast.SynqToastType.SUCCESS)
                }
                is JamUiEvent.MemberLeft -> {
                    com.synq.ui.toast.SynqToastManager.show("${event.username} left the Jam", com.synq.ui.toast.SynqToastType.INFO)
                }
                is JamUiEvent.HostChanged -> {
                    com.synq.ui.toast.SynqToastManager.show("${event.newHostUsername} is now the host", com.synq.ui.toast.SynqToastType.INFO)
                }
                is JamUiEvent.Kicked -> {
                    com.synq.ui.toast.SynqToastManager.show(event.reason, com.synq.ui.toast.SynqToastType.ERROR, longDuration = true)
                }
                is JamUiEvent.Toast -> {
                    com.synq.ui.toast.SynqToastManager.show(event.message)
                }
                is JamUiEvent.PermChanged -> {
                    // Handled via JamUiEvent.Toast derived in JamStateStore
                }
            }
        }
    }


    LaunchedEffect(jamState.error) {
        jamState.error?.let { err ->
            com.synq.ui.toast.SynqToastManager.show(err, com.synq.ui.toast.SynqToastType.ERROR, longDuration = true)
            rootViewModel.clearJamError()
        }
    }

    LaunchedEffect(playbackState.error) {
        playbackState.error?.let { err ->
            com.synq.ui.toast.SynqToastManager.show(err, com.synq.ui.toast.SynqToastType.ERROR)
        }
    }

    val isSportsEnabled by rootViewModel.isSportsEnabled.collectAsState()
    val showBottomBar = currentRoute in Screen.bottomNavItems.map { it.route }

    LaunchedEffect(isSportsEnabled, currentRoute) {
        if (!isSportsEnabled && (currentRoute == Screen.Sports.route || currentRoute?.startsWith("sports/") == true)) {
            navController.navigate(Screen.Home.route) {
                popUpTo(navController.graph.startDestinationId) { inclusive = false }
                launchSingleTop = true
            }
        }
    }
    val isPlaylistEditing by rootViewModel.isPlaylistEditing.collectAsState()
    val showMiniPlayer = currentRoute != Screen.MatchDetails().route &&
        currentRoute != Screen.HighlightPlayer().route &&
        !isPlaylistEditing

    val user by rootViewModel.user.collectAsState()
    val showSignOutConfirmation by rootViewModel.showSignOutDialog.collectAsState()

    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { rootViewModel.dismissSignOut() },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Sign Out",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to sign out of your Synq account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        rootViewModel.dismissSignOut()
                        rootViewModel.logout()
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        text = "Sign Out",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { rootViewModel.dismissSignOut() }) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    val showLeaveJamConfirm by rootViewModel.showLeaveJamConfirmDialog.collectAsState()
    if (showLeaveJamConfirm) {
        AlertDialog(
            onDismissRequest = { rootViewModel.dismissLeaveJamConfirmDialog() },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Leave Jam?",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Leave Jam and stop playback?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { rootViewModel.confirmLeaveJamAndStopPlayback() },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        text = "Leave & Stop",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { rootViewModel.dismissLeaveJamConfirmDialog() }) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    songForPlaylistAdd?.let { song ->
        val savedPlaylistIds by rootViewModel.getPlaylistIdsForSong(song.id).collectAsState(initial = emptyList())
        val isLiked = likedSongIds.contains(song.id)
        com.synq.features.music.components.AddToPlaylistSheet(
            song = song,
            playlists = playlists,
            savedPlaylistIds = savedPlaylistIds.toSet(),
            isLiked = isLiked,
            onToggleLike = {
                rootViewModel.toggleLike(song)
            },
            onSelectPlaylist = { playlist ->
                rootViewModel.addSongToExistingPlaylist(playlist.serverId, song) { success, msg ->
                    com.synq.ui.toast.SynqToastManager.show(
                        msg,
                        if (success) com.synq.ui.toast.SynqToastType.SUCCESS else com.synq.ui.toast.SynqToastType.ERROR
                    )
                }
            },
            onRemoveFromPlaylist = { playlist ->
                rootViewModel.removeSongFromPlaylist(playlist.serverId, song.id) { success, msg ->
                    com.synq.ui.toast.SynqToastManager.show(
                        msg,
                        if (success) com.synq.ui.toast.SynqToastType.INFO else com.synq.ui.toast.SynqToastType.ERROR
                    )
                }
            },
            onCreatePlaylist = { name ->
                rootViewModel.createPlaylistAndAddSong(name, song) { success, msg ->
                    com.synq.ui.toast.SynqToastManager.show(
                        msg,
                        if (success) com.synq.ui.toast.SynqToastType.SUCCESS else com.synq.ui.toast.SynqToastType.ERROR
                    )
                }
            },
            onDismiss = { rootViewModel.dismissAddToPlaylist() }
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalIsInJam provides jamState.isInJam,
        LocalCanAddSongsToJam provides (!jamState.isInJam || jamUiState.canAddSongs),
        com.synq.features.music.components.LocalAddToPlaylist provides { song ->
            rootViewModel.showAddToPlaylist(song)
        },
        com.synq.features.music.components.LocalOnNavigateToSongDetails provides { songId, provider ->
            showFullPlayer = false
            navController.navigate(com.synq.navigation.Screen.SongDetails().createRoute(songId, provider))
        },
        com.synq.features.music.components.LocalOnNavigateToAlbum provides { albumId ->
            showFullPlayer = false
            navController.navigate(com.synq.navigation.Screen.Album().createRoute(albumId))
        },
        com.synq.features.music.components.LocalLikedSongIds provides likedSongIds,
        com.synq.features.music.components.LocalToggleLike provides { song ->
            val wasLiked = likedSongIds.contains(song.id)
            rootViewModel.toggleLike(song)
            com.synq.ui.toast.SynqToastManager.show(
                if (wasLiked) "Removed from Liked Songs" else "Added to Liked Songs",
                if (wasLiked) com.synq.ui.toast.SynqToastType.INFO else com.synq.ui.toast.SynqToastType.SUCCESS
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            AppNavHost(
                navController = navController,
                modifier = Modifier.fillMaxSize(),
                rootViewModel = rootViewModel,
            )
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                Column(modifier = Modifier.animateContentSize()) {
                    AnimatedVisibility(
                        visible = showMiniPlayer,
                        enter = slideInVertically(initialOffsetY = { it }) + expandVertically(),
                        exit = slideOutVertically(targetOffsetY = { it }) + shrinkVertically(),
                    ) {
                        val canControlPlayback = !jamState.isInJam || jamUiState.canControlPlayback
                        val jamHostName = jamUiState.hostName.ifBlank { "host" }

                        MiniPlayer(
                            playbackState = playbackState,
                            canControlPlayback = canControlPlayback,
                            onPlayPauseClick = {
                                if (jamState.isInJam) {
                                    rootViewModel.toggleJamPlayPause()
                                } else {
                                    rootViewModel.togglePlayPause()
                                }
                            },
                            onPreviousClick = {
                                if (jamState.isInJam) {
                                    if (jamUiState.canControlPlayback) {
                                        rootViewModel.jamPreviousTrack()
                                    } else {
                                        com.synq.ui.toast.SynqToastManager.show("Ask $jamHostName for playback control", com.synq.ui.toast.SynqToastType.WARNING)
                                    }
                                } else {
                                    rootViewModel.skipToPrevious()
                                }
                            },
                            onNextClick = {
                                if (jamState.isInJam) {
                                    if (jamUiState.canControlPlayback) {
                                        rootViewModel.jamNextTrack()
                                    } else {
                                        com.synq.ui.toast.SynqToastManager.show("Ask $jamHostName for playback control", com.synq.ui.toast.SynqToastType.WARNING)
                                    }
                                } else {
                                    rootViewModel.skipToNext()
                                }
                            },
                            onNavigateToNowPlaying = { showFullPlayer = true },
                            onDismiss = { rootViewModel.stopPlayback() }
                        )
                    }
                    AnimatedVisibility(
                        visible = showBottomBar,
                        enter = slideInVertically(initialOffsetY = { it }) + expandVertically(),
                        exit = slideOutVertically(targetOffsetY = { it }) + shrinkVertically(),
                    ) {
                        AppBottomNavBar(
                            navController = navController,
                            isSportsEnabled = isSportsEnabled
                        )
                    }
                }
            }
            
            AnimatedVisibility(
                visible = showFullPlayer && playbackState.currentSong != null,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(320, easing = FastOutSlowInEasing)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(280, easing = FastOutSlowInEasing))
            ) {
                val currentSong = playbackState.currentSong ?: return@AnimatedVisibility
                val isLiked by rootViewModel.isSongLiked(currentSong.id).collectAsState(initial = false)
                val queue by rootViewModel.queue.collectAsState()
                val currentIndex by rootViewModel.currentIndex.collectAsState()
                val isRepeatOne by rootViewModel.isRepeatOne.collectAsState()
                val isShuffle by rootViewModel.isShuffle.collectAsState()
                val currentLyrics by rootViewModel.currentLyrics.collectAsState()
                val isLyricsLoading by rootViewModel.isLyricsLoading.collectAsState()
                val sleepSelected by rootViewModel.sleepSelected.collectAsState()
                val sleepRemainingSeconds by rootViewModel.sleepRemainingSeconds.collectAsState()

                com.synq.features.music.FullPlayerSheet(
                    playbackState = playbackState,
                    queue = queue,
                    currentIndex = currentIndex,
                    isRepeatOne = isRepeatOne,
                    isShuffle = isShuffle,
                    currentLyrics = currentLyrics,
                    isLyricsLoading = isLyricsLoading,
                    sleepSelected = sleepSelected,
                    sleepRemainingSeconds = sleepRemainingSeconds,
                    jamState = jamState,
                    jamUiState = jamUiState,
                    localPlaybackState = jamLocalPlaybackState,
                    isLiked = isLiked,
                    onLikeClick = { rootViewModel.toggleLike(currentSong) },
                    onPlayPauseClick = {
                        if (jamState.isInJam) {
                            rootViewModel.toggleJamPlayPause()
                        } else {
                            rootViewModel.togglePlayPause()
                        }
                    },
                    onNextClick = {
                        if (jamState.isInJam) {
                            rootViewModel.jamNextTrack()
                        } else {
                            rootViewModel.skipToNext()
                        }
                    },
                    onPreviousClick = {
                        if (jamState.isInJam) {
                            rootViewModel.jamPreviousTrack()
                        } else {
                            rootViewModel.skipToPrevious()
                        }
                    },
                    onSeek = { ratio -> rootViewModel.seekToRatio(ratio) },
                    onSeekToMs = { ms ->
                        if (jamState.isInJam) {
                            rootViewModel.jamSeekTo(ms)
                        } else {
                            rootViewModel.seekTo(ms)
                        }
                    },
                    onPlayQueueIndex = { idx -> rootViewModel.playQueueIndex(idx) },
                    onRemoveQueueItem = { idx -> rootViewModel.removeQueueItem(idx) },
                    onReorderQueue = { from, to -> rootViewModel.reorderQueue(from, to) },
                    onToggleRepeat = { rootViewModel.toggleRepeat() },
                    onToggleShuffle = { rootViewModel.toggleShuffle() },
                    onSelectSleepOption = { opt -> rootViewModel.setSleepTimer(opt) },
                    onStartJam = { rootViewModel.startJam() },
                    onJoinJam = { code -> rootViewModel.joinJam(code) },
                    onLeaveJam = { rootViewModel.leaveJam() },
                    onRemoveJamSong = { idx -> rootViewModel.removeSongFromJam(idx) },
                    onReorderJamQueue = { from, to -> rootViewModel.reorderJamQueue(from, to) },
                    onToggleGuestReorder = { allow -> rootViewModel.setAttendeeReorderPermission(allow) },
                    onStepOutLocally = { rootViewModel.stepOutLocally() },
                    onResumeFromLocalStepOut = { rootViewModel.resumeFromLocalStepOut() },
                    onPlayItemByQid = { qid -> rootViewModel.playItemByQid(qid) },
                    onPromoteRecommended = { qid -> rootViewModel.promoteRecommended(qid) },
                    onRemoveByQid = { qid -> rootViewModel.removeByQid(qid) },
                    onMoveByQid = { qid, toIdx -> rootViewModel.moveByQid(qid, toIdx) },
                    onPlayNext = { song -> rootViewModel.playNext(song) },
                    onAddToQueue = { song -> rootViewModel.addToQueue(song) },
                    recommendedSongIds = recommendedSongIds,
                    onAddToPlaylist = { song -> rootViewModel.showAddToPlaylist(song) },
                    onSongLike = { song -> rootViewModel.toggleLike(song) },
                    onNavigateToJam = { navController.navigate(Screen.Jam.route) },
                    onDismiss = { showFullPlayer = false }
                )
            }

            val toastBottomOffset = when {
                showFullPlayer && playbackState.currentSong != null -> 80.dp
                showMiniPlayer && playbackState.currentSong != null && showBottomBar -> 164.dp
                showMiniPlayer && playbackState.currentSong != null -> 92.dp
                showBottomBar -> 86.dp
                else -> 24.dp
            }
            com.synq.ui.toast.SynqToastHost(bottomOffset = toastBottomOffset)
        }
        }
    }
}
