package com.synq.features.music.jam

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.synq.RootViewModel
import com.synq.features.music.components.AnimatedEqualizerBars
import com.synq.features.music.domain.model.Song
import com.synq.ui.isAppDark

private val PinkBrush = Brush.linearGradient(
    listOf(Color(0xFFFF2D7A), Color(0xFFFF6584))
)
private val Accent = Color(0xFFFF2D7A)
private val Danger = Color(0xFFEF4444)

/**
 * Standalone, full-screen Jam Session view.
 * Navigated to when a Jam session is started or joined.
 */
@Composable
fun JamScreen(
    onBackClick: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
    rootViewModel: RootViewModel = hiltViewModel()
) {
    val jamState by rootViewModel.jamState.collectAsState()
    val playbackState by rootViewModel.playbackState.collectAsState()
    val isDark = isAppDark()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val bgColor = if (isDark) Color(0xFF0F0C13) else Color(0xFFF8FAFC)
    val cardBg = if (isDark) Color(0xFF181320) else Color(0xFFFFFFFF)
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF64748B)

    var showIntroDialog by remember { mutableStateOf(false) }
    val hasSeenBatteryTip by rootViewModel.jamPreferences.hasSeenBatteryTip.collectAsState()
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(jamState.isInJam, hasSeenBatteryTip) {
        if (jamState.isInJam && !hasSeenBatteryTip && !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
            showBatteryOptimizationDialog = true
        }
    }

    if (showBatteryOptimizationDialog) {
        AlertDialog(
            onDismissRequest = {
                rootViewModel.jamPreferences.setSeenBatteryTip(true)
                showBatteryOptimizationDialog = false
            },
            title = {
                Text(
                    text = "Smooth Background Jam",
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            },
            text = {
                Text(
                    text = "To keep Jam sessions smoothly in sync when your phone is locked or another app is open, Synq works best with unrestricted battery usage.",
                    color = textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        rootViewModel.jamPreferences.setSeenBatteryTip(true)
                        showBatteryOptimizationDialog = false
                        BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                    }
                ) {
                    Text("Optimize", color = Accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        rootViewModel.jamPreferences.setSeenBatteryTip(true)
                        showBatteryOptimizationDialog = false
                    }
                ) {
                    Text("Dismiss", color = textSecondary)
                }
            },
            containerColor = cardBg
        )
    }

    if (showIntroDialog) {
        JamIntroDialog(
            onDismiss = { showIntroDialog = false },
            onStartJam = { rootViewModel.startJam() },
            onJoinJam = { code -> rootViewModel.joinJam(code) }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── Top Header Bar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textPrimary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Jam Session",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    Text(
                        text = if (jamState.isInJam) {
                            "Code: ${jamState.sessionCode ?: "----"} • ${jamState.participants.size} listening"
                        } else {
                            "No active session"
                        },
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                }

                if (jamState.isInJam) {
                    IconButton(
                        onClick = {
                            jamState.sessionCode?.let {
                                clipboardManager.setText(AnnotatedString(it))
                                Toast.makeText(context, "Code copied: $it", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Code",
                            tint = Accent
                        )
                    }
                }
            }

            if (!jamState.isInJam) {
                // Not in Jam session view
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .shadow(20.dp, CircleShape, ambientColor = Accent, spotColor = Accent)
                            .background(PinkBrush, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "No Active Jam",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Start a Jam session to listen with friends or enter a 4-letter invite code.",
                        fontSize = 14.sp,
                        color = textSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = { showIntroDialog = true },
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(50.dp)
                    ) {
                        Text("Start or Join Jam", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            } else {
                // ── Active Jam Content ──
                val canReorder = jamState.isHost || jamState.allowAttendeeReorder

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    // Invite Code Banner
                    val boxShape = RoundedCornerShape(20.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(boxShape)
                            .background(cardBg)
                            .border(1.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f), boxShape)
                            .clickable {
                                jamState.sessionCode?.let {
                                    clipboardManager.setText(AnnotatedString(it))
                                    Toast.makeText(context, "Code copied: $it", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 16.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "INVITE CODE • TAP TO COPY",
                            fontSize = 11.sp,
                            color = textSecondary,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = jamState.sessionCode ?: "----",
                            style = TextStyle(
                                brush = PinkBrush,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 8.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Participants Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Listening Together (${jamState.participants.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                        Text(
                            text = if (jamState.isHost) "You are Host" else "Guest",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Accent
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Participants Horizontal Scroll
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        jamState.participants.forEach { user ->
                            RealJamUserBubble(user = user, isDark = isDark)
                        }
                    }

                    // Host Settings: Reorder Queue Toggle
                    if (jamState.isHost) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardBg)
                                .border(1.dp, if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Allow guests to reorder queue",
                                fontSize = 14.sp,
                                color = textPrimary
                            )
                            Switch(
                                checked = jamState.allowAttendeeReorder,
                                onCheckedChange = { rootViewModel.setAttendeeReorderPermission(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Accent
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Shared Jam Queue
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Shared Queue (${jamState.queue.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textPrimary
                            )
                            if (!canReorder) {
                                Text(
                                    text = "Host controls order",
                                    fontSize = 11.sp,
                                    color = textSecondary.copy(alpha = 0.6f)
                                )
                            }
                        }

                        TextButton(
                            onClick = onNavigateToSearch,
                            colors = ButtonDefaults.textButtonColors(contentColor = Accent),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Add Song",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (jamState.queue.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),          // <-- was .weight(1f)
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Queue is empty. Songs you add will sync here!",
                                color = textSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedButton(
                                onClick = onNavigateToSearch,
                                shape = RoundedCornerShape(100.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Accent),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Add Songs from Search",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Column(                                       // <-- was LazyColumn
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            jamState.queue.forEachIndexed { index, s ->   // <-- was itemsIndexed
                                JamQueueRow(
                                    song = s,
                                    active = playbackState.currentSong?.id == s.id,
                                    isPlaying = playbackState.currentSong?.id == s.id && playbackState.isPlaying,
                                    isDark = isDark,
                                    canMoveUp = canReorder && index > 0,
                                    canMoveDown = canReorder && index < jamState.queue.size - 1,
                                    showReorderControls = canReorder,
                                    onClick = {
                                        if (jamState.isHost) {
                                            rootViewModel.playQueueIndex(index)
                                        } else {
                                            Toast.makeText(context, "Only the host can select playback track", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onMoveUp = { rootViewModel.reorderJamQueue(index, index - 1) },
                                    onMoveDown = { rootViewModel.reorderJamQueue(index, index + 1) },
                                    onRemove = { rootViewModel.removeSongFromJam(index) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Leave / End Jam Button
                    val pill = RoundedCornerShape(100.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(pill)
                            .border(1.dp, Danger.copy(alpha = 0.6f), pill)   // outline only, no tinted fill
                            .clickable {
                                rootViewModel.leaveJam()
                                onBackClick()
                            }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            tint = Danger,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (jamState.isHost) "End Jam Session" else "Leave Jam Session",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Danger
                        )
                    }

                    Spacer(modifier = Modifier.height(if (playbackState.currentSong != null) 92.dp else 24.dp))
                }
            }
        }
    }
}

@Composable
private fun RealJamUserBubble(user: JamParticipantUi, isDark: Boolean) {
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    Column(
        modifier = Modifier.widthIn(min = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(if (user.isHost) Accent else Accent.copy(alpha = 0.7f), CircleShape)
                .padding(2.dp)
        ) {
            if (user.avatarUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1E1824) else Color(0xFFE2E8F0)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.username.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        fontSize = 16.sp
                    )
                }
            } else {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = user.username,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
        }

        Text(
            text = user.username,
            fontSize = 11.sp,
            color = textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (user.isHost) {
            Text(
                text = "HOST",
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Accent,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun JamQueueRow(
    song: Song,
    active: Boolean,
    isDark: Boolean,
    isPlaying: Boolean = true,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    showReorderControls: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF64748B)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) Accent.copy(alpha = 0.16f) else if (isDark) Color(0xFF181320) else Color.White)
            .border(1.dp, if (active) Accent.copy(alpha = 0.38f) else if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f), shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (active) Accent else textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.primaryArtistName,
                fontSize = 12.sp,
                color = textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (active) {
            AnimatedEqualizerBars(
                color = Accent,
                isPlaying = isPlaying,
                barCount = 3,
                modifier = Modifier.padding(end = 4.dp)
            )
        } else if (showReorderControls) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canMoveUp) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", tint = textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                if (canMoveDown) {
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", tint = textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = textSecondary.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
