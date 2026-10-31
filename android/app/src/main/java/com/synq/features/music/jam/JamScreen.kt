package com.synq.features.music.jam

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.synq.ui.isAppDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val PinkBrush = Brush.linearGradient(
    listOf(Color(0xFFFF2D7A), Color(0xFFFF6584))
)
private val Accent = Color(0xFFFF2D7A)
private val Danger = Color(0xFFEF4444)
private val OnlineGreen = Color(0xFF22C55E)
private val WarningAmber = Color(0xFFF59E0B)

/**
 * Spotify-Jam-like screen backed exclusively by authoritative JamStateStore.uiState,
 * JamClient.clientState, and JamSessionManager.localPlaybackState.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JamScreen(
    onBackClick: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    modifier: Modifier = Modifier,
    rootViewModel: RootViewModel = hiltViewModel()
) {
    val uiState by rootViewModel.jamUiState.collectAsState()
    val jamState by rootViewModel.jamState.collectAsState()
    val room by rootViewModel.jamRoom.collectAsState()
    val clientState by rootViewModel.jamClientState.collectAsState()
    val localPlaybackState by rootViewModel.jamLocalPlaybackState.collectAsState()
    val playerPlaybackState by rootViewModel.playbackState.collectAsState()
    val clockSync = rootViewModel.jamClockSync

    val isDark = isAppDark()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val bgColor = if (isDark) Color(0xFF0F0C13) else Color(0xFFF8FAFC)
    val cardBg = if (isDark) Color(0xFF181320) else Color(0xFFFFFFFF)
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF64748B)

    var showIntroDialog by remember { mutableStateOf(false) }
    var showConfirmEndDialog by remember { mutableStateOf(false) }
    var showPeopleSheet by remember { mutableStateOf(false) }
    var isHistoryExpanded by remember { mutableStateOf(false) }

    val hasSeenBatteryTip by rootViewModel.jamPreferences.hasSeenBatteryTip.collectAsState()
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { _ -> }
    )

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission && !rootViewModel.jamPreferences.hasRequestedNotificationPermission()) {
                rootViewModel.jamPreferences.setRequestedNotificationPermission(true)
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(uiState.isInJam, hasSeenBatteryTip) {
        if (uiState.isInJam && !hasSeenBatteryTip && !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
            showBatteryOptimizationDialog = true
        }
    }

    // Auto-exit if jam closed
    LaunchedEffect(Unit) {
        rootViewModel.jamClosedEvents.collect {
            onBackClick()
        }
    }

    // End Jam Confirmation Dialog
    if (showConfirmEndDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmEndDialog = false },
            title = {
                Text(
                    text = "End Jam Session?",
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            },
            text = {
                Text(
                    text = "This will end the session and stop playback for everyone.",
                    color = textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmEndDialog = false
                        rootViewModel.endJam()
                    }
                ) {
                    Text("End Jam", color = Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmEndDialog = false }) {
                    Text("Cancel", color = textSecondary)
                }
            },
            containerColor = cardBg
        )
    }

    // Battery Optimization Guidance Dialog
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
            onStartJam = { onComplete ->
                rootViewModel.startJam(onComplete = onComplete)
            },
            onJoinJam = { code, onComplete ->
                rootViewModel.joinJam(code, onComplete = onComplete)
            },
            onSuccessNav = {
                showIntroDialog = false
            }
        )
    }

    if (showPeopleSheet && room != null) {
        PeopleSheet(
            room = room!!,
            myUserId = uiState.myUserId,
            isHost = uiState.isHost,
            onDismiss = { showPeopleSheet = false },
            onUpdateMemberPerms = { targetUserId, perms ->
                rootViewModel.setMemberPerms(targetUserId, perms)
            },
            onUpdateDefaults = { defaults ->
                rootViewModel.setDefaults(defaults)
            },
            onMakeHost = { targetUserId ->
                rootViewModel.transferHost(targetUserId)
            },
            onKickMember = { targetUserId ->
                rootViewModel.kickMember(targetUserId)
            }
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
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textPrimary
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Jam",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    Text(
                        text = if (uiState.isInJam) {
                            "${uiState.members.size} listening together"
                        } else {
                            "No active session"
                        },
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                }

                if (uiState.isInJam && uiState.sessionCode != null) {
                    val code = uiState.sessionCode ?: ""

                    // Code Chip (Tap to copy)
                    Surface(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(code))
                            Toast.makeText(context, "Code copied: $code", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(100.dp),
                        color = Accent.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Accent.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .height(36.dp)
                            .defaultMinSize(minWidth = 48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = code,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Accent,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy code $code",
                                tint = Accent,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Share Button (Android share sheet)
                    IconButton(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Join my Synq Jam — code $code")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Join my Synq Jam — code $code"))
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share invite code",
                            tint = Accent
                        )
                    }
                }
            }

            if (!uiState.isInJam) {
                // Not in active Jam empty state
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
                            contentDescription = "Jam",
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    // Status Pill
                    val isStepOut = localPlaybackState is LocalPlaybackState.LocalStepOut
                    val isCatchingUp = localPlaybackState is LocalPlaybackState.CatchingUp
                    val isReconnecting = clientState is JamClientState.Reconnecting

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = {
                                if (isStepOut) {
                                    rootViewModel.resumeFromLocalStepOut()
                                }
                            },
                            shape = RoundedCornerShape(100.dp),
                            color = when {
                                isStepOut -> WarningAmber.copy(alpha = 0.16f)
                                isReconnecting -> WarningAmber.copy(alpha = 0.12f)
                                isCatchingUp -> Accent.copy(alpha = 0.12f)
                                else -> OnlineGreen.copy(alpha = 0.12f)
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when {
                                    isStepOut -> WarningAmber.copy(alpha = 0.5f)
                                    isReconnecting -> WarningAmber.copy(alpha = 0.4f)
                                    isCatchingUp -> Accent.copy(alpha = 0.4f)
                                    else -> OnlineGreen.copy(alpha = 0.4f)
                                }
                            ),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                when {
                                    isStepOut -> {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Resume for me",
                                            tint = WarningAmber,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Paused for you only • Tap to resume",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WarningAmber
                                        )
                                    }
                                    isReconnecting -> {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = "Reconnecting",
                                            tint = WarningAmber,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Reconnecting…",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WarningAmber
                                        )
                                    }
                                    isCatchingUp -> {
                                        Icon(
                                            imageVector = Icons.Default.FastForward,
                                            contentDescription = "Catching up",
                                            tint = Accent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Catching up…",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Accent
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Synced",
                                            tint = OnlineGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Synced ✓",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OnlineGreen
                                        )
                                    }
                                }
                            }
                        }

                        // Host / Guest indicator badge
                        Text(
                            text = if (uiState.isHost) "HOST" else "GUEST",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Accent,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Participant Avatars Row (Tap to open People sheet D2)
                    Surface(
                        onClick = { showPeopleSheet = true },
                        shape = RoundedCornerShape(18.dp),
                        color = cardBg,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "People in Jam (${uiState.members.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textPrimary
                                )
                                Text(
                                    text = if (uiState.isHost) "Manage >" else "View all >",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Accent
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                uiState.members.forEach { member ->
                                    JamMemberAvatarPill(
                                        member = member,
                                        isHost = member.userId == uiState.hostUserId,
                                        isDark = isDark
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Now Playing Card ──
                    val nowPlaying = uiState.nowPlaying
                    val nowPlayingItem = nowPlaying.item

                    if (nowPlayingItem != null) {
                        val hostName = uiState.hostName.ifBlank { "Host" }
                        val addedByMember = uiState.members.find { it.userId == nowPlayingItem.addedByUserId }
                        val addedByName = addedByMember?.username ?: if (nowPlayingItem.recommended) "Auto-recommended" else "Host"

                        // Expected position computed live from clockSync
                        var liveExpectedPos by remember { mutableLongStateOf(0L) }
                        val isPlaying = nowPlaying.status == JamPlaybackStatus.PLAYING

                        LaunchedEffect(nowPlaying, localPlaybackState) {
                            while (isActive) {
                                liveExpectedPos = clockSync.expectedPosition(nowPlaying, clockSync.serverNow())
                                delay(250)
                            }
                        }

                        val jamProgress = computeJamProgress(
                            metadataDurationSec = nowPlayingItem.song.durationSeconds,
                            playerDurationMs = playerPlaybackState.durationMs,
                            positionMs = liveExpectedPos
                        )
                        val durationMs = jamProgress.durationMs
                        val progress = jamProgress.fraction

                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = nowPlayingItem.song.imageUrl,
                                        contentDescription = "Artwork for ${nowPlayingItem.song.title}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = nowPlayingItem.song.title,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = nowPlayingItem.song.primaryArtistName,
                                            fontSize = 13.5.sp,
                                            color = textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Added by $addedByName",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Accent
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Progress Slider
                                Slider(
                                    value = progress,
                                    onValueChange = { targetRatio ->
                                        if (!uiState.canControlPlayback) {
                                            Toast.makeText(context, "Ask $hostName for playback control", Toast.LENGTH_SHORT).show()
                                        } else if (jamProgress.canSeek) {
                                            val targetMs = (targetRatio * durationMs).toLong()
                                            rootViewModel.jamSeekTo(targetMs)
                                        }
                                    },
                                    enabled = uiState.canControlPlayback && jamProgress.canSeek,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Accent,
                                        activeTrackColor = Accent,
                                        inactiveTrackColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f),
                                        disabledThumbColor = Accent.copy(alpha = 0.4f),
                                        disabledActiveTrackColor = Accent.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatTime(liveExpectedPos),
                                        fontSize = 11.sp,
                                        color = textSecondary
                                    )
                                    Text(
                                        text = if (durationMs > 0L) formatTime(durationMs) else "--:--",
                                        fontSize = 11.sp,
                                        color = textSecondary
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Transport Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Previous
                                    IconButton(
                                        onClick = {
                                            if (!uiState.canControlPlayback) {
                                                Toast.makeText(context, "Ask $hostName for playback control", Toast.LENGTH_SHORT).show()
                                            } else {
                                                rootViewModel.jamPreviousTrack()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .alpha(if (uiState.canControlPlayback) 1f else 0.38f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipPrevious,
                                            contentDescription = "Previous Track",
                                            tint = textPrimary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }

                                    // Play / Pause / Step-Out
                                    Box(
                                        modifier = Modifier
                                            .size(62.dp)
                                            .shadow(12.dp, CircleShape, ambientColor = Accent, spotColor = Accent)
                                            .background(PinkBrush, CircleShape)
                                            .clickable {
                                                if (uiState.canControlPlayback) {
                                                    rootViewModel.toggleJamPlayPause()
                                                } else {
                                                    // Guest without playback controls: toggle pause for me only
                                                    if (isStepOut) {
                                                        rootViewModel.resumeFromLocalStepOut()
                                                    } else {
                                                        rootViewModel.stepOutLocally()
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val displayPlaying = if (isStepOut) false else isPlaying
                                        Icon(
                                            imageVector = if (displayPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (displayPlaying) "Pause" else "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }

                                    // Next
                                    IconButton(
                                        onClick = {
                                            if (!uiState.canControlPlayback) {
                                                Toast.makeText(context, "Ask $hostName for playback control", Toast.LENGTH_SHORT).show()
                                            } else {
                                                rootViewModel.jamNextTrack()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .alpha(if (uiState.canControlPlayback) 1f else 0.38f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Next Track",
                                            tint = textPrimary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                val isUnplayable = uiState.isLocalPlaybackUnplayable || jamState.isLocalPlaybackUnplayable
                                if (isUnplayable) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Danger.copy(alpha = 0.12f))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Can't play on this device",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Danger
                                        )
                                        TextButton(
                                            onClick = { rootViewModel.retryJamPlayback() },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text(
                                                text = "Retry",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Danger
                                            )
                                        }
                                    }
                                }

                                // Guest Without Playback Help Hint
                                if (!uiState.canControlPlayback) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isStepOut) "Paused for your phone only • " else "Playback controlled by host • ",
                                            fontSize = 11.5.sp,
                                            color = textSecondary
                                        )
                                        TextButton(
                                            onClick = {
                                                if (isStepOut) rootViewModel.resumeFromLocalStepOut() else rootViewModel.stepOutLocally()
                                            },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text(
                                                text = if (isStepOut) "Resume for me" else "Pause for me only",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Accent
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Empty Now Playing Card
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No track currently playing",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Add songs to start listening together!",
                                    fontSize = 13.sp,
                                    color = textSecondary
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = onNavigateToSearch,
                                    shape = RoundedCornerShape(100.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add the first song", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ── Up Next Section ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Up Next (${uiState.userQueue.size})",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            if (!uiState.canManageQueue) {
                                Text(
                                    text = "Host controls order",
                                    fontSize = 11.sp,
                                    color = textSecondary.copy(alpha = 0.7f)
                                )
                            }
                        }

                        TextButton(
                            onClick = onNavigateToSearch,
                            colors = ButtonDefaults.textButtonColors(contentColor = Accent)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Add Song", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (uiState.userQueue.isEmpty()) {
                        Text(
                            text = "No upcoming songs added yet.",
                            fontSize = 13.sp,
                            color = textSecondary,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.userQueue.forEachIndexed { index, queueItem ->
                                val isOwnSong = queueItem.addedByUserId == uiState.myUserId
                                val canRemove = isOwnSong || uiState.canManageQueue
                                val canReorder = uiState.canManageQueue
                                val addedBy = uiState.members.find { it.userId == queueItem.addedByUserId }?.username ?: "Guest"

                                JamQueueItemRow(
                                    key = queueItem.qid,
                                    song = queueItem.song,
                                    addedByName = addedBy,
                                    isDark = isDark,
                                    canRemove = canRemove,
                                    canMoveUp = canReorder && index > 0,
                                    canMoveDown = canReorder && index < uiState.userQueue.size - 1,
                                    onClick = {
                                        if (uiState.canControlPlayback) {
                                            rootViewModel.playItemByQid(queueItem.qid)
                                        } else {
                                            Toast.makeText(context, "Ask ${uiState.hostName.ifBlank { "Host" }} for playback control", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onMoveUp = { rootViewModel.moveByQid(queueItem.qid, index - 1) },
                                    onMoveDown = { rootViewModel.moveByQid(queueItem.qid, index + 1) },
                                    onRemove = { rootViewModel.removeByQid(queueItem.qid) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ── Recommended Section Below Up Next ──
                    if (uiState.recommendedQueue.isNotEmpty()) {
                        Text(
                            text = "Recommended for this Jam",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.recommendedQueue.forEach { recItem ->
                                JamRecommendedRow(
                                    key = recItem.qid,
                                    song = recItem.song,
                                    isDark = isDark,
                                    canAdd = uiState.canAddSongs,
                                    canDismiss = canDismissRecommendation(uiState.isHost, uiState.myPerms),
                                    onAdd = {
                                        if (uiState.canAddSongs) {
                                            rootViewModel.promoteRecommended(recItem.qid)
                                        } else {
                                            Toast.makeText(context, "Ask ${uiState.hostName.ifBlank { "Host" }} to add songs", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onDismiss = {
                                        rootViewModel.removeByQid(recItem.qid)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // ── History Section (Collapsible) ──
                    if (uiState.history.isNotEmpty()) {
                        Surface(
                            onClick = { isHistoryExpanded = !isHistoryExpanded },
                            shape = RoundedCornerShape(16.dp),
                            color = cardBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Played (${uiState.history.size})",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                    Icon(
                                        imageVector = if (isHistoryExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isHistoryExpanded) "Collapse" else "Expand",
                                        tint = textSecondary
                                    )
                                }

                                AnimatedVisibility(
                                    visible = isHistoryExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(top = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        uiState.history.forEach { historyItem ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AsyncImage(
                                                    model = historyItem.song.imageUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = historyItem.song.title,
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = textPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = historyItem.song.primaryArtistName,
                                                        fontSize = 11.5.sp,
                                                        color = textSecondary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // ── Leave / End Jam Action Buttons ──
                    val pillShape = RoundedCornerShape(100.dp)

                    // Leave Button (Host passes on; Jam continues)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(pillShape)
                            .border(
                                1.dp,
                                if (uiState.isHost) textSecondary.copy(alpha = 0.4f) else Danger.copy(alpha = 0.6f),
                                pillShape
                            )
                            .clickable {
                                rootViewModel.leaveJam()
                            }
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Leave Jam",
                            tint = if (uiState.isHost) textPrimary else Danger,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Leave Jam",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.isHost) textPrimary else Danger
                        )
                    }

                    // Host-Only: End Jam for Everyone
                    if (uiState.isHost) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(pillShape)
                                .border(1.dp, Danger.copy(alpha = 0.6f), pillShape)
                                .clickable { showConfirmEndDialog = true }
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "End Jam for Everyone",
                                tint = Danger,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "End Jam for Everyone",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Danger
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(96.dp)) // Mini-player breathing space
                }
            }
        }
    }
}

@Composable
private fun JamMemberAvatarPill(
    member: JamMember,
    isHost: Boolean,
    isDark: Boolean
) {
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(52.dp)
    ) {
        Box(
            modifier = Modifier.size(46.dp)
        ) {
            if (member.avatarUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isHost) Accent else Accent.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = member.username.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = if (isHost) Color.White else textPrimary,
                        fontSize = 16.sp
                    )
                }
            } else {
                AsyncImage(
                    model = member.avatarUrl,
                    contentDescription = member.username,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                )
            }

            // Online Dot
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .align(Alignment.BottomEnd)
                    .background(Color.Black, CircleShape)
                    .padding(1.5.dp)
                    .background(if (member.online) OnlineGreen else Color(0xFF94A3B8), CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = member.username,
            fontSize = 11.sp,
            color = textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (isHost) {
            Text(
                text = "HOST",
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Accent
            )
        }
    }
}

@Composable
private fun JamQueueItemRow(
    key: String,
    song: com.synq.features.music.domain.model.Song,
    addedByName: String,
    isDark: Boolean,
    canRemove: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
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
            .background(if (isDark) Color(0xFF181320) else Color.White)
            .border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f),
                shape
            )
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
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${song.primaryArtistName} • added by $addedByName",
                fontSize = 11.5.sp,
                color = textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canMoveUp) {
                IconButton(onClick = onMoveUp, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move Up",
                        tint = textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (canMoveDown) {
                IconButton(onClick = onMoveDown, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move Down",
                        tint = textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (canRemove) {
                IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = textSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun JamRecommendedRow(
    key: String,
    song: com.synq.features.music.domain.model.Song,
    isDark: Boolean,
    canAdd: Boolean,
    canDismiss: Boolean,
    onAdd: () -> Unit,
    onDismiss: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF64748B)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isDark) Color(0xFF181320) else Color.White)
            .border(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f),
                shape
            )
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
                color = textPrimary,
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

        // Add Button (Promote)
        Button(
            onClick = onAdd,
            shape = RoundedCornerShape(100.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent.copy(alpha = if (canAdd) 1f else 0.4f)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        if (canDismiss) {
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = textSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// D2. People Sheet (Bottom Sheet for Members & Permissions)
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleSheet(
    room: RoomSnapshot,
    myUserId: String,
    isHost: Boolean,
    onDismiss: () -> Unit,
    onUpdateMemberPerms: (userId: String, perms: JamPermissions) -> Unit,
    onUpdateDefaults: (perms: JamPermissions) -> Unit,
    onMakeHost: (userId: String) -> Unit,
    onKickMember: (userId: String) -> Unit
) {
    val isDark = isAppDark()
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF64748B)

    var memberToMakeHost by remember { mutableStateOf<JamMember?>(null) }
    var memberToKick by remember { mutableStateOf<JamMember?>(null) }

    // Make Host Confirm Dialog
    memberToMakeHost?.let { target ->
        AlertDialog(
            onDismissRequest = { memberToMakeHost = null },
            title = { Text("Transfer Host?", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = { Text("Make ${target.username} the host? You will become a regular member.", color = textSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onMakeHost(target.userId)
                        memberToMakeHost = null
                    }
                ) {
                    Text("Make Host", fontWeight = FontWeight.Bold, color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToMakeHost = null }) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }

    // Kick Member Confirm Dialog
    memberToKick?.let { target ->
        AlertDialog(
            onDismissRequest = { memberToKick = null },
            title = { Text("Remove Listener?", fontWeight = FontWeight.Bold, color = textPrimary) },
            text = { Text("Remove ${target.username} from this Jam session?", color = textSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onKickMember(target.userId)
                        memberToKick = null
                    }
                ) {
                    Text("Remove", fontWeight = FontWeight.Bold, color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToKick = null }) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1E1624) else Color(0xFFFFFFFF),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "People in this Jam (${room.members.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Host Only: "Everyone can…" Defaults Section
            if (isHost) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Accent.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Everyone can…",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Accent
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = room.defaults.queueAdd,
                                onClick = {
                                    onUpdateDefaults(room.defaults.copy(queueAdd = !room.defaults.queueAdd))
                                },
                                label = { Text("Add songs", fontSize = 11.5.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Accent,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = room.defaults.playback,
                                onClick = {
                                    onUpdateDefaults(room.defaults.copy(playback = !room.defaults.playback))
                                },
                                label = { Text("Playback", fontSize = 11.5.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Accent,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = room.defaults.queueManage,
                                onClick = {
                                    onUpdateDefaults(room.defaults.copy(queueManage = !room.defaults.queueManage))
                                },
                                label = { Text("Manage queue", fontSize = 11.5.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Accent,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Member Rows
            Text(
                text = "Members",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = textSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            room.members.forEach { member ->
                val isMemberHost = member.userId == room.hostUserId
                val isMe = member.userId == myUserId

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar + online dot
                        Box(modifier = Modifier.size(42.dp)) {
                            if (member.avatarUrl.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isMemberHost) Accent else Accent.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = member.username.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMemberHost) Color.White else textPrimary
                                    )
                                }
                            } else {
                                AsyncImage(
                                    model = member.avatarUrl,
                                    contentDescription = member.username,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(Color.Black, CircleShape)
                                    .padding(1.dp)
                                    .background(if (member.online) OnlineGreen else Color(0xFF94A3B8), CircleShape)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = member.username + if (isMe) " (You)" else "",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                if (isMemberHost) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = Accent
                                    ) {
                                        Text(
                                            text = "HOST",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (member.online) "Online" else "Away",
                                fontSize = 11.5.sp,
                                color = if (member.online) OnlineGreen else textSecondary
                            )
                        }

                        // Host action buttons for other members
                        if (isHost && !isMemberHost) {
                            IconButton(onClick = { memberToMakeHost = member }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.SupervisorAccount,
                                    contentDescription = "Make Host",
                                    tint = Accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = { memberToKick = member }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.PersonRemove,
                                    contentDescription = "Remove Listener",
                                    tint = Danger,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Permission Chips
                    if (!isMemberHost) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 54.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isHost) {
                                // Editable Chips for Host
                                FilterChip(
                                    selected = member.perms.queueAdd,
                                    onClick = {
                                        onUpdateMemberPerms(member.userId, member.perms.copy(queueAdd = !member.perms.queueAdd))
                                    },
                                    label = { Text("Add songs", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Accent,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = member.perms.playback,
                                    onClick = {
                                        onUpdateMemberPerms(member.userId, member.perms.copy(playback = !member.perms.playback))
                                    },
                                    label = { Text("Playback", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Accent,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = member.perms.queueManage,
                                    onClick = {
                                        onUpdateMemberPerms(member.userId, member.perms.copy(queueManage = !member.perms.queueManage))
                                    },
                                    label = { Text("Manage queue", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Accent,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            } else {
                                // Read-only chips for Non-Hosts
                                if (member.perms.queueAdd) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("Add songs", fontSize = 11.sp) }
                                    )
                                }
                                if (member.perms.playback) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("Playback", fontSize = 11.sp) }
                                    )
                                }
                                if (member.perms.queueManage) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("Manage queue", fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f))
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
