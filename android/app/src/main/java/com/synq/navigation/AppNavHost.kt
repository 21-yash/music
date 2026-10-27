package com.synq.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.synq.features.home.HomeScreen
import com.synq.features.library.LibraryScreen
import com.synq.features.music.MusicScreen
import com.synq.features.profile.ProfileScreen
import com.synq.features.search.SearchScreen
import com.synq.features.sports.SportsScreen

/**
 * Main navigation host for the app.
 *
 * Contains all top-level tab destinations. Each tab can later define
 * its own nested navigation graph as features grow (e.g., Music →
 * Artist detail → Album detail).
 *
 * Transition: gentle fade to feel native and fast, avoiding jarring
 * slide animations between bottom-nav destinations.
 */

private val detailRoutes = listOf("match_details", "artist", "playlist", "album", "search", "auth", "music/jam", "music/popular_artists", "music/playlists")
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    rootViewModel: com.synq.RootViewModel,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier.fillMaxSize(),
        enterTransition = {
            if (detailRoutes.any { targetState.destination.route?.startsWith(it) == true }) {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
            } else {
                fadeIn(animationSpec = tween(200))
            }
        },
        exitTransition = {
            if (detailRoutes.any { targetState.destination.route?.startsWith(it) == true }) {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
            } else {
                fadeOut(animationSpec = tween(200))
            }
        },
        popEnterTransition = {
            if (detailRoutes.any { initialState.destination.route?.startsWith(it) == true }) {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
            } else {
                fadeIn(animationSpec = tween(200))
            }
        },
        popExitTransition = {
            if (detailRoutes.any { initialState.destination.route?.startsWith(it) == true }) {
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
            } else {
                fadeOut(animationSpec = tween(200))
            }
        },
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                navController = navController,
                rootViewModel = rootViewModel,
                onNavigateToJam = { navController.navigate(Screen.Jam.route) },
                onMatchClick = { matchId ->
                    navController.navigate(Screen.MatchDetails().createRoute(matchId))
                },
                onSeeAllSports = {
                    navController.navigate(Screen.Sports.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onSeeAllRecentlyPlayed = {
                    navController.navigate(Screen.ListeningHistory.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.Music.route) {
            MusicScreen(
                navController = navController,
                rootViewModel = rootViewModel,
                onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                onNavigateToArtist = { artistId -> navController.navigate(Screen.Artist().createRoute(artistId)) },
                onNavigateToPlaylist = { playlistId -> navController.navigate(Screen.Playlist().createRoute(playlistId)) },
                onNavigateToAlbum = { albumId -> navController.navigate(Screen.Album().createRoute(albumId)) },
                onNavigateToPopularArtists = { navController.navigate(Screen.PopularArtists.route) }
            )
        }

        composable(Screen.PopularArtists.route) { backStackEntry ->
            val musicEntry = androidx.compose.runtime.remember(backStackEntry) {
                runCatching { navController.getBackStackEntry(Screen.Music.route) }.getOrNull()
            }
            val popularArtistsViewModel: com.synq.features.music.MusicViewModel = if (musicEntry != null) {
                androidx.hilt.navigation.compose.hiltViewModel(musicEntry)
            } else {
                androidx.hilt.navigation.compose.hiltViewModel()
            }
            com.synq.features.music.PopularArtistsScreen(
                viewModel = popularArtistsViewModel,
                onBackClick = { navController.popBackStack() },
                onNavigateToArtist = { artistId -> navController.navigate(Screen.Artist().createRoute(artistId)) }
            )
        }

        composable(
            route = Screen.Artist().route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://21yash.online/artist/{artistId}" },
                navDeepLink { uriPattern = "synq://artist/{artistId}" }
            )
        ) { backStackEntry ->
            val artistId = backStackEntry.arguments?.getString("artistId") ?: ""
            com.synq.features.music.ArtistScreen(
                artistId = artistId,
                onBackClick = { navController.popBackStack() },
                onNavigateToAlbum = { albumId -> navController.navigate(Screen.Album().createRoute(albumId)) }
            )
        }

        composable(
            route = Screen.Playlist().route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://21yash.online/playlist/{playlistId}" },
                navDeepLink { uriPattern = "synq://playlist/{playlistId}" }
            )
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
            com.synq.features.music.PlaylistScreen(
                playlistId = playlistId,
                rootViewModel = rootViewModel,
                onBackClick = { navController.popBackStack() },
                onNavigateToArtist = { artistId -> navController.navigate(Screen.Artist().createRoute(artistId)) },
                onNavigateToSearch = { navController.navigate(Screen.Search.route) }
            )
        }

        composable(
            route = Screen.Album().route,
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://21yash.online/album/{albumId}" },
                navDeepLink { uriPattern = "synq://album/{albumId}" }
            )
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString("albumId") ?: ""
            com.synq.features.music.AlbumScreen(
                albumId = albumId,
                onBackClick = { navController.popBackStack() },
                onNavigateToArtist = { artistId -> navController.navigate(Screen.Artist().createRoute(artistId)) }
            )
        }

        composable(
            route = Screen.SongDetails().route,
            arguments = listOf(
                navArgument("songId") { type = NavType.StringType },
                navArgument("provider") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "https://21yash.online/track/{songId}" },
                navDeepLink { uriPattern = "https://21yash.online/song/{songId}" },
                navDeepLink { uriPattern = "synq://track/{songId}" },
                navDeepLink { uriPattern = "synq://song/{songId}" }
            )
        ) { backStackEntry ->
            val songId = backStackEntry.arguments?.getString("songId") ?: ""
            val provider = backStackEntry.arguments?.getString("provider")
            com.synq.features.music.SongDetailScreen(
                songId = songId,
                provider = provider,
                onBackClick = { navController.popBackStack() },
                onNavigateToArtist = { artistId -> navController.navigate(Screen.Artist().createRoute(artistId)) },
                onNavigateToAlbum = { albumId -> navController.navigate(Screen.Album().createRoute(albumId)) }
            )
        }

        composable(Screen.Sports.route) {
            SportsScreen(
                navController = navController,
                rootViewModel = rootViewModel,
                onMatchClick = { matchId ->
                    navController.navigate(Screen.MatchDetails().createRoute(matchId))
                }
            )
        }

        composable(
            route = Screen.MatchDetails().route,
        ) {
            com.synq.features.sports.MatchDetailsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                rootViewModel = rootViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                navController = navController,
                rootViewModel = rootViewModel,
                onNavigateToLogin = {
                    navController.navigate(Screen.Auth.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.ListeningHistory.route) {
                        launchSingleTop = true
                    }
                },
                onNavigateToAllPlaylists = {
                    navController.navigate(Screen.AllPlaylists.route) {
                        launchSingleTop = true
                    }
                },
                onPlaylistClick = { playlistId ->
                    navController.navigate(Screen.Playlist().createRoute(playlistId))
                }
            )
        }

        composable(Screen.AllPlaylists.route) {
            com.synq.features.library.AllPlaylistsScreen(
                onBackClick = { navController.popBackStack() },
                onPlaylistClick = { playlistId ->
                    navController.navigate(Screen.Playlist().createRoute(playlistId))
                }
            )
        }

        composable(Screen.ListeningHistory.route) {
            com.synq.features.music.history.ListeningHistoryScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToLogin = {
                    navController.navigate(Screen.Auth.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onBackClick = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                onNavigateToAuth = {
                    navController.navigate(Screen.Auth.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.Auth.route) {
            com.synq.features.auth.AuthScreen(
                onBackClick = { navController.popBackStack() },
                onAuthSuccess = { navController.popBackStack() }
            )
        }

        composable(Screen.Jam.route) {
            com.synq.features.music.jam.JamScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                rootViewModel = rootViewModel
            )
        }
    }
}
