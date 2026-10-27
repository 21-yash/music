package com.synq.features.music.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.synq.ui.SynqPink
import com.synq.ui.SynqPinkDark
import com.synq.ui.SynqPinkGradient
import com.synq.ui.isAppDark

@Composable
fun CreatePlaylistDialog(
    defaultName: String = "My Playlist",
    onDismiss: () -> Unit,
    onCreate: (name: String, desc: String) -> Unit
) {
    val isDark = isAppDark()

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

    val isValid = nameValue.text.isNotBlank()
    val handleCreate = {
        if (isValid) onCreate(nameValue.text.trim(), description.trim())
    }

    val dialogShape = RoundedCornerShape(28.dp)
    val cardBg = if (isDark) {
        Brush.verticalGradient(
            listOf(Color(0xFF22172A), Color(0xFF15101B))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color(0xFFFFFFFF), Color(0xFFFBF8FA))
        )
    }

    val cardBorder = if (isDark) {
        Brush.linearGradient(
            listOf(SynqPink.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f))
        )
    } else {
        Brush.linearGradient(
            listOf(SynqPink.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.06f))
        )
    }

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
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 24.dp, shape = dialogShape, ambientColor = SynqPinkDark.copy(alpha = 0.2f), spotColor = SynqPink.copy(alpha = 0.3f))
                    .clip(dialogShape)
                    .background(cardBg)
                    .border(1.dp, cardBorder, dialogShape),
                color = Color.Transparent,
                shape = dialogShape
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "New Playlist",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(Modifier.height(20.dp))

                    // ─── Playlist Name Field ───
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "PLAYLIST NAME",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isNameFocused) SynqPink else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.height(6.dp))

                        val nameBorderColor by animateColorAsState(
                            targetValue = if (isNameFocused) SynqPink else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                            label = "nameBorder"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                                .border(1.5.dp, nameBorderColor, RoundedCornerShape(16.dp))
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = nameValue,
                                onValueChange = { nameValue = it },
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(SynqPink),
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
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                    innerTextField()
                                }
                            )

                            if (nameValue.text.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                                        .clickable {
                                            nameValue = TextFieldValue(text = "", selection = TextRange(0))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ─── Optional Description Field ───
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "DESCRIPTION (OPTIONAL)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDescFocused) SynqPink else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(Modifier.height(6.dp))

                        val descBorderColor by animateColorAsState(
                            targetValue = if (isDescFocused) SynqPink else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                            label = "descBorder"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                                .border(1.5.dp, descBorderColor, RoundedCornerShape(16.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            BasicTextField(
                                value = description,
                                onValueChange = { description = it },
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 13.5.sp
                                ),
                                maxLines = 2,
                                cursorBrush = SolidColor(SynqPink),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (isValid) onCreate(nameValue.text.trim(), description.trim())
                                }),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onFocusChanged { isDescFocused = it.isFocused },
                                decorationBox = { innerTextField ->
                                    if (description.isEmpty()) {
                                        Text(
                                            text = "Add an optional description...",
                                            fontSize = 13.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // ─── Action Buttons ───
                    Button(
                        onClick = { handleCreate() },
                        enabled = isValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .shadow(
                                elevation = if (isValid) 8.dp else 0.dp,
                                shape = RoundedCornerShape(24.dp),
                                spotColor = SynqPink.copy(alpha = 0.5f)
                            ),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)
                        ),
                        contentPadding = PaddingValues()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isValid) SynqPinkGradient else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Create",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isValid) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = RoundedCornerShape(21.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
