package com.synq.ui.toast

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.synq.ui.isAppDark
import com.synq.ui.motion.MotionTokens
import com.synq.ui.motion.pressable
import com.synq.ui.theme.PinkDeep
import com.synq.ui.theme.PinkPrimary
import com.synq.ui.theme.SemanticLive
import com.synq.ui.theme.SemanticSuccess
import com.synq.ui.theme.SemanticWarning
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SynqToastType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class SynqToastData(
    val message: String,
    val type: SynqToastType,
    val durationMs: Long,
    val revision: Long
)

/**
 * Global single-slot glassmorphic toast manager with anti-flood deduplication.
 *
 * - Never queues a backlog of toasts: a new toast replaces the active one smoothly in place.
 * - Drops identical messages repeated within [DUPLICATE_COOLDOWN_MS] to stop rapid-tap spam.
 */
object SynqToastManager {
    private const val SHORT_MS = 2000L
    private const val LONG_MS = 3200L
    private const val DUPLICATE_COOLDOWN_MS = 900L

    private val lock = Any()
    private val _currentToast = MutableStateFlow<SynqToastData?>(null)
    val currentToast: StateFlow<SynqToastData?> = _currentToast.asStateFlow()

    private val _modalDepth = MutableStateFlow(0)
    val modalDepth: StateFlow<Int> = _modalDepth.asStateFlow()

    private var lastMessage: String? = null
    private var lastShownAtMs: Long = 0L
    private var nextRevision: Long = 0L

    fun show(
        message: String,
        type: SynqToastType = inferType(message),
        longDuration: Boolean = false
    ) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return

        synchronized(lock) {
            val now = System.currentTimeMillis()
            val active = _currentToast.value
            if (active != null && trimmed == lastMessage && (now - lastShownAtMs) < DUPLICATE_COOLDOWN_MS) {
                return
            }
            lastMessage = trimmed
            lastShownAtMs = now
            _currentToast.value = SynqToastData(
                message = trimmed,
                type = type,
                durationMs = if (longDuration) LONG_MS else SHORT_MS,
                revision = ++nextRevision
            )
        }
    }

    fun dismiss(revision: Long? = null) {
        synchronized(lock) {
            if (revision == null || _currentToast.value?.revision == revision) {
                _currentToast.value = null
            }
        }
    }

    internal fun pushModal() {
        synchronized(lock) {
            _modalDepth.value = _modalDepth.value + 1
        }
    }

    internal fun popModal() {
        synchronized(lock) {
            _modalDepth.value = (_modalDepth.value - 1).coerceAtLeast(0)
        }
    }

    private fun inferType(message: String): SynqToastType {
        val lower = message.lowercase()
        return when {
            lower.contains("fail") ||
                lower.contains("error") ||
                lower.contains("couldn't") ||
                lower.contains("not available") ||
                lower.contains("removed from this jam") ||
                lower.contains("kicked") -> SynqToastType.ERROR

            lower.contains("ask ") ||
                lower.contains("permission") ||
                lower.contains("disabled") ||
                lower.contains("managed by") -> SynqToastType.WARNING

            lower.contains("saved") ||
                lower.contains("created") ||
                lower.contains("added") ||
                lower.contains("updated") ||
                lower.contains("cleared") ||
                lower.contains("copied") ||
                lower.contains("joined") ||
                lower.contains("removed") ||
                lower.contains("deleted") -> SynqToastType.SUCCESS

            else -> SynqToastType.INFO
        }
    }
}

/**
 * Host rendered inside [com.synq.SynqAppRoot] (pauses automatically while a [ModalBottomSheet]
 * with [SynqToastModalOverlay] is mounted so sheets always receive the single active toast).
 */
@Composable
fun SynqToastHost(
    bottomOffset: Dp
) {
    val toast by SynqToastManager.currentToast.collectAsState()
    val modalDepth by SynqToastManager.modalDepth.collectAsState()

    LaunchedEffect(toast?.revision) {
        val active = toast ?: return@LaunchedEffect
        delay(active.durationMs)
        SynqToastManager.dismiss(active.revision)
    }

    if (modalDepth == 0) {
        SynqToastPopup(
            toast = toast,
            bottomOffset = bottomOffset
        )
    }
}

