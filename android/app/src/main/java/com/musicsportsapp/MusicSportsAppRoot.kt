package com.musicsportsapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    
    var showFullPlayer by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    // Bottom bar is visible on all bottom-nav tabs, hidden on sub-pages
    val showBottomBar = currentRoute in Screen.bottomNavItems.map { it.route }
    // Floating avatar is strictly visible ONLY on the 4 primary tabs: Home, Music, Sports, Library
    val showFloatingAvatar = currentRoute in Screen.bottomNavItems.map { it.route } && !showFullPlayer
    val showMiniPlayer = currentRoute != Screen.MatchDetails().route

    val isLoggedIn by rootViewModel.isLoggedIn.collectAsState(initial = false)
    val user by rootViewModel.user.collectAsState()
    var showSignOutConfirmation by remember { mutableStateOf(false) }

    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmation = false },
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
                        showSignOutConfirmation = false
                        rootViewModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            AppNavHost(
                navController = navController,
                modifier = Modifier.fillMaxSize(),
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
                            onNavigateToNowPlaying = { showFullPlayer = true }
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
            
            // Floating Profile Avatar at Top End (gradient circle + dropdown menu)
            // Rendered exclusively on Home, Music, Sports, and Library (hidden when FullPlayer is open)
            if (showFloatingAvatar && !showFullPlayer) {
                ProfileAvatarMenu(
                    isLoggedIn = isLoggedIn,
                    userName = if (isLoggedIn) (user?.username ?: "User") else "Guest",
                    planLabel = if (isLoggedIn) (user?.email ?: "Account & Profile") else "Tap to sign in",
                    onProfileClick = {
                        if (isLoggedIn) {
                            navController.navigate(Screen.Profile.route) {
                                launchSingleTop = true
                            }
                        } else {
                            navController.navigate(Screen.Auth.route) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onSignInClick = {
                        navController.navigate(Screen.Auth.route) {
                            launchSingleTop = true
                        }
                    },
                    onToggleTheme = { rootViewModel.toggleTheme() },
                    onSettingsClick = {
                        navController.navigate(Screen.Profile.route) {
                            launchSingleTop = true
                        }
                    },
                    onHistoryClick = {
                        navController.navigate(Screen.ListeningHistory.route) {
                            launchSingleTop = true
                        }
                    },
                    onLogoutClick = {
                        showSignOutConfirmation = true
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 16.dp, end = 24.dp)
                )
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
                    onDismiss = { showFullPlayer = false }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Profile avatar + dropdown
// ─────────────────────────────────────────────────────────────

private val AvatarGradient = Brush.linearGradient(
    listOf(Color(0xFF6366F1), Color(0xFFEC4899))
)

@Composable
private fun ProfileAvatarMenu(
    isLoggedIn: Boolean,
    userName: String,
    planLabel: String,
    onProfileClick: () -> Unit,
    onSignInClick: () -> Unit,
    onToggleTheme: () -> Unit,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val initial = if (isLoggedIn) userName.firstOrNull()?.uppercase() ?: "U" else "?"

    Box(modifier = modifier) {
        GradientAvatar(initial = initial, onClick = { expanded = true })

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(0.dp, 8.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(220.dp)
        ) {
            // Header row: tapping it opens the Profile or Auth screen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = false; onProfileClick() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GradientAvatar(initial = initial)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = userName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = planLabel,
                        color = Color(0xFFEC4899),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            )
            Spacer(Modifier.height(4.dp))

            ProfileMenuItem("Toggle Theme", Icons.Default.Star) { expanded = false; onToggleTheme() }
            if (isLoggedIn) {
                ProfileMenuItem("Settings", Icons.Default.Settings) { expanded = false; onSettingsClick() }
                ProfileMenuItem("Listening History", Icons.Default.Refresh) { expanded = false; onHistoryClick() }
                ProfileMenuItem("Log Out", Icons.Default.ExitToApp, tint = Color(0xFFEF4444)) {
                    expanded = false
                    onLogoutClick()
                }
            } else {
                ProfileMenuItem("Sign In", Icons.Default.AccountCircle, tint = MaterialTheme.colorScheme.primary) {
                    expanded = false
                    onSignInClick()
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuItem(
    label: String,
    icon: ImageVector,
    tint: Color? = null,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                color = tint ?: MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        },
        onClick = onClick
    )
}

@Composable
private fun GradientAvatar(initial: String, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(AvatarGradient)
            .border(2.dp, Color.White.copy(alpha = 0.1f), CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}