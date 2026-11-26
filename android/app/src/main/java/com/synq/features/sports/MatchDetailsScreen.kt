package com.synq.features.sports

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.synq.features.sports.domain.model.*
import com.synq.features.sports.highlights.highlightsTabItems
import com.synq.features.sports.live.*
import com.synq.ui.SynqLoadingIndicator
import com.synq.ui.motion.MotionTokens
import com.synq.ui.motion.pressable
import com.synq.ui.theme.OnSportsAccent
import com.synq.ui.theme.SportsAccent
import kotlinx.coroutines.launch

// ─── DESIGN TOKENS (wired directly to theme tokens) ──────────────

private val TextMain: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val TextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val AccentRed = Color(0xFFEF4444)
private val BallWicket = Color(0xFFEF4444)
private val BallFour = Color(0xFF2563EB)
private val BallSix = Color(0xFF9B51E0)
private val ActiveBatterGreen = Color(0xFF22C55E)
private val CardBlue: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow
private val CardHeaderBg: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
private val SubHeaderBg: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerLowest
private val BorderLight: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
private val AvatarBg: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest

// Fixed widths for scorecard stat columns (keeps numbers on one line, gives the name column the rest)
private val ColR = 32.dp
private val ColB = 32.dp
private val Col4s = 26.dp
private val Col6s = 26.dp
private val ColSR = 50.dp
private val ColO = 40.dp
private val ColM = 28.dp
private val ColRuns = 36.dp
private val ColW = 28.dp
private val ColEco = 44.dp

@Composable
fun MatchDetailsScreen(
    onBackClick: () -> Unit,
    onHighlightClick: (matchId: String, highlightId: String) -> Unit = { _, _ -> },
    viewModel: MatchDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Tab State (rememberSaveable preserves tab across forward navigation to HighlightPlayerScreen)
    val tabs = listOf("Info", "Live", "Scorecard", "Squads", "Highlights")
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedInning by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex == 2) {
            viewModel.refreshScorecard(force = true)
        } else if (selectedTabIndex == 4) {
            viewModel.loadHighlights()
        }
    }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val isScrolledPastCard by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 24
        }
    }
    val borderAlpha by animateFloatAsState(
        targetValue = if (isScrolledPastCard) 0.35f else 0f,
        label = "tabStickyBorder"
    )
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    // Reset default text lineHeight and letterSpacing for crisp compact cricket stats
    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(
            lineHeight = TextUnit.Unspecified,
            letterSpacing = 0.sp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (uiState.isLoading && uiState.details == null) {
                SynqLoadingIndicator(modifier = Modifier.align(Alignment.Center), size = 48.dp, barCount = 5)
            } else if (uiState.error != null && uiState.details == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadData() }) { Text("Retry") }
                }
            } else if (uiState.details != null) {
                val details = uiState.details!!

                Column(modifier = Modifier.fillMaxSize()) {
                    // STICKY HEADER: Back button + Title & Series
                    CustomHeader(
                        title = "${details.summary.team1.shortName} vs ${details.summary.team2.shortName}",
                        subtitle = details.summary.seriesName,
                        onBackClick = onBackClick
                    )

                    // SCROLLABLE CONTAINER: MatchCard scrolls off; Tab Pills stick below CustomHeader!
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 240.dp)
                    ) {
                        // Match Card (scrolls)
                        item(key = "match_card") {
                            MatchDetailsSummaryCard(
                                summary = details.summary,
                                activeLiveEvent = uiState.activeLiveEvent,
                                modifier = Modifier
                                    .padding(horizontal = 20.dp)
                                    .padding(top = 4.dp, bottom = 8.dp)
                            )
                        }

                        // STICKY TAB PILLS: Sticks right below CustomHeader when scrolled
                        stickyHeader(key = "sticky_tab_pills") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .drawBehind {
                                        if (borderAlpha > 0f) {
                                            drawLine(
                                                color = outlineColor.copy(alpha = borderAlpha),
                                                start = Offset(0f, size.height),
                                                end = Offset(size.width, size.height),
                                                strokeWidth = 1.dp.toPx()
                                            )
                                        }
                                    }
                            ) {
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(tabs.size) { index ->
                                        val isSelected = selectedTabIndex == index
                                        Box(
                                            modifier = Modifier
                                                .height(32.dp)
                                                .pressable(
                                                    onClick = {
                                                        selectedTabIndex = index
                                                        coroutineScope.launch {
                                                            lazyListState.animateScrollToItem(0)
                                                        }
                                                    },
                                                    pressedScale = MotionTokens.ChipPressScale
                                                )
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) SportsAccent else MaterialTheme.colorScheme.surfaceContainerHighest
                                                )
                                                .padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = tabs[index],
                                                color = if (isSelected) OnSportsAccent else TextSecondary,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // TAB CONTENT ITEMS
                        when (selectedTabIndex) {
                            0 -> infoTabItems(details = details)
                            1 -> liveTabItems(
                                details = details,
                                activeLiveEvent = uiState.activeLiveEvent,
                                recentBallItems = uiState.recentBallItems,
                                lastWicketPulseId = uiState.lastWicketPulseId
                            )
                            2 -> scorecardTabItems(
                                scorecard = uiState.scorecard,
                                state = details.summary.state,
                                selectedInning = selectedInning,
                                onInningSelected = { selectedInning = it }
                            )
                            3 -> squadsTabItems(squads = uiState.squads, state = details.summary.state)
                            4 -> highlightsTabItems(
                                highlights = uiState.highlights,
                                isLoading = uiState.isHighlightsLoading,
                                error = uiState.highlightsError,
                                matchState = details.summary.state,
                                onSelectHighlight = { highlight ->
                                    onHighlightClick(details.summary.id, highlight.id)
                                },
                                onRetryLoad = { viewModel.loadHighlights(forceRefresh = true) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── SHARED BUILDING BLOCKS ───────────────────────────────────────────

/** Rounded card with 1px border. */
@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardBlue)
            .border(1.dp, BorderLight, shape),
        content = content
    )
}

/** Card header strip with title and bottom divider. */
@Composable
private fun CardHeader(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardHeaderBg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
    HorizontalDivider(color = BorderLight)
}

@Composable
private fun CardHeaderTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextMain)
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = TextMain,
        modifier = modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun PlayerAvatar(iconTint: Color = TextSecondary) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(AvatarBg, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Person, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
    }
}