/**
 * Host placed inside [ModalBottomSheet]s so toasts appear cleanly above the sheet window.
 */
@Composable
fun SynqToastModalOverlay(
    bottomOffset: Dp = 24.dp
) {
    val toast by SynqToastManager.currentToast.collectAsState()

    DisposableEffect(Unit) {
        SynqToastManager.pushModal()
        onDispose { SynqToastManager.popModal() }
    }

    SynqToastPopup(
        toast = toast,
        bottomOffset = bottomOffset
    )
}

@Composable
private fun SynqToastPopup(
    toast: SynqToastData?,
    bottomOffset: Dp
) {
    var displayToast by remember { mutableStateOf(toast) }
    if (toast != null) {
        displayToast = toast
    }
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = toast != null

    val activeToast = displayToast ?: return
    if (!visibleState.currentState && !visibleState.targetState) return

    Popup(
        alignment = Alignment.BottomCenter,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = bottomOffset),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + scaleIn(
                    initialScale = 0.92f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(tween(180, easing = FastOutSlowInEasing)),
                exit = slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = tween(160, easing = FastOutSlowInEasing)
                ) + scaleOut(
                    targetScale = 0.94f,
                    animationSpec = tween(160, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(150))
            ) {
                SynqGlassToastPill(
                    data = activeToast,
                    onDismiss = { SynqToastManager.dismiss(activeToast.revision) }
                )
            }
        }
    }
}

@Composable
private fun SynqGlassToastPill(
    data: SynqToastData,
    onDismiss: () -> Unit
) {
    val dark = isAppDark()
    val glassFill = if (dark) Color(0xFF1A1A22).copy(alpha = 0.88f) else Color.White.copy(alpha = 0.92f)
    val borderBrush = Brush.linearGradient(
        if (dark) {
            listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.07f))
        } else {
            listOf(Color.White, Color(0xFF1C1420).copy(alpha = 0.12f))
        }
    )
    val textColor = if (dark) Color(0xFFF7F3F8) else Color(0xFF1C1420)

    val targetAccent = when (data.type) {
        SynqToastType.INFO -> if (dark) PinkPrimary else PinkDeep
        SynqToastType.SUCCESS -> SemanticSuccess
        SynqToastType.WARNING -> SemanticWarning
        SynqToastType.ERROR -> SemanticLive
    }
    val accentColor by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(180),
        label = "toastAccent"
    )
    val iconVector = when (data.type) {
        SynqToastType.INFO -> Icons.Default.MusicNote
        SynqToastType.SUCCESS -> Icons.Default.Check
        SynqToastType.WARNING -> Icons.Default.PriorityHigh
        SynqToastType.ERROR -> Icons.Default.Close
    }

    Box(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .shadow(
                elevation = 14.dp,
                shape = CircleShape,
                ambientColor = if (dark) Color.Black.copy(alpha = 0.55f) else Color(0xFF1C1420).copy(alpha = 0.18f),
                spotColor = if (dark) Color.Black.copy(alpha = 0.55f) else Color(0xFF1C1420).copy(alpha = 0.18f)
            )
            .pressable(
                pressedScale = MotionTokens.ChipPressScale,
                onClick = onDismiss
            )
            .pointerInput(data.revision) {
                var totalDragY = 0f
                detectVerticalDragGestures(
                    onDragEnd = { totalDragY = 0f },
                    onDragCancel = { totalDragY = 0f }
                ) { _, dragAmount ->
                    totalDragY += dragAmount
                    if (totalDragY > 24f) {
                        onDismiss()
                    }
                }
            }
            .clip(CircleShape)
            .background(glassFill)
            .border(1.dp, borderBrush, CircleShape)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(15.dp)
                )
            }

            AnimatedContent(
                targetState = data.message,
                transitionSpec = {
                    (fadeIn(tween(150)) + slideInVertically { it / 4 }) togetherWith
                        (fadeOut(tween(110)) + slideOutVertically { -it / 4 })
                },
                label = "toastText"
            ) { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
