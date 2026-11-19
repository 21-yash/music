package com.synq.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// ─── The Pink Family (exactly four, defined once) ───────────────────────────
val PinkPrimary = Color(0xFFFF2D7A) // Accent: buttons, selected states, active icons, links, FAB
val PinkSoft    = Color(0xFFFF6584) // Gradient end, secondary highlights, secondary accents
val PinkGlow    = Color(0xFFFF8FA3) // Light tint: highlights, text-on-dark accents, hero gradient start
val PinkDeep    = Color(0xFFD0165E) // Pressed/containers, hero gradient end, pink text on light theme

// ─── Dark Theme Surfaces & Neutrals (tinted to match pink — replaces slate) ─
val DarkBackground              = Color(0xFF141116)
val DarkSurface                 = Color(0xFF1E1822)
val DarkSurfaceContainerLowest  = Color(0xFF100D12)
val DarkSurfaceContainerLow     = Color(0xFF1A141E) // cards on bg
val DarkSurfaceContainer        = Color(0xFF1E1822)
val DarkSurfaceContainerHigh    = Color(0xFF261E2B) // menus, sheets, dialogs
val DarkSurfaceContainerHighest = Color(0xFF2E2535) // unselected chips, search fields
val DarkSurfaceVariant          = Color(0xFF2E2535)
val DarkOnBackground            = Color(0xFFF7F3F8)
val DarkOnSurface               = Color(0xFFF7F3F8)
val DarkOnSurfaceVariant        = Color(0xFFA79EAD) // secondary text
val DarkOutline                 = Color(0xFF5A4F61)
val DarkOutlineVariant          = Color(0xFF3A2F42)

val DarkPrimary                 = PinkPrimary
val DarkOnPrimary               = Color(0xFFFFFFFF)
val DarkPrimaryContainer        = PinkDeep
val DarkOnPrimaryContainer      = Color(0xFFFFFFFF)
val DarkSecondary               = PinkSoft
val DarkOnSecondary             = Color(0xFFFFFFFF)
val DarkSecondaryContainer      = Color(0xFF2E2535)
val DarkOnSecondaryContainer    = Color(0xFFF7F3F8)
val DarkTertiary                = PinkGlow
val DarkOnTertiary              = Color(0xFF3A0A1C)
val DarkTertiaryContainer       = Color(0xFF3A1B2C)
val DarkOnTertiaryContainer     = Color(0xFFF7F3F8)
val DarkError                   = Color(0xFFEF4444)
val DarkOnError                 = Color(0xFFFFFFFF)

// ─── Light Theme Surfaces & Neutrals (tinted, not slate) ───────────────────
val LightBackground              = Color(0xFFFBF4F7)
val LightSurface                 = Color(0xFFFFFFFF)
val LightSurfaceContainerLowest  = Color(0xFFFFFFFF)
val LightSurfaceContainerLow     = Color(0xFFFBF7F9)
val LightSurfaceContainer        = Color(0xFFF7EFF3)
val LightSurfaceContainerHigh    = Color(0xFFF3E8EE)
val LightSurfaceContainerHighest = Color(0xFFEDDFE7)
val LightSurfaceVariant          = Color(0xFFF3E8EE)
val LightOnBackground            = Color(0xFF1C1420)
val LightOnSurface               = Color(0xFF1C1420)
val LightOnSurfaceVariant        = Color(0xFF6B6070)
val LightOutline                 = Color(0xFFB9A9B4)
val LightOutlineVariant          = Color(0xFFE6D6DF)

val LightPrimary                 = PinkPrimary
val LightOnPrimary               = Color(0xFFFFFFFF)
val LightPrimaryContainer        = PinkDeep
val LightOnPrimaryContainer      = Color(0xFFFFFFFF)
val LightSecondary               = PinkSoft
val LightOnSecondary             = Color(0xFFFFFFFF)
val LightSecondaryContainer      = Color(0xFFEDDFE7)
val LightOnSecondaryContainer    = Color(0xFF1C1420)
val LightTertiary                = PinkGlow
val LightOnTertiary              = Color(0xFF3A0A1C)
val LightTertiaryContainer       = Color(0xFFF6D5E2)
val LightOnTertiaryContainer     = Color(0xFF1C1420)
val LightError                   = Color(0xFFEF4444)
val LightOnError                 = Color(0xFFFFFFFF)

// ─── Sports Accent (Scoped: Sports tab, SportsScreen, MatchDetailsScreen) ───
// Lime: dark #C6F135, light #4D7C0F; onSportsAccent dark #141116, light #FFFFFF
// Commented alternative for testing: Amber #FFB020 / #B45309
val SportsAccentDark    = Color(0xFFC6F135)
val SportsAccentLight   = Color(0xFF4D7C0F)
val OnSportsAccentDark  = Color(0xFF141116)
val OnSportsAccentLight = Color(0xFFFFFFFF)

// Dynamic accessors that follow the current theme
val SportsAccent: Color
    @Composable
    @ReadOnlyComposable
    get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) SportsAccentDark else SportsAccentLight

val OnSportsAccent: Color
    @Composable
    @ReadOnlyComposable
    get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) OnSportsAccentDark else OnSportsAccentLight

// ─── Semantic Tokens (not pink, not themeable away) ─────────────────────────
val SemanticLive    = Color(0xFFEF4444)
val SemanticSuccess = Color(0xFF10B981)
val SemanticWarning = Color(0xFFFBBF24) // match status highlight amber from HomeScreen MatchStatus
