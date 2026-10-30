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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.synq.navigation.AppBottomNavBar
import com.synq.navigation.AppNavHost
import com.synq.navigation.Screen
import com.synq.features.music.components.MiniPlayer
import com.synq.features.music.jam.JamClientState
import com.synq.features.music.jam.JamUiEvent
import com.synq.features.music.jam.LocalCanAddSongsToJam
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

    // D6: Rejoin candidate dialog on app start
    var rejoinCandidateCode by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val candidate = rootViewModel.jamPreferences.rejoinCandidate()
        if (candidate != null && !jamState.isInJam) {
            rejoinCandidateCode = candidate
        }
    }

    if (rejoinCandidateCode != null && !jamState.isInJam) {
        val codeToRejoin = rejoinCandidateCode!!
        AlertDialog(
            onDismissRequest = {
                rootViewModel.jamPreferences.clearLastJam()
                rejoinCandidateCode = null
            },
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D7A))
                ) {
                    Text("Rejoin", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        rootViewModel.jamPreferences.clearLastJam()
                        rejoinCandidateCode = null
                    }
                ) {
                    Text("Dismiss")
                }
            }
        )
    }

    // C4: One-time battery optimization tip dialog to help background playback on aggressive battery savers
    var showBatteryOptimizationTip by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val hasSeen = rootViewModel.jamPreferences.hasSeenBatteryTip.value
        val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        val isIgnoring = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        if (!hasSeen && !isIgnoring) {
            showBatteryOptimizationTip = true
        }
    }

    if (showBatteryOptimizationTip) {
        AlertDialog(
            onDismissRequest = {
                rootViewModel.jamPreferences.setSeenBatteryTip(true)
                showBatteryOptimizationTip = false
            },
            title = {
                Text(
                    text = "Background Playback Tip",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "To keep music and Jam sessions playing continuously in the background (especially with the screen off on devices with aggressive OEM battery savers), disable battery optimization for Synq. You will only see this tip once.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        rootViewModel.jamPreferences.setSeenBatteryTip(true)
                        showBatteryOptimizationTip = false
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D7A))
                ) {
                    Text("Allow", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        rootViewModel.jamPreferences.setSeenBatteryTip(true)
                        showBatteryOptimizationTip = false
                    }
                ) {
                    Text("Dismiss")
                }
            }
        )
    }

    // Jam UI Event toasts (single source of truth: JamStateStore -> JamUiEvent -> this collector)
    LaunchedEffect(Unit) {
        rootViewModel.jamUiEvents.collect { event ->
            when (event) {
                is JamUiEvent.MemberJoined -> {
                    android.widget.Toast.makeText(context, "${event.username} joined the Jam", android.widget.Toast.LENGTH_SHORT).show()
                }
                is JamUiEvent.MemberLeft -> {
                    android.widget.Toast.makeText(context, "${event.username} left the Jam", android.widget.Toast.LENGTH_SHORT).show()
                }
                is JamUiEvent.HostChanged -> {
                    android.widget.Toast.makeText(context, "${event.newHostUsername} is now the host", android.widget.Toast.LENGTH_SHORT).show()
                }
                is JamUiEvent.Kicked -> {
                    android.widget.Toast.makeText(context, event.reason, android.widget.Toast.LENGTH_LONG).show()
                }
                is JamUiEvent.Toast -> {
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                }
                is JamUiEvent.PermChanged -> {
                    // Handled via JamUiEvent.Toast derived in JamStateStore
                }
            }
        }
    }

    // D4: Quiet reconnecting & reconnected toasts
    var prevClientState by remember { mutableStateOf<JamClientState?>(null) }
    LaunchedEffect(jamClientState) {
        val prev = prevClientState
        val curr = jamClientState
        if (prev is JamClientState.Active && curr is JamClientState.Reconnecting && curr.attempt == 1) {
            android.widget.Toast.makeText(context, "Reconnecting to Jam…", android.widget.Toast.LENGTH_SHORT).show()
        } else if (prev is JamClientState.Reconnecting && curr is JamClientState.Active) {
            android.widget.Toast.makeText(context, "Reconnected to Jam ✓", android.widget.Toast.LENGTH_SHORT).show()
        }
        prevClientState = curr
    }

    LaunchedEffect(jamState.error) {
        jamState.error?.let { err ->
            android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
            rootViewModel.clearJamError()
        }
    }

    LaunchedEffect(playbackState.error) {
        playbackState.error?.let { err ->
            android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Bottom bar is visible on all bottom-nav tabs, hidden on sub-pages
    val showBottomBar = currentRoute in Screen.bottomNavItems.map { it.route }
    val isPlaylistEditing by rootViewModel.isPlaylistEditing.collectAsState()
    val showMiniPlayer = currentRoute != Screen.MatchDetails().route && !isPlaylistEditing

    val user by rootViewModel.user.collectAsState()
    val showSignOutConfirmation by rootViewModel.showSignOutDialog.collectAsState()

    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { rootViewModel.dismissSignOut() },
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
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { rootViewModel.dismissSignOut() }) {
                    Text("Cancel")
                }
            }
        )
    }

    val showLeaveJamConfirm by rootViewModel.showLeaveJamConfirmDialog.collectAsState()
    if (showLeaveJamConfirm) {
        AlertDialog(
            onDismissRequest = { rootViewModel.dismissLeaveJamConfirmDialog() },
            title = {
                Text("Leave Jam?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Leave Jam and stop playback?")
            },
            confirmButton = {
                Button(
                    onClick = { rootViewModel.confirmLeaveJamAndStopPlayback() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Leave & Stop", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { rootViewModel.dismissLeaveJamConfirmDialog() }) {
                    Text("Cancel")
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
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onRemoveFromPlaylist = { playlist ->
                rootViewModel.removeSongFromPlaylist(playlist.serverId, song.id) { success, msg ->
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onCreatePlaylist = { name ->
                rootViewModel.createPlaylistAndAddSong(name, song) { success, msg ->
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
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
            android.widget.Toast.makeText(
                context,
                if (wasLiked) "Removed from Liked Songs" else "Added to Liked Songs",
                android.widget.Toast.LENGTH_SHORT
            ).show()
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
                                        android.widget.Toast.makeText(context, "Ask $jamHostName for playback control", android.widget.Toast.LENGTH_SHORT).show()
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
                                        android.widget.Toast.makeText(context, "Ask $jamHostName for playback control", android.widget.Toast.LENGTH_SHORT).show()
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
                        AppBottomNavBar(navController = navController)
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
        }
        }
    }
}
