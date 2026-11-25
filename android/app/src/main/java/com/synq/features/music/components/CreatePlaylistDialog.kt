package com.synq.features.music.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.synq.ui.isAppDark
import com.synq.ui.motion.MotionTokens
import com.synq.ui.motion.performConfirm
import com.synq.ui.motion.pressable
import com.synq.ui.theme.PinkDeep
import com.synq.ui.theme.PinkGradient
import com.synq.ui.theme.PinkPrimary
import com.synq.ui.theme.Spacing

@Composable
fun CreatePlaylistDialog(
    defaultName: String = "My Playlist",
    onDismiss: () -> Unit,
    onCreate: (name: String, desc: String) -> Unit
) {
    val isDark = isAppDark()
    val haptic = LocalHapticFeedback.current
    val pinkAccent = if (isDark) PinkPrimary else PinkDeep

    var nameValue by remember(defaultName) {
        mutableStateOf(
            TextFieldValue(
                text = defaultName,
                selection = TextRange(defaultName.length)
            )
        )
    }
    var description by remember { mutableStateOf("") }
    var isNameFocused by remember { mutableStateOf(false) }
    var isDescFocused by remember { mutableStateOf(false) }

    val entranceScale = remember { Animatable(0.92f) }
    val entranceAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entranceScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    LaunchedEffect(Unit) {
        entranceAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        )
    }

    val isValid = nameValue.text.isNotBlank()
    val handleCreate = {
        if (isValid) {
            haptic.performConfirm()
            onCreate(nameValue.text.trim(), description.trim())
        }
    }

    val dialogShape = MaterialTheme.shapes.extraLarge
    val dialogBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val fieldBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val cardBorder = Brush.linearGradient(
        listOf(PinkPrimary.copy(alpha = 0.30f), MaterialTheme.colorScheme.outlineVariant)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .graphicsLayer {
                    scaleX = entranceScale.value
                    scaleY = entranceScale.value
                    alpha = entranceAlpha.value
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(dialogShape)
                    .background(dialogBg)
                    .border(1.dp, cardBorder, dialogShape),
                color = dialogBg,
                shape = dialogShape
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xl, vertical = Spacing.xl),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "New Playlist",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )

                    Spacer(Modifier.height(Spacing.l))

                    // ─── Playlist Name Field ───
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val nameLabelColor by animateColorAsState(
                            targetValue = if (isNameFocused) pinkAccent else textSecondary.copy(alpha = 0.75f),
                            label = "nameLabelColor"
                        )
                        Text(
                            text = "PLAYLIST NAME",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                            fontWeight = FontWeight.Bold,
                            color = nameLabelColor
                        )
                        Spacer(Modifier.height(6.dp))

                        val nameBorderColor by animateColorAsState(
                            targetValue = if (isNameFocused) PinkPrimary else MaterialTheme.colorScheme.outlineVariant,
                            label = "nameBorder"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(fieldBg)
                                .border(1.5.dp, nameBorderColor, MaterialTheme.shapes.medium)
                                .padding(horizontal = Spacing.m),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = nameValue,
                                onValueChange = { nameValue = it },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = textPrimary
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(PinkPrimary),
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { handleCreate() }),
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { isNameFocused = it.isFocused },
                                decorationBox = { innerTextField ->
                                    if (nameValue.text.isEmpty()) {
                                        Text(
                                            text = "Playlist name",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = textSecondary.copy(alpha = 0.5f)
                                        )
                                    }
                                    innerTextField()
                                }
                            )

                            AnimatedVisibility(
                                visible = nameValue.text.isNotEmpty(),
                                enter = scaleIn(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                ) + fadeIn(),
                                exit = scaleOut() + fadeOut()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .pressable(
                                            pressedScale = MotionTokens.ChipPressScale,
                                            onClick = {
                                                nameValue = TextFieldValue(text = "", selection = TextRange(0))
                                            }
                                        )
                                        .background(textSecondary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(Spacing.m))

                    // ─── Optional Description Field ───
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val descLabelColor by animateColorAsState(
                            targetValue = if (isDescFocused) pinkAccent else textSecondary.copy(alpha = 0.75f),
                            label = "descLabelColor"
                        )
                        Text(
                            text = "DESCRIPTION (OPTIONAL)",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                            fontWeight = FontWeight.Bold,
                            color = descLabelColor
                        )
                        Spacer(Modifier.height(6.dp))

                        val descBorderColor by animateColorAsState(
                            targetValue = if (isDescFocused) PinkPrimary else MaterialTheme.colorScheme.outlineVariant,
                            label = "descBorder"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(fieldBg)
                                .border(1.5.dp, descBorderColor, MaterialTheme.shapes.medium)
                                .padding(horizontal = Spacing.m, vertical = Spacing.s)
                        ) {
                            BasicTextField(
                                value = description,
                                onValueChange = { description = it },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = textPrimary),
                                maxLines = 2,
                                cursorBrush = SolidColor(PinkPrimary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { handleCreate() }),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onFocusChanged { isDescFocused = it.isFocused },
                                decorationBox = { innerTextField ->
                                    if (description.isEmpty()) {
                                        Text(
                                            text = "Add an optional description...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = textSecondary.copy(alpha = 0.5f)
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(Spacing.xl))

                    // ─── Action Buttons ───
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .then(
                                if (isValid) {
                                    Modifier.pressable(
                                        pressedScale = MotionTokens.ButtonPressScale,
                                        onClick = { handleCreate() }
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clip(CircleShape)
                            .then(
                                if (isValid) {
                                    Modifier.background(PinkGradient)
                                } else {
                                    Modifier.background(textSecondary.copy(alpha = 0.14f))
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val createTextColor by animateColorAsState(
                            targetValue = if (isValid) Color.White else textSecondary.copy(alpha = 0.45f),
                            label = "createTextColor"
                        )
                        Text(
                            text = "Create",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = createTextColor
                        )
                    }

                    Spacer(Modifier.height(Spacing.xs))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .clip(CircleShape)
                            .pressable(
                                pressedScale = MotionTokens.ButtonPressScale,
                                onClick = onDismiss
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = textSecondary
                        )
                    }
                }
            }
        }
    }
}
