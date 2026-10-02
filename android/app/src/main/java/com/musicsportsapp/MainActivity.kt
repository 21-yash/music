package com.musicsportsapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.musicsportsapp.ui.theme.MusicSportsAppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity architecture.
 *
 * All navigation happens within Compose via Navigation Compose.
 * This activity just sets up edge-to-edge, applies the theme,
 * and hosts the root composable.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MusicSportsAppTheme {
                MusicSportsAppRoot()
            }
        }
    }
}
