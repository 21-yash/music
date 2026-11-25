package com.synq.features.music.jam

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Sync
import com.synq.features.music.util.SeekHold
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synq.RootViewModel
import com.synq.features.music.components.AnimatedEqualizerBars
import com.synq.ui.isAppDark
import com.synq.ui.motion.MotionTokens
import com.synq.ui.motion.performConfirm
import com.synq.ui.motion.performToggle
import com.synq.ui.motion.pressable
import com.synq.ui.theme.PinkDeep
import com.synq.ui.theme.PinkGradient
import com.synq.ui.theme.PinkGradientHero
import com.synq.ui.theme.PinkPrimary
import com.synq.ui.theme.SemanticLive
import com.synq.ui.theme.SemanticSuccess
import com.synq.ui.theme.SemanticWarning
import com.synq.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
    val hasPendingCommand by rootViewModel.jamHasPendingCommand.collectAsState()
    val localPlaybackState by rootViewModel.jamLocalPlaybackState.collectAsState()
    val playerPlaybackState by rootViewModel.playbackState.collectAsState()
    val clockSync = rootViewModel.jamClockSync

    val isDark = isAppDark()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    val bgColor = MaterialTheme.colorScheme.background
    val cardBg = MaterialTheme.colorScheme.surfaceContainerLow
    val dialogBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val subtleBorder = MaterialTheme.colorScheme.outlineVariant
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val pinkText = if (isDark) PinkPrimary else PinkDeep

    var showIntroDialog by remember { mutableStateOf(false) }
    var showConfirmEndDialog by remember { mutableStateOf(false) }
    var showPeopleSheet by remember { mutableStateOf(false) }
    var isHistoryExpanded by remember { mutableStateOf(false) }
    var showReconnectingChip by remember { mutableStateOf(false) }

    LaunchedEffect(clientState, hasPendingCommand) {
        if (clientState is JamClientState.Reconnecting) {
            if (hasPendingCommand) {
                showReconnectingChip = true
            } else if (!showReconnectingChip) {
                kotlinx.coroutines.delay(2000L)
                showReconnectingChip = true
            }
        } else {
            showReconnectingChip = false
        }
    }

    val hasSeenBatteryTip by rootViewModel.jamPreferences.hasSeenBatteryTip.collectAsState()
    var showBatteryOptimizationDialog by remember { mutableStateOf(false) }

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
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = dialogBg,
            title = {
                Text(
                    text = "End Jam Session?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            },
            text = {
                Text(
                    text = "This will end the session and stop playback for everyone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performConfirm()
                        showConfirmEndDialog = false
                        rootViewModel.endJam()
                    }
                ) {
                    Text(
                        text = "End Jam",
                        style = MaterialTheme.typography.labelLarge,
                        color = SemanticLive,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmEndDialog = false }) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = textSecondary
                    )
                }
            }
        )
    }

    // Battery Optimization Guidance Dialog
    if (showBatteryOptimizationDialog) {
        AlertDialog(
            onDismissRequest = {
                rootViewModel.jamPreferences.setSeenBatteryTip(true)
                showBatteryOptimizationDialog = false
            },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = dialogBg,
            title = {
                Text(
                    text = "Smooth Background Jam",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            },
            text = {
                Text(
                    text = "To keep Jam sessions smoothly in sync when your phone is locked or another app is open, Synq works best with unrestricted battery usage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textSecondary
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
                    Text(
                        text = "Optimize",
                        style = MaterialTheme.typography.labelLarge,
                        color = pinkText,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        rootViewModel.jamPreferences.setSeenBatteryTip(true)
                        showBatteryOptimizationDialog = false
                    }
                ) {
                    Text(
                        text = "Dismiss",
                        style = MaterialTheme.typography.labelLarge,
                        color = textSecondary
                    )
                }
            }
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
                    .padding(horizontal = Spacing.screenHorizontal, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .pressable(pressedScale = MotionTokens.ChipPressScale, onClick = onBackClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textPrimary
                    )
                }

                Spacer(modifier = Modifier.width(Spacing.xxs))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Jam",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    Text(
                        text = if (uiState.isInJam) {
                            "${uiState.members.size} listening together"
                        } else {
                            "No active session"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = textSecondary
                    )
                }

                if (uiState.isInJam && uiState.sessionCode != null) {
                    val code = uiState.sessionCode ?: ""

                    // Code Chip (Tap to copy)
                    Surface(
                        shape = CircleShape,
                        color = PinkPrimary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, PinkPrimary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .height(36.dp)
                            .defaultMinSize(minWidth = 48.dp)
                            .pressable(
                                pressedScale = MotionTokens.ChipPressScale,
                                onClick = {
                                    haptic.performConfirm()
                                    clipboardManager.setText(AnnotatedString(code))
                                    Toast.makeText(context, "Code copied: $code", Toast.LENGTH_SHORT).show()
                                }
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = code,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFeatureSettings = "tnum",
                                    letterSpacing = 1.sp
                                ),
                                fontWeight = FontWeight.Bold,
                                color = pinkText
                            )
                            Spacer(modifier = Modifier.width(Spacing.xxs))
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy code $code",
                                tint = pinkText,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(Spacing.xs))

                    // Share Button (Android share sheet)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .pressable(
                                pressedScale = MotionTokens.ChipPressScale,
                                onClick = {
                                    haptic.performConfirm()
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "Join my Synq Jam — code $code")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Join my Synq Jam — code $code"))
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share invite code",
                            tint = pinkText
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
                            .clip(CircleShape)
                            .background(PinkGradientHero),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = "Jam",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.l))

                    Text(
                        text = "No Active Jam",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Text(
                        text = "Start a Jam session to listen with friends or enter a 4-letter invite code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(50.dp)
                            .pressable(
                                pressedScale = MotionTokens.ButtonPressScale,
                                onClick = {
                                    haptic.performConfirm()
                                    showIntroDialog = true
                                }
                            )
                            .clip(CircleShape)
                            .background(PinkGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Start or Join Jam",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            } else {
                // ── Active Jam Content ──
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.screenHorizontal)
                ) {
                    item(key = "jam_top_section") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Status Pill
                    val isStepOut = localPlaybackState is LocalPlaybackState.LocalStepOut
                    val isCatchingUp = localPlaybackState is LocalPlaybackState.CatchingUp
                    val isReconnecting = showReconnectingChip && (clientState is JamClientState.Reconnecting)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = when {
                                isStepOut -> SemanticWarning.copy(alpha = 0.16f)
                                isReconnecting -> SemanticWarning.copy(alpha = 0.12f)
                                isCatchingUp -> PinkPrimary.copy(alpha = 0.12f)
                                else -> SemanticSuccess.copy(alpha = 0.12f)
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isStepOut -> SemanticWarning.copy(alpha = 0.5f)
                                    isReconnecting -> SemanticWarning.copy(alpha = 0.4f)
                                    isCatchingUp -> PinkPrimary.copy(alpha = 0.4f)
                                    else -> SemanticSuccess.copy(alpha = 0.4f)
                                }
                            ),
                            modifier = Modifier
                                .height(32.dp)
                                .then(
                                    if (isStepOut) {
                                        Modifier.pressable(
                                            pressedScale = MotionTokens.ChipPressScale,
                                            onClick = {
                                                haptic.performConfirm()
                                                rootViewModel.resumeFromLocalStepOut()
                                            }
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = Spacing.s)
                            ) {
                                when {
                                    isStepOut -> {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Resume for me",
                                            tint = SemanticWarning,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Paused for you only • Tap to resume",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = SemanticWarning
                                        )
                                    }
                                    isReconnecting -> {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = "Reconnecting",
                                            tint = SemanticWarning,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Reconnecting…",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = SemanticWarning
                                        )
                                    }
                                    isCatchingUp -> {
                                        Icon(
                                            imageVector = Icons.Default.FastForward,
                                            contentDescription = "Catching up",
                                            tint = pinkText,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Catching up…",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = pinkText
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Synced",
                                            tint = SemanticSuccess,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Synced ✓",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = SemanticSuccess
                                        )
                                    }
                                }
                            }
                        }

                        // Host / Guest indicator badge
                        Text(
                            text = if (uiState.isHost) "HOST" else "GUEST",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = pinkText
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Participant Avatars Row (Tap to open People sheet D2)
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = cardBg,
                        border = BorderStroke(1.dp, subtleBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressable(
                                pressedScale = MotionTokens.CardPressScale,
                                onClick = { showPeopleSheet = true }
                            )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "People in Jam (${uiState.members.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                                    fontWeight = FontWeight.SemiBold,
                                    color = textPrimary
                                )
                                Text(
                                    text = if (uiState.isHost) "Manage >" else "View all >",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = pinkText
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                                verticalAlignment = Alignment.Top
                            ) {
                                uiState.members.forEach { member ->
                                    JamMemberAvatarPill(
                                        member = member,
                                        isHost = member.userId == uiState.hostUserId
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.m))

                    // ── Now Playing Card ──
                    val nowPlaying = uiState.nowPlaying
                    val nowPlayingItem = nowPlaying.item

                    if (nowPlayingItem != null) {
                        val hostName = uiState.hostName.ifBlank { "Host" }
                        val addedByMember = uiState.members.find { it.userId == nowPlayingItem.addedByUserId }
                        val isPlaying = nowPlaying.status == JamPlaybackStatus.PLAYING

                        Card(
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        PinkPrimary.copy(alpha = 0.35f),
                                        subtleBorder
                                    )
                                )
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize()
                        ) {
                            Column(
                                modifier = Modifier
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                PinkPrimary.copy(alpha = 0.06f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                                    .padding(horizontal = Spacing.m, vertical = 14.dp)
                            ) {
                                // Top Header: Equalizer + NOW PLAYING + Added By Tag
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AnimatedEqualizerBars(
                                            color = PinkPrimary,
                                            isPlaying = isPlaying && !isStepOut,
                                            barCount = 3,
                                            modifier = Modifier.padding(end = 6.dp)
                                        )
                                        Text(
                                            text = "NOW PLAYING",
                                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = pinkText
                                        )
                                    }

                                    if (!nowPlayingItem.recommended) {
                                        Surface(
                                            shape = CircleShape,
                                            color = PinkPrimary.copy(alpha = 0.12f),
                                            border = BorderStroke(1.dp, PinkPrimary.copy(alpha = 0.25f))
                                        ) {
                                            val displayName = addedByMember?.username ?: "Host"
                                            Text(
                                                text = "Added by $displayName",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = pinkText,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(nowPlayingItem.song.imageUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Artwork for ${nowPlayingItem.song.title}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(MaterialTheme.shapes.medium)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.s))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = nowPlayingItem.song.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = nowPlayingItem.song.primaryArtistName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Progress Slider (isolated to avoid recomposing parent JamScreen)
                                JamTrackProgressBar(
                                    nowPlaying = nowPlaying,
                                    nowPlayingItem = nowPlayingItem,
                                    localPlaybackState = localPlaybackState,
                                    playerDurationMs = playerPlaybackState.durationMs,
                                    clockSync = clockSync,
                                    canControlPlayback = uiState.canControlPlayback,
                                    hostName = hostName,
                                    onSeekTo = { targetMs -> rootViewModel.jamSeekTo(targetMs) }
                                )

                                Spacer(modifier = Modifier.height(Spacing.xs))

                                // Transport Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Previous
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .alpha(if (uiState.canControlPlayback) 1f else 0.38f)
                                            .clip(CircleShape)
                                            .pressable(
                                                pressedScale = MotionTokens.ButtonPressScale,
                                                onClick = {
                                                    if (!uiState.canControlPlayback) {
                                                        Toast.makeText(context, "Ask $hostName for playback control", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        haptic.performConfirm()
                                                        rootViewModel.jamPreviousTrack()
                                                    }
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipPrevious,
                                            contentDescription = "Previous Track",
                                            tint = textPrimary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    // Play / Pause / Step-Out
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .pressable(
                                                pressedScale = MotionTokens.ButtonPressScale,
                                                onClick = {
                                                    haptic.performConfirm()
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
                                                }
                                            )
                                            .clip(CircleShape)
                                            .background(PinkGradient),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val displayPlaying = if (isStepOut) false else isPlaying
                                        Icon(
                                            imageVector = if (displayPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (displayPlaying) "Pause" else "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }

                                    // Next
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .alpha(if (uiState.canControlPlayback) 1f else 0.38f)
                                            .clip(CircleShape)
                                            .pressable(
                                                pressedScale = MotionTokens.ButtonPressScale,
                                                onClick = {
                                                    if (!uiState.canControlPlayback) {
                                                        Toast.makeText(context, "Ask $hostName for playback control", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        haptic.performConfirm()
                                                        rootViewModel.jamNextTrack()
                                                    }
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Next Track",
                                            tint = textPrimary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                val isUnplayable = uiState.isLocalPlaybackUnplayable || jamState.isLocalPlaybackUnplayable
                                if (isUnplayable) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.small)
                                            .background(SemanticLive.copy(alpha = 0.12f))
                                            .padding(horizontal = Spacing.s, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Can't play on this device",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = SemanticLive
                                        )
                                        TextButton(
                                            onClick = { rootViewModel.retryJamPlayback() },
                                            contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = 0.dp)
                                        ) {
                                            Text(
                                                text = "Retry",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = SemanticLive
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
                                            style = MaterialTheme.typography.labelMedium,
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
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = pinkText
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Empty Now Playing Card
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            border = BorderStroke(1.dp, subtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.xl),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No track currently playing",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                Spacer(modifier = Modifier.height(Spacing.xxs))
                                Text(
                                    text = "Add songs to start listening together!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textSecondary
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = onNavigateToSearch,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                                    modifier = Modifier.pressable(pressedScale = MotionTokens.ButtonPressScale)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Add the first song",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.l))

                    // ── Up Next Section ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Up Next (${uiState.userQueue.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            if (!uiState.canManageQueue) {
                                Text(
                                    text = "Host controls order",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textSecondary.copy(alpha = 0.7f)
                                )
                            }
                        }

                        TextButton(
                            onClick = onNavigateToSearch,
                            colors = ButtonDefaults.textButtonColors(contentColor = pinkText)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(Spacing.xxs))
                            Text(
                                text = "Add Song",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    if (uiState.userQueue.isEmpty()) {
                        Text(
                            text = "No upcoming songs added yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = textSecondary,
                            modifier = Modifier.padding(vertical = Spacing.s)
                        )
                    }
                        }
                    }

                    if (uiState.userQueue.isNotEmpty()) {
                        itemsIndexed(
                            items = uiState.userQueue,
                            key = { _, queueItem -> "queue_${queueItem.qid}" }
                        ) { index, queueItem ->
                            val isOwnSong = queueItem.addedByUserId == uiState.myUserId
                            val canRemove = isOwnSong || uiState.canManageQueue
                            val canReorder = uiState.canManageQueue
                            val addedBy = uiState.members.find { it.userId == queueItem.addedByUserId }?.username ?: "Guest"

                            JamQueueItemRow(
                                key = queueItem.qid,
                                song = queueItem.song,
                                addedByName = addedBy,
                                pinkText = pinkText,
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
                                onRemove = { rootViewModel.removeByQid(queueItem.qid) },
                                modifier = jamQueueItemAnimateModifier()
                            )
                        }
                    }

                    // ── Recommended Section Below Up Next ──
                    if (uiState.recommendedQueue.isNotEmpty()) {
                        item(key = "recommended_header") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Spacer(modifier = Modifier.height(Spacing.l))
                                Text(
                                    text = "Recommended for this Jam",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                Spacer(modifier = Modifier.height(Spacing.xs))
                            }
                        }

                        items(
                            items = uiState.recommendedQueue,
                            key = { recItem -> "rec_${recItem.qid}" }
                        ) { recItem ->
                            JamRecommendedRow(
                                key = recItem.qid,
                                song = recItem.song,
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
                                },
                                modifier = jamQueueItemAnimateModifier()
                            )
                        }
                    }

                    item(key = "jam_bottom_section") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(Spacing.l))

                    // ── History Section (Collapsible) ──
                    if (uiState.history.isNotEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = cardBg,
                            border = BorderStroke(1.dp, subtleBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize()
                                .pressable(
                                    pressedScale = MotionTokens.CardPressScale,
                                    onClick = { isHistoryExpanded = !isHistoryExpanded }
                                )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Played (${uiState.history.size})",
                                        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
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
                                        modifier = Modifier.padding(top = 10.dp)
                                    ) {
                                        uiState.history.forEach { historyItem ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 56.dp)
                                                    .padding(vertical = Spacing.xs),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(historyItem.song.imageUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(MaterialTheme.shapes.small)
                                                )
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = historyItem.song.title,
                                                        style = MaterialTheme.typography.titleMedium.copy(lineHeight = 18.sp),
                                                        color = textPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = historyItem.song.primaryArtistName,
                                                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp),
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

                        Spacer(modifier = Modifier.height(Spacing.xl))
                    }

                    // ── Leave / End Jam Action Buttons ──
                    val actionButtonShape = MaterialTheme.shapes.large

                    if (uiState.isHost) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Leave Button (Host passes on; Jam continues)
                            Surface(
                                shape = actionButtonShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, subtleBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .pressable(
                                        pressedScale = MotionTokens.ButtonPressScale,
                                        onClick = {
                                            haptic.performConfirm()
                                            rootViewModel.leaveJam()
                                        }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 14.dp, horizontal = Spacing.s),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                        contentDescription = "Leave Jam",
                                        tint = textPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text(
                                        text = "Leave Jam",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textPrimary,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Host-Only: End Jam for Everyone
                            Surface(
                                shape = actionButtonShape,
                                color = SemanticLive.copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, SemanticLive.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .pressable(
                                        pressedScale = MotionTokens.ButtonPressScale,
                                        onClick = { showConfirmEndDialog = true }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 14.dp, horizontal = Spacing.s),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "End Jam for Everyone",
                                        tint = SemanticLive,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text(
                                        text = "End Jam",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = SemanticLive,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = actionButtonShape,
                                color = SemanticLive.copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, SemanticLive.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .fillMaxWidth(0.55f)
                                    .pressable(
                                        pressedScale = MotionTokens.ButtonPressScale,
                                        onClick = {
                                            haptic.performConfirm()
                                            rootViewModel.leaveJam()
                                        }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 14.dp, horizontal = Spacing.m),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                        contentDescription = "Leave Jam",
                                        tint = SemanticLive,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Text(
                                        text = "Leave Jam",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SemanticLive,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(96.dp)) // Mini-player breathing space
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JamMemberAvatarPill(
    member: JamMember,
    isHost: Boolean
) {
    val isDark = isAppDark()
    val context = LocalContext.current
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val pinkText = if (isDark) PinkPrimary else PinkDeep

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
                        .then(
                            if (isHost) {
                                Modifier.background(PinkGradientHero)
                            } else {
                                Modifier.background(PinkPrimary.copy(alpha = 0.20f))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = member.username.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isHost) Color.White else textPrimary
                    )
                }
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(member.avatarUrl)
                        .crossfade(true)
                        .build(),
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
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
                    .padding(1.5.dp)
                    .background(
                        if (member.online) SemanticSuccess else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        CircleShape
                    )
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = member.username,
            style = MaterialTheme.typography.labelSmall,
            color = textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (isHost) {
            Text(
                text = "HOST",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = pinkText
            )
        } else {
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun JamQueueItemRow(
    key: String,
    song: com.synq.features.music.domain.model.Song,
    addedByName: String,
    pinkText: Color,
    canRemove: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pressable(
                pressedScale = MotionTokens.RowPressScale,
                onClick = onClick
            )
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(song.imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 18.sp),
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Added by $addedByName",
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp),
                color = pinkText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(Spacing.xs))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canMoveUp) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .pressable(
                            pressedScale = MotionTokens.ChipPressScale,
                            onClick = {
                                haptic.performConfirm()
                                onMoveUp()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move Up",
                        tint = textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (canMoveDown) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .pressable(
                            pressedScale = MotionTokens.ChipPressScale,
                            onClick = {
                                haptic.performConfirm()
                                onMoveDown()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move Down",
                        tint = textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (canRemove) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .pressable(
                            pressedScale = MotionTokens.ChipPressScale,
                            onClick = {
                                haptic.performConfirm()
                                onRemove()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
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
    canAdd: Boolean,
    canDismiss: Boolean,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pressable(
                pressedScale = MotionTokens.RowPressScale,
                onClick = {
                    if (canAdd) haptic.performConfirm()
                    onAdd()
                }
            )
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(song.imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 18.sp),
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.primaryArtistName,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp),
                color = textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(Spacing.xs))

        // Add Button (Promote)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .pressable(
                    pressedScale = MotionTokens.ChipPressScale,
                    onClick = {
                        if (canAdd) haptic.performConfirm()
                        onAdd()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add to Jam Queue",
                tint = if (canAdd) textSecondary else textSecondary.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp)
            )
        }

        if (canDismiss) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .pressable(
                        pressedScale = MotionTokens.ChipPressScale,
                        onClick = {
                            haptic.performConfirm()
                            onDismiss()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
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

private fun LazyItemScope.jamQueueItemAnimateModifier(): Modifier =
    Modifier.animateItem(
        fadeInSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        fadeOutSpec = tween(220, easing = FastOutSlowInEasing),
        placementSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

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
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sheetBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val subtleBorder = MaterialTheme.colorScheme.outlineVariant
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val pinkText = if (isDark) PinkPrimary else PinkDeep

    var memberToMakeHost by remember { mutableStateOf<JamMember?>(null) }
    var memberToKick by remember { mutableStateOf<JamMember?>(null) }

    // Make Host Confirm Dialog
    memberToMakeHost?.let { target ->
        AlertDialog(
            onDismissRequest = { memberToMakeHost = null },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = sheetBg,
            title = {
                Text(
                    text = "Transfer Host?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            },
            text = {
                Text(
                    text = "Make ${target.username} the host? You will become a regular member.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performConfirm()
                        onMakeHost(target.userId)
                        memberToMakeHost = null
                    }
                ) {
                    Text(
                        text = "Make Host",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = pinkText
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToMakeHost = null }) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = textSecondary
                    )
                }
            }
        )
    }

    // Kick Member Confirm Dialog
    memberToKick?.let { target ->
        AlertDialog(
            onDismissRequest = { memberToKick = null },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = sheetBg,
            title = {
                Text(
                    text = "Remove Listener?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            },
            text = {
                Text(
                    text = "Remove ${target.username} from this Jam session?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performConfirm()
                        onKickMember(target.userId)
                        memberToKick = null
                    }
                ) {
                    Text(
                        text = "Remove",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = SemanticLive
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToKick = null }) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = textSecondary
                    )
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetBg,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.m, vertical = Spacing.xs)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "People in this Jam (${room.members.size})",
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .pressable(pressedScale = MotionTokens.ChipPressScale, onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = textSecondary)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s))

            // Host Only: "Everyone can…" Defaults Section
            if (isHost) {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = PinkPrimary.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.s)) {
                        Text(
                            text = "Everyone can…",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = pinkText
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
                        ) {
                            FilterChip(
                                selected = room.defaults.queueAdd,
                                onClick = {
                                    val next = !room.defaults.queueAdd
                                    haptic.performToggle(next)
                                    onUpdateDefaults(room.defaults.copy(queueAdd = next))
                                },
                                shape = CircleShape,
                                label = {
                                    Text(
                                        text = "Add songs",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PinkPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = room.defaults.playback,
                                onClick = {
                                    val next = !room.defaults.playback
                                    haptic.performToggle(next)
                                    onUpdateDefaults(room.defaults.copy(playback = next))
                                },
                                shape = CircleShape,
                                label = {
                                    Text(
                                        text = "Playback",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PinkPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = room.defaults.queueManage,
                                onClick = {
                                    val next = !room.defaults.queueManage
                                    haptic.performToggle(next)
                                    onUpdateDefaults(room.defaults.copy(queueManage = next))
                                },
                                shape = CircleShape,
                                label = {
                                    Text(
                                        text = "Manage queue",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PinkPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.m))
            }

            // Member Rows
            Text(
                text = "Members",
                style = MaterialTheme.typography.titleSmall,
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
                        .padding(vertical = Spacing.xs)
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
                                        .then(
                                            if (isMemberHost) {
                                                Modifier.background(PinkGradientHero)
                                            } else {
                                                Modifier.background(PinkPrimary.copy(alpha = 0.20f))
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = member.username.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMemberHost) Color.White else textPrimary
                                    )
                                }
                            } else {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(member.avatarUrl)
                                        .crossfade(true)
                                        .build(),
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
                                    .background(sheetBg, CircleShape)
                                    .padding(1.dp)
                                    .background(
                                        if (member.online) SemanticSuccess else textSecondary.copy(alpha = 0.45f),
                                        CircleShape
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.width(Spacing.s))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = member.username + if (isMe) " (You)" else "",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                if (isMemberHost) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = PinkPrimary
                                    ) {
                                        Text(
                                            text = "HOST",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (member.online) "Online" else "Away",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (member.online) SemanticSuccess else textSecondary
                            )
                        }

                        // Host action buttons for other members
                        if (isHost && !isMemberHost) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .pressable(
                                        pressedScale = MotionTokens.ChipPressScale,
                                        onClick = { memberToMakeHost = member }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SupervisorAccount,
                                    contentDescription = "Make Host",
                                    tint = pinkText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .pressable(
                                        pressedScale = MotionTokens.ChipPressScale,
                                        onClick = { memberToKick = member }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonRemove,
                                    contentDescription = "Remove Listener",
                                    tint = SemanticLive,
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
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
                        ) {
                            if (isHost) {
                                // Editable Chips for Host
                                FilterChip(
                                    selected = member.perms.queueAdd,
                                    onClick = {
                                        val next = !member.perms.queueAdd
                                        haptic.performToggle(next)
                                        onUpdateMemberPerms(member.userId, member.perms.copy(queueAdd = next))
                                    },
                                    shape = CircleShape,
                                    label = {
                                        Text(
                                            text = "Add songs",
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PinkPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = member.perms.playback,
                                    onClick = {
                                        val next = !member.perms.playback
                                        haptic.performToggle(next)
                                        onUpdateMemberPerms(member.userId, member.perms.copy(playback = next))
                                    },
                                    shape = CircleShape,
                                    label = {
                                        Text(
                                            text = "Playback",
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PinkPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = member.perms.queueManage,
                                    onClick = {
                                        val next = !member.perms.queueManage
                                        haptic.performToggle(next)
                                        onUpdateMemberPerms(member.userId, member.perms.copy(queueManage = next))
                                    },
                                    shape = CircleShape,
                                    label = {
                                        Text(
                                            text = "Manage queue",
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PinkPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            } else {
                                // Read-only chips for Non-Hosts
                                if (member.perms.queueAdd) {
                                    AssistChip(
                                        onClick = {},
                                        shape = CircleShape,
                                        label = {
                                            Text(
                                                text = "Add songs",
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    )
                                }
                                if (member.perms.playback) {
                                    AssistChip(
                                        onClick = {},
                                        shape = CircleShape,
                                        label = {
                                            Text(
                                                text = "Playback",
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    )
                                }
                                if (member.perms.queueManage) {
                                    AssistChip(
                                        onClick = {},
                                        shape = CircleShape,
                                        label = {
                                            Text(
                                                text = "Manage queue",
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = subtleBorder.copy(alpha = 0.5f))
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JamTrackProgressBar(
    nowPlaying: JamNowPlaying,
    nowPlayingItem: JamQueueItem,
    localPlaybackState: LocalPlaybackState,
    playerDurationMs: Long,
    clockSync: ClockSync,
    canControlPlayback: Boolean,
    hostName: String,
    onSeekTo: (Long) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var liveExpectedPos by remember { mutableLongStateOf(0L) }
    val isPlaying = nowPlaying.status == JamPlaybackStatus.PLAYING
    val context = LocalContext.current
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(nowPlaying, localPlaybackState, isPlaying) {
        liveExpectedPos = clockSync.expectedPosition(nowPlaying, clockSync.serverNow())
        if (!isPlaying) return@LaunchedEffect

        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                liveExpectedPos = clockSync.expectedPosition(nowPlaying, clockSync.serverNow())
                delay(200)
            }
        }
    }

    val jamProgress = computeJamProgress(
        metadataDurationSec = nowPlayingItem.song.durationSeconds,
        playerDurationMs = playerDurationMs,
        positionMs = liveExpectedPos
    )
    val durationMs = jamProgress.durationMs
    val progress = jamProgress.fraction
    val trackKey = "${nowPlayingItem.song.id}_${nowPlaying?.trackSeq ?: 0L}"

    var dragProgressRatio by remember { mutableStateOf<Float?>(null) }
    var seekHold by remember { mutableStateOf<SeekHold?>(null) }

    LaunchedEffect(trackKey) {
        dragProgressRatio = null
        seekHold = null
    }

    LaunchedEffect(seekHold) {
        val hold = seekHold ?: return@LaunchedEffect
        val remaining = hold.timeoutMs - (android.os.SystemClock.elapsedRealtime() - hold.releasedAtMs)
        if (remaining > 0) {
            delay(remaining)
        }
        seekHold = null
    }

    LaunchedEffect(seekHold, liveExpectedPos) {
        val hold = seekHold ?: return@LaunchedEffect
        val nowRealtime = android.os.SystemClock.elapsedRealtime()
        if (hold.shouldRelease(liveExpectedPos, nowRealtime, trackKey)) {
            seekHold = null
        }
    }

    val heldRatio = seekHold?.let {
        if (durationMs > 0L) (it.targetMs.toFloat() / durationMs).coerceIn(0f, 1f) else null
    }

    Slider(
        value = (dragProgressRatio ?: heldRatio ?: progress).coerceIn(0f, 1f),
        onValueChange = { targetRatio ->
            if (canControlPlayback && jamProgress.canSeek) {
                dragProgressRatio = targetRatio
                seekHold = null
            }
        },
        onValueChangeFinished = {
            val target = dragProgressRatio
            dragProgressRatio = null
            if (!canControlPlayback) {
                Toast.makeText(context, "Ask $hostName for playback control", Toast.LENGTH_SHORT).show()
            } else if (jamProgress.canSeek && target != null) {
                val targetMs = (target * durationMs).toLong()
                seekHold = SeekHold(
                    targetMs = targetMs,
                    releasedAtMs = android.os.SystemClock.elapsedRealtime(),
                    trackKey = trackKey
                )
                onSeekTo(targetMs)
            }
        },
        enabled = canControlPlayback && jamProgress.canSeek,
        thumb = {},
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(4.dp),
                drawStopIndicator = null,
                thumbTrackGapSize = 0.dp,
                colors = SliderDefaults.colors(
                    activeTrackColor = PinkPrimary,
                    inactiveTrackColor = textSecondary.copy(alpha = 0.22f),
                    disabledActiveTrackColor = PinkPrimary.copy(alpha = 0.4f),
                    disabledInactiveTrackColor = textSecondary.copy(alpha = 0.12f)
                )
            )
        },
        colors = SliderDefaults.colors(
            thumbColor = PinkPrimary,
            activeTrackColor = PinkPrimary,
            inactiveTrackColor = textSecondary.copy(alpha = 0.22f),
            disabledThumbColor = PinkPrimary.copy(alpha = 0.4f),
            disabledActiveTrackColor = PinkPrimary.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
    )

    val displayPos = dragProgressRatio?.let { (it * durationMs).toLong() }
        ?: seekHold?.targetMs
        ?: liveExpectedPos
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = formatTime(displayPos),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = textSecondary
        )
        Text(
            text = if (durationMs > 0L) formatTime(durationMs) else "--:--",
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = textSecondary
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