// ─── INFO TAB ITEMS ───────────────────────────────────────────────────

private fun LazyListScope.infoTabItems(details: MatchDetails) {
    item {
        SectionCard(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            CardHeader { CardHeaderTitle("Match Details") }

            val titleStr = buildString {
                append(details.summary.team1.shortName)
                append(" vs ")
                append(details.summary.team2.shortName)
                if (details.summary.matchDesc.isNotBlank()) append(" • ${details.summary.matchDesc}")
                if (details.summary.series.isNotBlank()) append(" • ${details.summary.series}")
            }
            InfoRow("Match", titleStr)
            InfoRow("Series", details.summary.series)
            if (details.summary.venue.isNotBlank()) {
                InfoRow("Venue", details.summary.venue)
            }

            if (details.summary.startTime > 0L) {
                val dateStr = java.text.SimpleDateFormat(
                    "MMM dd, yyyy • hh:mm a",
                    java.util.Locale.getDefault()
                ).format(java.util.Date(details.summary.startTime))
                InfoRow("Date & Time", dateStr)
            }

            InfoRow("Toss", if (details.toss.isNotBlank()) details.toss else "-", isLast = true)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, isLast: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, modifier = Modifier.width(100.dp), color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Normal)
        Text(
            value,
            modifier = Modifier.weight(1f),
            color = TextMain,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal
        )
    }
    if (!isLast) HorizontalDivider(color = BorderLight)
}

// ─── HEADER ───────────────────────────────────────────────────────────

