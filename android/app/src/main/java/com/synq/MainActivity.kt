package com.synq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.synq.ui.theme.SynqTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity architecture.
 *
 * All navigation happens within Compose via Navigation Compose.
 * Android OS handles the startup splash window via core-splashscreen,
 * immediately displaying the Synq logo on cold start before rendering SynqAppRoot.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val rootViewModel: RootViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep splash screen visible until initial auth state is loaded from DataStore
        splashScreen.setKeepOnScreenCondition {
            rootViewModel.isLoggedIn.value == null
        }

        enableEdgeToEdge()
        setContent {
            val isDarkTheme by rootViewModel.isDarkTheme.collectAsState()

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as android.app.Activity).window
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkTheme
                    WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDarkTheme
                }
            }

            SynqTheme(darkTheme = isDarkTheme) {
                SynqAppRoot(rootViewModel = rootViewModel)
            }
        }
    }
}


