package com.synq.features.music.jam

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.synq.ui.isAppDark

private val PinkBrush = Brush.linearGradient(
    listOf(Color(0xFFFF2D7A), Color(0xFFFF6584))
)
private val Accent = Color(0xFFFF2D7A)
private val Danger = Color(0xFFEF4444)

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
    val isDark = isAppDark()
    var enterCode by remember { mutableStateOf("") }
    var isStarting by remember { mutableStateOf(false) }
    var isJoining by remember { mutableStateOf(false) }
    var inlineError by remember { mutableStateOf<String?>(null) }

    val isLoading = isStarting || isJoining

    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF64748B)

    val dialogShape = RoundedCornerShape(28.dp)
    val dialogBg = if (isDark) Color(0xFF1A1420) else Color(0xFFFFFFFF)
    val dialogBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)

    Dialog(
        onDismissRequest = {
            if (!isLoading) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(dialogShape)
                .background(dialogBg)
                .border(1.dp, dialogBorder, dialogShape)
                .padding(24.dp)
        ) {
            // Dismiss close button
            IconButton(
                onClick = {
                    if (!isLoading) onDismiss()
                },
                enabled = !isLoading,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
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
                Spacer(modifier = Modifier.height(8.dp))

                // Glowing Group Icon
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .shadow(16.dp, CircleShape, ambientColor = Accent, spotColor = Accent)
                        .background(PinkBrush, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = "Jam Group",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Listen Together",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Invite friends to join a Jam. Everyone can add songs and enjoy synchronized playback.",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Start Jam Button (Pink Gradient Pill)
                val pill = RoundedCornerShape(100.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, pill, ambientColor = Accent, spotColor = Accent)
                        .clip(pill)
                        .alpha(if (isLoading) 0.6f else 1f)
                        .background(PinkBrush)
                        .clickable(enabled = !isLoading) {
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
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "OR JOIN WITH CODE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textSecondary,
                    letterSpacing = 1.sp
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
                        placeholder = {
                            Text(
                                "ABCD",
                                color = textSecondary.copy(alpha = 0.5f),
                                fontSize = 14.sp,
                                letterSpacing = 2.sp
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedContainerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f),
                            unfocusedContainerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            if (enterCode.length == 4) {
                                inlineError = null
                                isJoining = true
                                onJoinJam(enterCode) { res ->
                                    isJoining = false
                                    res.onSuccess {
                                        onDismiss()
                                        onSuccessNav()
                                    }
                                    res.onFailure { err ->
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
                        enabled = enterCode.length == 4 && !isLoading,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        modifier = Modifier
                            .height(50.dp)
                            .defaultMinSize(minWidth = 72.dp)
                    ) {
                        if (isJoining) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Join", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                if (inlineError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = inlineError ?: "",
                        color = Danger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
