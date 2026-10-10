package com.musicsportsapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.musicsportsapp.ui.theme.MusicSportsAppTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState

/**
 * Single-activity architecture.
 *
 * All navigation happens within Compose via Navigation Compose.
 * This activity just sets up edge-to-edge, applies the theme,
 * and hosts the root composable.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val rootViewModel: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkTheme = rootViewModel.isDarkTheme.collectAsState().value
            
            val view = androidx.compose.ui.platform.LocalView.current
            if (!view.isInEditMode) {
                androidx.compose.runtime.SideEffect {
                    val window = (view.context as android.app.Activity).window
                    androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkTheme
                    androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDarkTheme
                }
            }
            
            MusicSportsAppTheme(darkTheme = isDarkTheme) {
                MusicSportsAppRoot(rootViewModel = rootViewModel)
            }
        }
    }
}
