package com.synq.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Phase delay between neighbouring bars, in radians (about 52 degrees). Smaller = calmer, more coherent wave. */
private const val WaveStep = 0.9f

/** Height of the tallest bar at its peak, as a fraction of the loader size. */
private const val MaxHeightFraction = 0.74f

/** How low a bar sinks, as a fraction of its own peak height. */
private const val RestFraction = 0.35f

/** One full wave cycle. */
private const val CycleMillis = 1100

/** Bars in the middle swing taller than bars at the edges, which gives the soft "hill" shape. */
private fun envelopeFor(count: Int): FloatArray = when (count) {
    3 -> floatArrayOf(0.70f, 1f, 0.70f)
    5 -> floatArrayOf(0.45f, 0.75f, 1f, 0.75f, 0.45f)
    else -> FloatArray(count) { i ->
        val mid = (count - 1) / 2f
        1f - 0.55f * abs(i - mid) / maxOf(mid, 1f)
    }
}

/**
 * Branded Synq equalizer loading indicator.
 *
 * Bars swing from their vertical center in a smooth wave that travels left to right.
 * Every measurement is a fraction of [size], so the loader scales cleanly.
 * Works best with 3 bars (small, inline) or 5 bars (screens).
 *
 * @param size Width and height of the loader.
 * @param barCount 5 for screens, 3 for compact inline buttons.
 * @param colors Gradient applied top to bottom on each bar.
 */
@Composable
fun SynqLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    barCount: Int = 5,
    colors: List<Color> = listOf(SynqPinkLight, SynqPink, SynqPinkDark),
    contentDescription: String = "Loading"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SynqEqualizerWave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = CycleMillis, easing = LinearEasing)
        ),
        label = "phase"
    )
    val envelope = remember(barCount) { envelopeFor(barCount) }

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription }
    ) {
        val s = this.size.minDimension

        // Slim bars with open gaps read calmer than fat, tightly packed ones.
        val barWidth = s * if (barCount <= 3) 0.19f else 0.115f
        val pitch = s * if (barCount <= 3) 0.33f else 0.20f
        val clusterWidth = (barCount - 1) * pitch + barWidth
        val startX = (this.size.width - clusterWidth) / 2f
        val centerY = this.size.height / 2f
        val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)

        for (i in 0 until barCount) {
            // Minus sign: the crest reaches bar 0 first, then bar 1, and so on (left to right).
            val wave = 0.5f + 0.5f * sin(phase - i * WaveStep)
            val barHeight = s * MaxHeightFraction * envelope[i] * (RestFraction + (1f - RestFraction) * wave)

            val barTop = centerY - barHeight / 2f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = colors,
                    startY = barTop,
                    endY = barTop + barHeight
                ),
                topLeft = Offset(startX + i * pitch, barTop),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius
            )
        }
    }
}