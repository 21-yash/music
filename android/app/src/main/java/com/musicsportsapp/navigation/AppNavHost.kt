package com.musicsportsapp.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.musicsportsapp.features.home.HomeScreen
import com.musicsportsapp.features.library.LibraryScreen
import com.musicsportsapp.features.music.MusicScreen
import com.musicsportsapp.features.profile.ProfileScreen
import com.musicsportsapp.features.search.SearchScreen
import com.musicsportsapp.features.sports.SportsScreen

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
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier.fillMaxSize(),
        enterTransition = { fadeIn(animationSpec = tween(200)) },
        exitTransition = { fadeOut(animationSpec = tween(200)) },
        popEnterTransition = { fadeIn(animationSpec = tween(200)) },
        popExitTransition = { fadeOut(animationSpec = tween(200)) },
    ) {
        composable(Screen.Home.route) {
            HomeScreen()
        }

        composable(Screen.Music.route) {
            MusicScreen()
        }

        composable(Screen.Sports.route) {
            SportsScreen(
                onMatchClick = { matchId ->
                    navController.navigate(Screen.MatchDetails().createRoute(matchId))
                }
            )
        }

        composable(
            route = Screen.MatchDetails().route,
        ) {
            com.musicsportsapp.features.sports.MatchDetailsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen()
        }

        composable(Screen.Library.route) {
            LibraryScreen()
        }

        composable(Screen.Profile.route) {
            ProfileScreen()
        }
    }
}
