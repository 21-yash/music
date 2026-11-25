package com.synq.features.music.jam

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.synq.ui.motion.MotionTokens
import com.synq.ui.motion.performConfirm
import com.synq.ui.motion.pressable
import com.synq.ui.theme.PinkGradient
import com.synq.ui.theme.PinkGradientHero
import com.synq.ui.theme.PinkPrimary
import com.synq.ui.theme.Spacing

/**
 * Modern glassmorphic pop-up dialog to Start or Join a Jam session.
 * Features 4-character uppercase paste-friendly code entry, loading states,
 * double-submit protection, and inline error display from server acks.
 */
@Composable
fun JamIntroDialog(
    onDismiss: () -> Unit,
    onStartJam: (onComplete: (Result<Unit>) -> Unit) -> Unit,
    onJoinJam: (code: String, onComplete: (Result<Unit>) -> Unit) -> Unit,
    onSuccessNav: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    var enterCode by remember { mutableStateOf("") }
    var isStarting by remember { mutableStateOf(false) }
    var isJoining by remember { mutableStateOf(false) }
    var inlineError by remember { mutableStateOf<String?>(null) }

    val isLoading = isStarting || isJoining

    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    val dialogShape = MaterialTheme.shapes.extraLarge
    val dialogBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val dialogBorder = MaterialTheme.colorScheme.outlineVariant

    Dialog(
        onDismissRequest = {
            if (!isLoading) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .clip(dialogShape)
                .background(dialogBg)
                .border(1.dp, dialogBorder, dialogShape)
                .animateContentSize()
                .padding(Spacing.xl)
        ) {
            // Dismiss close button
            IconButton(
                onClick = {
                    if (!isLoading) onDismiss()
                },
                enabled = !isLoading,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(40.dp)
                    .then(
                        if (!isLoading) {
                            Modifier.pressable(pressedScale = MotionTokens.ChipPressScale)
                        } else {
                            Modifier
                        }
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = if (isLoading) textSecondary.copy(alpha = 0.3f) else textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(Spacing.xs))

                // Group Icon
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(PinkGradientHero),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = "Jam Group",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.m))

                Text(
                    text = "Listen Together",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )

                Spacer(modifier = Modifier.height(Spacing.xs))

                Text(
                    text = "Invite friends to join a Jam. Everyone can add songs and enjoy synchronized playback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.s)
                )

                Spacer(modifier = Modifier.height(Spacing.xl))

                // Start Jam Button (Pink Gradient Pill)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .then(
                            if (!isLoading) {
                                Modifier.pressable(
                                    pressedScale = MotionTokens.ButtonPressScale,
                                    onClick = {
                                        haptic.performConfirm()
                                        inlineError = null
                                        isStarting = true
                                        onStartJam { res ->
                                            isStarting = false
                                            res.onSuccess {
                                                onDismiss()
                                                onSuccessNav()
                                            }
                                            res.onFailure { err ->
                                                inlineError = err.message ?: "Failed to start Jam"
                                            }
                                        }
                                    }
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clip(CircleShape)
                        .alpha(if (isLoading) 0.6f else 1f)
                        .background(PinkGradient)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isStarting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Start a Jam",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.l))

                Text(
                    text = "OR JOIN WITH CODE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = textSecondary,
                    letterSpacing = 0.8.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = enterCode,
                        onValueChange = { input ->
                            inlineError = null
                            enterCode = input.filter { it.isLetterOrDigit() }.take(4).uppercase()
                        },
                        enabled = !isLoading,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            fontFeatureSettings = "tnum",
                            letterSpacing = 2.sp
                        ),
                        placeholder = {
                            Text(
                                "ABCD",
                                color = textSecondary.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFeatureSettings = "tnum",
                                    letterSpacing = 2.sp
                                )
                            )
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PinkPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    val canJoin = enterCode.length == 4 && !isLoading
                    Button(
                        onClick = {
                            if (enterCode.length == 4) {
                                haptic.performConfirm()
                                inlineError = null
                                isJoining = true
                                onJoinJam(enterCode) { res ->
                                    isJoining = false
                                    res.onSuccess {
                                        onDismiss()
                                        onSuccessNav()
                                    }
                                    res.onFailure { err ->
                                        haptic.performHapticFeedback(HapticFeedbackType.Reject)
                                        inlineError = when (err.message) {
                                            "NOT_FOUND" -> "Jam session not found. Check the code."
                                            "INVALID_PROTOCOL" -> "Please update Synq to join."
                                            "REMOVED" -> "You were removed from this Jam."
                                            else -> err.message ?: "Failed to join Jam"
                                        }
                                    }
                                }
                            }
                        },
                        enabled = canJoin,
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                        modifier = Modifier
                            .height(50.dp)
                            .defaultMinSize(minWidth = 72.dp)
                            .then(
                                if (canJoin) {
                                    Modifier.pressable(pressedScale = MotionTokens.ButtonPressScale)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        if (isJoining) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Join",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                if (inlineError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = inlineError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = Spacing.xs)
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xs))
            }
        }
    }
}
