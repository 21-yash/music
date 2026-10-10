package com.musicsportsapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

// Accent colors from the HTML prototype (indigo -> pink)
val GlassAccent = Color(0xFF6366F1)
val GlassAccentPink = Color(0xFFEC4899)
val GlassProgressBrush = Brush.horizontalGradient(listOf(GlassAccent, GlassAccentPink))

/** True when the current Material theme is dark (follows your in-app theme, not just the system). */
@Composable
fun isAppDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

class GlassContentColors(
    val primary: Color,
    val secondary: Color,
    val muted: Color,
    val track: Color
)

@Composable
fun glassContentColors(): GlassContentColors =
    if (isAppDark()) {
        GlassContentColors(
            primary = Color.White,
            secondary = Color(0xFFA1A1AA),
            muted = Color(0xFF71717A),
            track = Color.White.copy(alpha = 0.10f)
        )
    } else {
        GlassContentColors(
            primary = Color(0xFF0F172A),
            secondary = Color(0xFF475569),
            muted = Color(0xFF64748B),
            track = Color.Black.copy(alpha = 0.08f)
        )
    }

/**
 * Frosted "glass" card like the prototype's mini-player / nav bar:
 * translucent fill + 1dp border that is brighter on the top-left edges.
 *
 * Compose has no built-in backdrop blur, so the fill is more opaque than the
 * prototype's 5% to keep text readable over scrolling content.
 */
@Composable
fun Modifier.glassSurface(shape: Shape): Modifier {
    val dark = isAppDark()
    val fill = if (dark) Color(0xFF1A1A22).copy(alpha = 0.82f) else Color.White.copy(alpha = 0.80f)
    val borderBrush = Brush.linearGradient(
        if (dark) listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f))
        else listOf(Color.White, Color.Black.copy(alpha = 0.06f))
    )
    return this
        .clip(shape)
        .background(fill)
        .border(1.dp, borderBrush, shape)
}