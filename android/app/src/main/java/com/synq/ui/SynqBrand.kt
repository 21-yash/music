package com.synq.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.synq.R

// ─── Brand Colors ─────────────────────────────────────────────────────────────
val SynqPink = Color(0xFFFF2D55)
val SynqPinkLight = Color(0xFFFF8FA3)
val SynqPinkDark = Color(0xFFD0165E)
val SynqBackground = Color(0xFF141116)
val SynqSurface = Color(0xFF1E1822)

val SynqPinkGradient = Brush.linearGradient(
    listOf(SynqPinkLight, SynqPink, SynqPinkDark)
)

/**
 * Builds an AnnotatedString for the Synq brand name where:
 * - "Syn" / "syn" is styled with [synColor] (default White)
 * - "q" is styled with [qColor] (default SynqPink #FF2D55)
 */
fun buildSynqAnnotatedString(
    capitalized: Boolean = true,
    synColor: Color = Color.White,
    qColor: Color = SynqPink,
    suffix: String = ""
): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = synColor)) {
        append(if (capitalized) "Syn" else "syn")
    }
    withStyle(SpanStyle(color = qColor)) {
        append("q")
    }
    if (suffix.isNotEmpty()) {
        withStyle(SpanStyle(color = synColor.copy(alpha = 0.7f))) {
            append(suffix)
        }
    }
}

/**
 * Reusable Composable that renders the Synq brand text with "Syn"/"syn" in white and "q" in pink.
 */
@Composable
fun SynqBrandedText(
    modifier: Modifier = Modifier,
    capitalized: Boolean = true,
    fontSize: TextUnit = 28.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    letterSpacing: TextUnit = (-0.5).sp,
    synColor: Color = Color.White,
    qColor: Color = SynqPink,
    suffix: String = "",
    style: TextStyle = MaterialTheme.typography.headlineMedium
) {
    Text(
        text = buildSynqAnnotatedString(
            capitalized = capitalized,
            synColor = synColor,
            qColor = qColor,
            suffix = suffix
        ),
        modifier = modifier,
        fontSize = fontSize,
        fontWeight = fontWeight,
        letterSpacing = letterSpacing,
        style = style
    )
}

/**
 * Renders the official Synq app logo with rounded curvature and optional ambient pink glow.
 */
@Composable
fun SynqLogo(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    showGlow: Boolean = true
) {
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (showGlow) {
                    Modifier.drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(SynqPink.copy(alpha = 0.40f), Color.Transparent),
                                center = center,
                                radius = size.toPx() * 0.85f
                            )
                        )
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = R.mipmap.ic_launcher,
            contentDescription = "synq logo",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(size * 0.22f))
        )
    }
}
