package com.musicsportsapp.features.music.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Animated audio spectrum equalizer bars for currently playing tracks.
 * Features vertical bars animating smoothly with staggered timings.
 */
@Composable
fun AnimatedEqualizerBars(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFEC4899),
    isPlaying: Boolean = true,
    barCount: Int = 3,
    barWidth: Dp = 3.dp,
    spacing: Dp = 2.dp,
    maxHeight: Dp = 16.dp
) {
    if (isPlaying) {
        ActiveEqualizerBars(
            modifier = modifier,
            color = color,
            barCount = barCount,
            barWidth = barWidth,
            spacing = spacing,
            maxHeight = maxHeight
        )
    } else {
        StaticEqualizerBars(
            modifier = modifier,
            color = color,
            barCount = barCount,
            barWidth = barWidth,
            spacing = spacing,
            maxHeight = maxHeight
        )
    }
}

@Composable
private fun ActiveEqualizerBars(
    modifier: Modifier,
    color: Color,
    barCount: Int,
    barWidth: Dp,
    spacing: Dp,
    maxHeight: Dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )

    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 310, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )

    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    val h4 by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar4"
    )

    val animatedFractions = listOf(h1, h2, h3, h4)
    val totalWidth = (barWidth * barCount) + (spacing * (barCount - 1))

    Row(
        modifier = modifier
            .width(totalWidth)
            .height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.Bottom
    ) {
        animatedFractions.take(barCount).forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(fraction.coerceIn(0.15f, 1f))
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(color)
            )
        }
    }
}

@Composable
private fun StaticEqualizerBars(
    modifier: Modifier,
    color: Color,
    barCount: Int,
    barWidth: Dp,
    spacing: Dp,
    maxHeight: Dp
) {
    val staticFractions = listOf(0.35f, 0.65f, 0.45f, 0.30f)
    val totalWidth = (barWidth * barCount) + (spacing * (barCount - 1))

    Row(
        modifier = modifier
            .width(totalWidth)
            .height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.Bottom
    ) {
        staticFractions.take(barCount).forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(fraction.coerceIn(0.15f, 1f))
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(color)
            )
        }
    }
}