@Composable
private fun CustomHeader(title: String, subtitle: String, onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 8.dp, top = 8.dp, end = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextMain,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = TextMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─── MATCH CARD ───────────────────────────────────────────────────────

@Composable
internal fun MatchDetailsSummaryCard(
    summary: MatchSummary,
    modifier: Modifier = Modifier,
    activeLiveEvent: LiveMatchEvent? = null
) {
    val summaryType = activeLiveEvent?.matchCardType
    val summaryId = if (summaryType != null) activeLiveEvent.id else null
    val hasMultiInnings = LiveMatchEventDetector.hasMultipleInnings(summary)
    var showInningsPopup by remember(summary.id) { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.large
    val animatedBorderColor = rememberAnimatedEventBorderColor(
        eventId = summaryId,
        eventType = summaryType,
        baseColor = BorderLight
    )
    val team1First = LiveMatchEventDetector.isTeam1BattingFirst(summary)
    val topTeam = if (team1First) summary.team1 else summary.team2
    val topScore = if (team1First) (summary.team1Score ?: "") else (summary.team2Score ?: "")
    val bottomTeam = if (team1First) summary.team2 else summary.team1
    val bottomScore = if (team1First) (summary.team2Score ?: "") else (summary.team1Score ?: "")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (hasMultiInnings) {
                    Modifier.pressable(
                        onClick = { showInningsPopup = true },
                        pressedScale = MotionTokens.CardPressScale
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(CardBlue)
            .border(1.dp, animatedBorderColor, shape)
    ) {
        if (summaryType == LiveEventType.NO_BALL) {
            NoBallSirenOverlay(
                triggerId = summaryId,
                modifier = Modifier.matchParentSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Series name & Badge
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = summary.seriesName,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = summary.format.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SportsAccent
                    )
                }
            }

            val topActive = summaryType != null && (
                activeLiveEvent.battingTeamShort == topTeam.shortName ||
                    (activeLiveEvent.battingTeamShort == null && topScore.isNotBlank() && bottomScore.isBlank())
                )
            TeamCardRow(
                name = topTeam.shortName,
                score = topScore,
                logoUrl = topTeam.logoUrl,
                floatingPillType = if (topActive) summaryType else null,
                floatingPillId = if (topActive) summaryId else null,
                shakeTriggerId = if (topActive && summaryType == LiveEventType.WICKET) summaryId else null
            )
            Spacer(modifier = Modifier.height(8.dp))
            val bottomActive = summaryType != null && (
                activeLiveEvent.battingTeamShort == bottomTeam.shortName ||
                    (!topActive && activeLiveEvent.battingTeamShort == null && bottomScore.isNotBlank())
                )
            TeamCardRow(
                name = bottomTeam.shortName,
                score = bottomScore,
                logoUrl = bottomTeam.logoUrl,
                floatingPillType = if (bottomActive) summaryType else null,
                floatingPillId = if (bottomActive) summaryId else null,
                shakeTriggerId = if (bottomActive && summaryType == LiveEventType.WICKET) summaryId else null
            )

            // Status + Bottom-right Innings chip
            if (summary.status.isNotBlank() || hasMultiInnings) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (summary.status.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (summary.isLive) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(AccentRed, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = summary.status,
                                fontSize = 12.sp,
                                fontWeight = if (summary.isLive) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (summary.isLive) AccentRed else TextSecondary,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    if (hasMultiInnings) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Row(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(start = 8.dp, end = 5.dp, top = 3.dp, bottom = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Innings",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "View all innings",
                                tint = TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showInningsPopup && hasMultiInnings) {
        MultiInningsScoreDialog(
            seriesName = summary.seriesName,
            topTeam = topTeam,
            topScore = topScore,
            bottomTeam = bottomTeam,
            bottomScore = bottomScore,
            onDismiss = { showInningsPopup = false }
        )
    }
}

@Composable
private fun MultiInningsScoreDialog(
    seriesName: String,
    topTeam: Team,
    topScore: String,
    bottomTeam: Team,
    bottomScore: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Column {
                Text(
                    text = "All Innings",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMain
                )
                if (seriesName.isNotBlank()) {
                    Text(
                        text = seriesName,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InningsBreakdownSection(team = topTeam, rawScore = topScore)
                HorizontalDivider(color = BorderLight)
                InningsBreakdownSection(team = bottomTeam, rawScore = bottomScore)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = SportsAccent, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun InningsBreakdownSection(
    team: Team,
    rawScore: String
) {
    val innings = LiveMatchEventDetector.splitInnings(rawScore)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (team.logoUrl != null) {
                    AsyncImage(
                        model = team.logoUrl,
                        contentDescription = team.shortName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = team.shortName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextMain
            )
            if (team.name.isNotBlank() && !team.name.equals(team.shortName, ignoreCase = true)) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = team.name,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (innings.isEmpty()) {
            Text(
                text = "Yet to bat",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 30.dp)
            )
        } else {
            innings.forEachIndexed { idx, part ->
                val label = when (idx) {
                    0 -> "1st Innings"
                    1 -> "2nd Innings"
                    else -> "${idx + 1}th Innings"
                }
                val isLatest = idx == innings.lastIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 30.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = part.currentScore,
                            fontSize = 15.sp,
                            fontWeight = if (isLatest) FontWeight.Bold else FontWeight.SemiBold,
                            color = TextMain,
                            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                        )
                        if (part.oversSuffix.isNotEmpty()) {
                            Text(
                                text = part.oversSuffix,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamCardRow(
    name: String,
    score: String,
    logoUrl: String?,
    floatingPillType: LiveEventType? = null,
    floatingPillId: Long? = null,
    shakeTriggerId: Long? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wicketShake(shakeTriggerId),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, Color(0x0D000000), CircleShape)
            ) {
                if (logoUrl != null) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val parsed = LiveMatchEventDetector.parseTeamScore(score)
        if (parsed.isYetToBat) {
            Text(
                text = "Yet to bat",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = TextSecondary
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    RollingValueTransition(targetValue = parsed.currentScore) { currentPart ->
                        Text(
                            text = currentPart,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMain,
                            maxLines = 1,
                            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                        )
                    }

                    FloatingScoreEventPill(
                        eventType = floatingPillType,
                        triggerId = floatingPillId,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-21).dp)
                    )
                }

                if (parsed.oversSuffix.isNotEmpty()) {
                    Text(
                        text = parsed.oversSuffix,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        maxLines = 1,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                    )
                }
            }
        }
    }
}

// ─── LIVE TAB ITEMS ───────────────────────────────────────────────────

private fun LazyListScope.liveTabItems(
    details: MatchDetails,
    activeLiveEvent: LiveMatchEvent? = null,
    recentBallItems: List<RecentBallItem> = emptyList(),
    lastWicketPulseId: Long? = null
) {
    val crr = details.summary.liveScore?.runRate ?: ""
    val rrr = details.summary.liveScore?.requiredRunRate ?: ""
    val hasLiveData = details.currentBatsmen.isNotEmpty() ||
            details.currentBowlers.isNotEmpty() ||
            details.commentary.isNotEmpty() ||
            details.recentBalls.isNotBlank() ||
            details.lastWicket.isNotBlank() ||
            crr.isNotBlank()

    if (!hasLiveData) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Live match updates will appear here when the match begins",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    // Top summary (CRR, RRR, Partnership)
    if (crr.isNotBlank() || rrr.isNotBlank() || details.partnership.isNotBlank()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 12.dp)
                    .background(CardHeaderBg, RoundedCornerShape(12.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (crr.isNotBlank()) SummaryStat("CRR:", crr)
                if (rrr.isNotBlank()) SummaryStat("RRR:", rrr)
                if (details.partnership.isNotBlank()) SummaryStat("Partnership:", details.partnership)
            }
        }
    }

    // Batting Card
    val wicketBatterKeys = activeLiveEvent?.batterEvents
        ?.filterValues { it == LiveEventType.WICKET }
        ?.keys
        .orEmpty()
    val dismissedTransient = if (wicketBatterKeys.isNotEmpty()) {
        activeLiveEvent?.transientDismissedBatter?.takeIf { outBat ->
            val outNorm = LiveMatchEventDetector.normalizeName(outBat.name)
            details.currentBatsmen.none { LiveMatchEventDetector.normalizeName(it.name) == outNorm }
        }
    } else null

    if (details.currentBatsmen.isNotEmpty() || dismissedTransient != null) {
        item {
            SectionCard(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    "Batting",
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextMain
                )
                HorizontalDivider(color = BorderLight)

                val batterRows = buildList {
                    val replaceIdx = if (dismissedTransient != null) {
                        val incomingName = activeLiveEvent?.replacedBatterName
                        val matchedIncoming = if (incomingName != null) {
                            details.currentBatsmen.indexOfFirst {
                                LiveMatchEventDetector.normalizeName(it.name) == incomingName
                            }
                        } else -1

                        when {
                            matchedIncoming >= 0 -> matchedIncoming
                            else -> details.currentBatsmen.indexOfFirst { it.runs == 0 && it.balls == 0 }
                                .takeIf { it >= 0 }
                                ?: if (details.currentBatsmen.size >= 2) details.currentBatsmen.lastIndex else -1
                        }
                    } else -1

                    details.currentBatsmen.forEachIndexed { index, bat ->
                        if (index == replaceIdx && dismissedTransient != null) {
                            add(dismissedTransient.copy(onStrike = false) to LiveEventType.WICKET)
                        } else {
                            val normalizedName = LiveMatchEventDetector.normalizeName(bat.name)
                            val eventType = activeLiveEvent?.batterEvents?.get(normalizedName)
                            add(bat to eventType)
                        }
                    }

                    if (dismissedTransient != null && replaceIdx < 0) {
                        add(dismissedTransient.copy(onStrike = false) to LiveEventType.WICKET)
                    }
                }

                batterRows.forEachIndexed { index, (bat, batterEventType) ->
                    key(LiveMatchEventDetector.normalizeName(bat.name)) {
                        LiveBatterRow(
                            bat = bat,
                            isLast = index == batterRows.lastIndex,
                            eventType = batterEventType,
                            eventId = if (batterEventType != null) activeLiveEvent?.id else null
                        )
                    }
                }
            }
        }
    }

    // Bowling Card
    if (details.currentBowlers.isNotEmpty()) {
        item {
            SectionCard(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    "Bowling",
                    modifier = Modifier.padding(16.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextMain
                )
                HorizontalDivider(color = BorderLight)

                details.currentBowlers.forEachIndexed { index, bowl ->
                    key(LiveMatchEventDetector.normalizeName(bowl.name)) {
                        val normalizedName = LiveMatchEventDetector.normalizeName(bowl.name)
                        val bowlerEventType = activeLiveEvent?.bowlerEvents?.get(normalizedName)
                        LiveBowlerRow(
                            bowl = bowl,
                            isLast = index == details.currentBowlers.lastIndex,
                            eventType = bowlerEventType,
                            eventId = if (bowlerEventType != null) activeLiveEvent?.id else null
                        )
                    }
                }
            }
        }
    }

    // Key Stats (Bubbles)
    val hasKeyStats = details.latestPerformance.isNotEmpty() || details.oversLeft.isNotBlank()
    if (hasKeyStats) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (details.oversLeft.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .background(CardHeaderBg, RoundedCornerShape(16.dp))
                            .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "Ovs Left: ${details.oversLeft}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMain
                        )
                    }
                }

                if (details.latestPerformance.isNotEmpty()) {
                    var perfIndex by remember { mutableIntStateOf(0) }
                    val perf = details.latestPerformance[perfIndex % details.latestPerformance.size]

                    Box(
                        modifier = Modifier
                            .background(CardHeaderBg, RoundedCornerShape(16.dp))
                            .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                            .pressable(
                                onClick = { perfIndex++ },
                                pressedScale = MotionTokens.ChipPressScale
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "${perf.label}: ${perf.runs} runs, ${perf.wkts} wkts",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMain
                        )
                    }
                }
            }
        }
    }

    // Recent Balls: Latest on left side with unique item keys so new balls pop in & older balls slide right
    val displayBalls = if (recentBallItems.isNotEmpty()) {
        recentBallItems
    } else if (details.recentBalls.isNotBlank()) {
        LiveMatchEventDetector.parseBalls(details.recentBalls)
            .reversed()
            .mapIndexed { idx, token -> RecentBallItem(id = -(idx + 1L), label = token, isNew = false) }
    } else emptyList()

    if (displayBalls.isNotEmpty()) {
        item {
            SectionCard(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recent (Last ${displayBalls.size} balls)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextMain
                        )
                        Text(
                            "Latest ←",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val recentRowState = rememberLazyListState()
                    val newestBall = displayBalls.firstOrNull()
                    LaunchedEffect(newestBall?.id) {
                        if (newestBall?.isNew == true) {
                            recentRowState.animateScrollToItem(0)
                        }
                    }
                    LazyRow(
                        state = recentRowState,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(displayBalls, key = { it.id }) { item ->
                            val ball = item.label
                            val isW = LiveMatchEventDetector.isWicketToken(ball)
                            val isFour = LiveMatchEventDetector.isFourToken(ball)
                            val isSix = LiveMatchEventDetector.isSixToken(ball)
                            val isNb = LiveMatchEventDetector.isNoBallToken(ball)

                            val bgColor = when {
                                isW -> BallWicket
                                isFour -> BallFour
                                isSix -> BallSix
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                            val colored = isW || isFour || isSix
                            val textColor = if (colored) Color.White else TextMain
                            val borderColor = if (colored) bgColor else BorderLight
                            val pulseColor = when {
                                isW -> BallWicket
                                isFour -> BallFour
                                isSix -> BallSix
                                isNb -> Color(0xFFF59E0B)
                                else -> null
                            }

                            PopInBallWrapper(
                                isNew = item.isNew,
                                ballKey = item.id,
                                pulseColor = pulseColor,
                                modifier = Modifier.animateItem()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(bgColor)
                                        .border(1.dp, borderColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ball,
                                        color = textColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Last Wicket (Redesigned sleek card with red wicket badge)
    if (details.lastWicket.isNotBlank()) {
        item {
            LastWicketCard(
                lastWicketText = details.lastWicket,
                pulseId = lastWicketPulseId,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp)
            )
        }
    }

    // Commentary
    if (details.commentary.isNotEmpty()) {
        item {
            SectionTitle(
                "Commentary",
                modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp)
            )
        }
        items(details.commentary) { comm ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = comm.ball,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.width(40.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (comm.event.isNotBlank()) {
                        val eventUpper = comm.event.uppercase().trim()
                        val isWicket = eventUpper.contains("WICKET") || eventUpper == "W" || eventUpper.contains("OUT")
                        val isFour = eventUpper.contains("FOUR") || eventUpper == "4"
                        val isSix = eventUpper.contains("SIX") || eventUpper == "6"

                        val (pillBg, pillTextColor) = when {
                            isWicket -> BallWicket.copy(alpha = 0.16f) to BallWicket
                            isFour -> BallFour.copy(alpha = 0.16f) to Color(0xFF60A5FA)
                            isSix -> BallSix.copy(alpha = 0.18f) to Color(0xFFC084FC)
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest to TextSecondary
                        }

                        Box(
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .background(pillBg, RoundedCornerShape(12.dp))
                                .border(
                                    1.dp,
                                    if (isWicket || isFour || isSix) pillTextColor.copy(alpha = 0.35f) else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = comm.event.uppercase(),
                                fontSize = 11.sp,
                                color = pillTextColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val parts = comm.text.split(",", limit = 2)
                    val softTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
                    val descriptionColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)

                    if (parts.size > 1 && parts[0].contains(" to ")) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = softTextColor)) {
                                    append(parts[0])
                                }
                                append(",")
                                withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = descriptionColor)) {
                                    append(parts[1])
                                }
                            },
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    } else {
                        Text(
                            text = comm.text,
                            color = softTextColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }
    }
}

// ─── REDESIGNED LAST WICKET CARD ──────────────────────────────────────

@Composable
private fun LastWicketCard(
    lastWicketText: String,
    pulseId: Long? = null,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.large
    val animatedBorder = rememberLastWicketBorderColor(
        triggerId = pulseId,
        baseColor = BorderLight
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardBlue)
            .border(1.dp, animatedBorder, shape)
    ) {
        LastWicketFlashOverlay(
            triggerId = pulseId,
            modifier = Modifier.matchParentSize()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Red wicket badge with spring pop & ripple ring
            LastWicketBadgeWrapper(triggerId = pulseId) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AccentRed.copy(alpha = 0.15f))
                        .border(1.dp, AccentRed.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "W",
                        color = AccentRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LAST WICKET",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = AccentRed
                )
                Spacer(modifier = Modifier.height(2.dp))
                AnimatedContent(
                    targetState = lastWicketText,
                    transitionSpec = {
                        (slideInVertically(animationSpec = tween(460)) { it / 2 } + fadeIn(tween(380)))
                            .togetherWith(slideOutVertically(animationSpec = tween(260)) { -it / 2 } + fadeOut(tween(220)))
                    },
                    label = "lastWicketText"
                ) { wicketText ->
                    Text(
                        text = wicketText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 18.sp
                        ),
                        color = TextMain
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = TextSecondary)) { append(label) }
            append(" ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum", color = TextMain)) { append(value) }
        },
        fontSize = 13.sp
    )
}

@Composable
private fun LiveBatterRow(
    bat: BatsmanLive,
    isLast: Boolean,
    eventType: LiveEventType? = null,
    eventId: Long? = null
) {
    val shakeId = if (eventType == LiveEventType.WICKET) eventId else null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        when (eventType) {
            LiveEventType.FOUR -> KineticGlyphSweepOverlay(
                glyph = "4",
                accentColor = BallFour,
                triggerId = eventId,
                modifier = Modifier.matchParentSize()
            )
            LiveEventType.SIX -> KineticGlyphSweepOverlay(
                glyph = "6",
                accentColor = BallSix,
                triggerId = eventId,
                modifier = Modifier.matchParentSize()
            )
            LiveEventType.WICKET -> IconScoutWicketOverlay(
                triggerId = eventId,
                modifier = Modifier.matchParentSize()
            )
            LiveEventType.FIFTY, LiveEventType.CENTURY -> MilestoneRaisedBatOverlay(
                eventType = eventType,
                triggerId = eventId,
                modifier = Modifier.matchParentSize()
            )
            LiveEventType.NO_BALL, null -> Unit
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wicketShake(shakeId)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Neutral text for non-active batsman, green for active, red if just out
                val nameColor = when {
                    bat.onStrike -> ActiveBatterGreen
                    eventType == LiveEventType.WICKET -> AccentRed
                    else -> TextMain
                }
                Text(
                    text = bat.name + if (bat.onStrike) " *" else "",
                    color = nameColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RollingValueTransition(targetValue = bat.runs.toString()) { runsText ->
                        Text(
                            text = runsText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextMain,
                            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                        )
                    }
                    Text(
                        text = " (${bat.balls})",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RollingValueTransition(
                    targetValue = bat.fours.toString(),
                    modifier = Modifier.weight(1f)
                ) { foursText ->
                    Text("4s: $foursText", fontSize = 13.sp, color = TextSecondary, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
                }
                RollingValueTransition(
                    targetValue = bat.sixes.toString(),
                    modifier = Modifier.weight(1f)
                ) { sixesText ->
                    Text("6s: $sixesText", fontSize = 13.sp, color = TextSecondary, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
                }
                RollingValueTransition(
                    targetValue = bat.strikeRate.toString(),
                    modifier = Modifier.weight(1f)
                ) { srText ->
                    Text("SR: $srText", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
                }
            }
        }
    }
    if (!isLast) HorizontalDivider(color = BorderLight, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun LiveBowlerRow(
    bowl: BowlerLive,
    isLast: Boolean,
    eventType: LiveEventType? = null,
    eventId: Long? = null
) {
    val isWicket = eventType == LiveEventType.WICKET
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        if (isWicket) {
            KineticGlyphSweepOverlay(
                glyph = "W",
                accentColor = AccentRed,
                triggerId = eventId,
                modifier = Modifier.matchParentSize()
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bowler name in neutral TextMain
                Text(
                    text = bowl.name,
                    color = TextMain,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                RollingValueTransition(targetValue = "${bowl.wickets}|${bowl.runs}") { value ->
                    val wicketsPart = value.substringBefore("|")
                    val runsPart = value.substringAfter("|")
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFeatureSettings = "tnum", color = TextMain)) {
                                append(wicketsPart)
                            }
                            withStyle(SpanStyle(color = TextSecondary, fontSize = 13.sp, fontFeatureSettings = "tnum")) {
                                append("-$runsPart")
                            }
                        }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RollingValueTransition(
                    targetValue = bowl.overs,
                    modifier = Modifier.weight(1f)
                ) { oversText ->
                    Text("O: $oversText", fontSize = 13.sp, color = TextSecondary, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
                }
                RollingValueTransition(
                    targetValue = bowl.maidens.toString(),
                    modifier = Modifier.weight(1f)
                ) { maidensText ->
                    Text("M: $maidensText", fontSize = 13.sp, color = TextSecondary, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
                }
                RollingValueTransition(
                    targetValue = bowl.economy.toString(),
                    modifier = Modifier.weight(1f)
                ) { ecoText ->
                    Text("ECO: $ecoText", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
                }
            }
        }
    }
    if (!isLast) HorizontalDivider(color = BorderLight, modifier = Modifier.padding(horizontal = 16.dp))
}

// ─── SCORECARD TAB ITEMS ──────────────────────────────────────────────

private fun inningLabel(index: Int): String {
    val n = index + 1
    val suffix = when (n) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix Inn"
}

@Composable
private fun HeaderCell(text: String, width: Dp, bold: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        textAlign = TextAlign.End,
        fontSize = 11.sp,
        maxLines = 1,
        softWrap = false,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        color = TextSecondary
    )
}

@Composable
private fun StatCell(text: String, width: Dp, strong: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        textAlign = TextAlign.End,
        fontSize = 13.sp,
        maxLines = 1,
        softWrap = false,
        fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        color = if (strong) TextMain else TextSecondary
    )
}

private fun LazyListScope.scorecardTabItems(
    scorecard: Scorecard?,
    state: com.synq.features.sports.domain.model.MatchState,
    selectedInning: Int,
    onInningSelected: (Int) -> Unit
) {
    if (scorecard == null || scorecard.innings.isEmpty()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                val emptyMessage = if (state == com.synq.features.sports.domain.model.MatchState.UPCOMING) {
                    "Scorecard yet to take place"
                } else {
                    "Scorecard unavailable"
                }
                Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    // Innings switcher
    if (scorecard.innings.size > 1) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(3.dp)
            ) {
                scorecard.innings.forEachIndexed { index, _ ->
                    val selected = selectedInning == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .pressable(
                                onClick = { onInningSelected(index) },
                                pressedScale = MotionTokens.ChipPressScale
                            )
                            .clip(CircleShape)
                            .background(if (selected) SportsAccent else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = inningLabel(index),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) OnSportsAccent else TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }

    if (scorecard.innings.isNotEmpty()) {
        val safeInningIndex = selectedInning.coerceIn(0, scorecard.innings.lastIndex)
        val inning = scorecard.innings[safeInningIndex]

        // Batting & extras card
        item {
            SectionCard(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp)
            ) {
                CardHeader {
                    CardHeaderTitle(inning.battingTeam)
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontFeatureSettings = "tnum")) {
                                append(inning.score)
                            }
                            append(" ")
                            withStyle(
                                SpanStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFeatureSettings = "tnum",
                                    color = TextSecondary
                                )
                            ) {
                                append("(${inning.overs})")
                            }
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = TextMain
                    )
                }

                // Batters header
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Batter", modifier = Modifier.weight(1f), fontSize = 12.sp, color = TextSecondary)
                    HeaderCell("R", ColR)
                    HeaderCell("B", ColB)
                    HeaderCell("4s", Col4s)
                    HeaderCell("6s", Col6s)
                    HeaderCell("SR", ColSR)
                }
                HorizontalDivider(color = BorderLight)

                inning.batting.forEach { bat ->
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(bat.name, fontWeight = FontWeight.Normal, color = TextMain, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(bat.status, fontSize = 11.sp, color = TextSecondary)
                        }
                        StatCell(bat.runs.toString(), ColR, strong = true)
                        StatCell(bat.balls.toString(), ColB)
                        StatCell(bat.fours.toString(), Col4s)
                        StatCell(bat.sixes.toString(), Col6s)
                        StatCell(bat.strikeRate.toString(), ColSR)
                    }
                    HorizontalDivider(color = BorderLight)
                }

                // Extras
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SubHeaderBg)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Extras", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextMain)
                    Text(
                        inning.extras,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextMain,
                        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                    )
                }
            }
        }

        // Fall of Wickets card (only when the data is available)
        if (inning.fallOfWickets.isNotEmpty()) {
            item {
                SectionCard(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        "Fall of Wickets",
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardHeaderBg)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = TextMain
                    )
                    HorizontalDivider(color = BorderLight)

                    inning.fallOfWickets.forEachIndexed { index, fow ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = buildAnnotatedString {
                                    val score = fow.scoreAtWicket.trim().ifBlank { "0" }
                                    withStyle(
                                        SpanStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontFeatureSettings = "tnum",
                                            color = TextMain
                                        )
                                    ) {
                                        append("$score-${index + 1}")
                                    }
                                    withStyle(SpanStyle(color = TextSecondary)) {
                                        append("  ${fow.player}")
                                    }
                                },
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${fow.over} ov",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")
                            )
                        }
                        if (index < inning.fallOfWickets.lastIndex) HorizontalDivider(color = BorderLight)
                    }
                }
            }
        }

        // Bowling card
        item {
            SectionCard(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                val bowlingTeam = scorecard.innings
                    .map { it.battingTeam }
                    .distinct()
                    .firstOrNull { it != inning.battingTeam }
                if (bowlingTeam != null) {
                    CardHeader { CardHeaderTitle(bowlingTeam) }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Bowler",
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    HeaderCell("O", ColO, bold = true)
                    HeaderCell("M", ColM, bold = true)
                    HeaderCell("R", ColRuns, bold = true)
                    HeaderCell("W", ColW, bold = true)
                    HeaderCell("ECO", ColEco, bold = true)
                }
                HorizontalDivider(color = BorderLight)

                inning.bowling.forEachIndexed { index, bowl ->
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            bowl.name,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            fontWeight = FontWeight.Normal,
                            color = TextMain,
                            fontSize = 13.sp
                        )
                        StatCell(bowl.overs, ColO)
                        StatCell(bowl.maidens.toString(), ColM)
                        StatCell(bowl.runs.toString(), ColRuns)
                        StatCell(bowl.wickets.toString(), ColW, strong = true)
                        StatCell(bowl.economy.toString(), ColEco)
                    }
                    if (index < inning.bowling.lastIndex) HorizontalDivider(color = BorderLight)
                }
            }
        }
    }
}

