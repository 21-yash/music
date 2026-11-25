package com.synq.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val PinkGradient     = Brush.linearGradient(listOf(PinkPrimary, PinkSoft))              // buttons, Jam card, hero chips
val PinkGradientHero = Brush.linearGradient(listOf(PinkGlow, PinkPrimary, PinkDeep))    // logo-like hero surfaces, avatars
val PinkGradientSoft = Brush.verticalGradient(listOf(PinkPrimary.copy(alpha = 0.28f), Color.Transparent)) // tinted tops of headers/tiles

