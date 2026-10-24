package com.musicsportsapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Column
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.musicsportsapp.navigation.AppBottomNavBar
import com.musicsportsapp.navigation.AppNavHost
import com.musicsportsapp.navigation.Screen
import com.musicsportsapp.features.music.components.MiniPlayer

/**
 * Root composable for the app — the main shell.
 *
 * Hosts the floating profile avatar (with its dropdown menu), bottom navigation
 * bar, mini player, and the navigation host. The bottom bar is hidden on the
 * Profile screen (since it's not a bottom-nav tab). The avatar floats at the
 * top-end of every screen except MatchDetails and Profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSportsAppRoot(
    rootViewModel: RootViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val playbackState by rootViewModel.playbackState.collectAsState()
    val jamState by rootViewModel.jamState.collectAsState()
    val recommendedSongIds by rootViewModel.recommendedSongIds.collectAsState()
    val playlists by rootViewModel.playlists.collectAsState()
    val songForPlaylistAdd by rootViewModel.songForPlaylistAdd.collectAsState()
    val likedSongIds by rootViewModel.likedSongIds.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    var showFullPlayer by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(playbackState.error) {
        playbackState.error?.let { err ->
            android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Bottom bar is visible on all bottom-nav tabs, hidden on sub-pages
    val showBottomBar = currentRoute in Screen.bottomNavItems.map { it.route }
    val isPlaylistEditing by rootViewModel.isPlaylistEditing.collectAsState()
    val showMiniPlayer = currentRoute != Screen.MatchDetails().route && !isPlaylistEditing

    val isLoggedIn by rootViewModel.isLoggedIn.collectAsState(initial = false)
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
                    text = "Are you sure you want to sign out of your account?",
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

    songForPlaylistAdd?.let { song ->
        com.musicsportsapp.features.music.components.AddToPlaylistSheet(
            song = song,
            playlists = playlists,
            onSelectPlaylist = { playlist ->
                rootViewModel.addSongToExistingPlaylist(playlist.serverId, song) { success, msg ->
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
        com.musicsportsapp.features.music.jam.LocalIsInJam provides jamState.isInJam,
        com.musicsportsapp.features.music.components.LocalAddToPlaylist provides { song ->
            rootViewModel.showAddToPlaylist(song)
        },
        com.musicsportsapp.features.music.components.LocalOnNavigateToSongDetails provides { songId ->
            showFullPlayer = false
            navController.navigate(com.musicsportsapp.navigation.Screen.SongDetails().createRoute(songId))
        },
        com.musicsportsapp.features.music.components.LocalLikedSongIds provides likedSongIds,
        com.musicsportsapp.features.music.components.LocalToggleLike provides { song ->
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
                        MiniPlayer(
                            playbackState = playbackState,
                            onPlayPauseClick = { rootViewModel.togglePlayPause() },
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
                val jamState by rootViewModel.jamState.collectAsState()

                com.musicsportsapp.features.music.FullPlayerSheet(
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
                    isLiked = isLiked,
                    onLikeClick = { rootViewModel.toggleLike(currentSong) },
                    onPlayPauseClick = { rootViewModel.togglePlayPause() },
                    onNextClick = { rootViewModel.skipToNext() },
                    onPreviousClick = { rootViewModel.skipToPrevious() },
                    onSeek = { ratio -> rootViewModel.seekToRatio(ratio) },
                    onSeekToMs = { ms -> rootViewModel.seekTo(ms) },
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