package com.synq.ui.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset

object MotionTokens {
    /** Item placement animation spec for LazyColumn/Row items (§4.7) */
    val ItemPlacementSpec = spring<IntOffset>(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Standard spring for interactive touch feedback */
    val PressSpring = spring<Float>(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessMedium
    )

    const val CardPressScale = 0.98f
    const val ButtonPressScale = 0.96f
    const val ChipPressScale = 0.96f
    const val DefaultPressScale = 0.97f
}

/**
 * Spring-based press scale modifier (§4.7).
 * Animates scale on press without swallowing or modifying click handlers.
 */
fun Modifier.pressScale(
    targetScale: Float = MotionTokens.DefaultPressScale,
    interactionSource: MutableInteractionSource? = null
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1.0f,
        animationSpec = MotionTokens.PressSpring,
        label = "pressScale"
    )

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

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

