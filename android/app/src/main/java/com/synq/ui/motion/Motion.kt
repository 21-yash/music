package com.synq.ui.motion

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object MotionTokens {
    /** Item placement animation spec for LazyColumn/Row items (§4.7) */
    val ItemPlacementSpec = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Standard spring for interactive touch feedback */
    val PressSpring = spring<Float>(
        dampingRatio = 0.45f,
        stiffness = Spring.StiffnessMedium
    )

    const val CardPressScale = 0.97f
    const val TilePressScale = 0.97f
    const val RowPressScale = 0.98f
    const val ButtonPressScale = 0.96f
    const val ChipPressScale = 0.96f
    const val DefaultPressScale = 0.97f
}

/**
 * Combined spring press scale and clickable modifier sharing the same interaction source (§4.7).
 * - Collects PressInteraction events from a MutableInteractionSource shared with combinedClickable(indication = null).
 * - On Press: animates scale to pressedScale with a stiff spring.
 * - On Release/Cancel: waits until at least ~120ms have passed since Press, then animates back to 1f with
 *   spring(dampingRatio = 0.45f, stiffness = StiffnessMedium) with overshoot pop.
 * - Scales via graphicsLayer with zero recomposition per frame.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    pressedScale: Float = MotionTokens.DefaultPressScale,
    targetScale: Float = pressedScale,
    externalInteractionSource: MutableInteractionSource? = null
): Modifier = composed {
    val effectiveScale = if (pressedScale != MotionTokens.DefaultPressScale) pressedScale else targetScale
    val scale = remember { Animatable(1f) }
    val interactionSource = externalInteractionSource ?: remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource, effectiveScale) {
        var pressStartTime = 0L
        var releaseJob: Job? = null
        var pressJob: Job? = null

        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    releaseJob?.cancel()
                    pressStartTime = SystemClock.uptimeMillis()
                    pressJob = launch {
                        scale.animateTo(
                            targetValue = effectiveScale,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    val elapsed = SystemClock.uptimeMillis() - pressStartTime
                    val remaining = (120L - elapsed).coerceAtLeast(0L)
                    releaseJob?.cancel()
                    releaseJob = launch {
                        if (remaining > 0L) {
                            delay(remaining)
                        }
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 0.45f,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
            }
        }
    }

    val clickableModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick ?: {},
            onLongClick = onLongClick
        )
    } else {
        Modifier
    }

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .then(clickableModifier)
}

/**
 * Compatibility alias for pressScale delegating to pressable.
 */
fun Modifier.pressScale(
    targetScale: Float = MotionTokens.DefaultPressScale,
    interactionSource: MutableInteractionSource? = null
): Modifier = pressable(pressedScale = targetScale, externalInteractionSource = interactionSource)

/**
 * Helper extensions for standard app haptics (§4.7).
 */
fun HapticFeedback.performToggle(on: Boolean) {
    performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
}

fun HapticFeedback.performConfirm() {
    performHapticFeedback(HapticFeedbackType.Confirm)
}

fun HapticFeedback.performLongPress() {
    performHapticFeedback(HapticFeedbackType.LongPress)
}

