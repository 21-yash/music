package com.synq

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.synq.features.sports.notifications.SynqFirebaseMessagingService
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

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled by OS */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep splash screen visible until initial auth state is loaded from DataStore
        splashScreen.setKeepOnScreenCondition {
            rootViewModel.isLoggedIn.value == null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission && savedInstanceState == null) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        enableEdgeToEdge()
        if (savedInstanceState == null) {
            handleNotificationIntent(intent)
        }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val extraMatchId = intent?.getStringExtra(SynqFirebaseMessagingService.EXTRA_MATCH_ID)
        val uriMatchId = intent?.data?.takeIf {
            it.scheme == "synq" && it.host == "sports" && it.pathSegments.size >= 2 && it.pathSegments[0] == "match"
        }?.pathSegments?.getOrNull(1)
        val matchId = extraMatchId?.takeIf { it.isNotBlank() } ?: uriMatchId?.takeIf { it.isNotBlank() }
        if (matchId != null) {
            intent?.removeExtra(SynqFirebaseMessagingService.EXTRA_MATCH_ID)
            if (uriMatchId != null) {
                intent?.data = null
            }
            rootViewModel.onNotificationMatchId(matchId)
        }
    }
}


