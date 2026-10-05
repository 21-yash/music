package com.musicsportsapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
 * Hosts the top app bar, bottom navigation bar, and the navigation
 * host. The bottom bar is hidden on the Profile screen (since it's
 * not a bottom-nav tab). The top bar shows the current section title
 * and a profile avatar button.
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

    // Bottom bar is visible on all bottom-nav tabs, hidden on Profile
    val showBottomBar = currentRoute in Screen.bottomNavItems.map { it.route }
    val showTopBar = currentRoute != Screen.MatchDetails().route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                Column {
                    MiniPlayer(
                        playbackState = playbackState,
                        onPlayPauseClick = { rootViewModel.togglePlayPause() },
                        onNavigateToNowPlaying = { /* TODO: Open full player */ }
                    )
                    AppBottomNavBar(navController = navController)
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            AppNavHost(
                navController = navController,
                modifier = Modifier.fillMaxSize(),
            )
            
            // Floating Profile Avatar at Top End
            if (showTopBar && currentRoute != Screen.Profile.route) {
                IconButton(
                    onClick = {
                        navController.navigate(Screen.Profile.route) {
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                ) {
                    Card(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Profile",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}