// ─── SQUADS TAB ITEMS ─────────────────────────────────────────────────

private fun LazyListScope.squadsTabItems(
    squads: Squad?,
    state: com.synq.features.sports.domain.model.MatchState
) {
    if (squads == null || squads.teams.isEmpty()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                val emptyMessage = if (state == com.synq.features.sports.domain.model.MatchState.UPCOMING) {
                    "Squads yet to be announced"
                } else {
                    "Squads unavailable"
                }
                Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    items(squads.teams.entries.toList(), key = { it.key }) { entry ->
        val teamName = entry.key
        val teamSquad = entry.value

        SectionCard(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            CardHeader { CardHeaderTitle(teamName) }

            SquadSubHeader("PLAYING XI")
            teamSquad.playingXI.forEachIndexed { index, player ->
                PlayerRow(
                    player = player,
                    isBench = false,
                    isLast = teamSquad.bench.isEmpty() && index == teamSquad.playingXI.lastIndex
                )
            }

            if (teamSquad.bench.isNotEmpty()) {
                SquadSubHeader("BENCH")
                teamSquad.bench.forEachIndexed { index, player ->
                    PlayerRow(
                        player = player,
                        isBench = true,
                        isLast = index == teamSquad.bench.lastIndex
                    )
                }
            }
        }
    }
}

@Composable
private fun SquadSubHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(SubHeaderBg)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = TextSecondary
    )
    HorizontalDivider(color = BorderLight)
}

@Composable
private fun PlayerRow(player: PlayerRef, isBench: Boolean, isLast: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (player.imageUrl != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AvatarBg)
            ) {
                AsyncImage(
                    model = player.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            PlayerAvatar(iconTint = if (isBench) Color(0xFFDDDDDD) else TextSecondary)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            val displayName = buildString {
                append(player.name)
                if (player.isCaptain) append(" (c)")
                if (player.isKeeper) append(" (wk)")
            }
            Text(displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextMain)
            Spacer(modifier = Modifier.height(2.dp))
            Text(player.role, fontSize = 12.sp, color = TextSecondary)
        }
    }
    if (!isLast) HorizontalDivider(color = BorderLight)
}
