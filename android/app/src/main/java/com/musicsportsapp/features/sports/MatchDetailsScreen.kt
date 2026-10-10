package com.musicsportsapp.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import com.musicsportsapp.features.sports.domain.model.*

// ─── DESIGN TOKENS (from the HTML :root + inline styles) ──────────────

private val TextMain: Color @Composable get() = MaterialTheme.colorScheme.onBackground
private val TextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val AccentRed = Color(0xFFDE474E)
private val AccentBlue = Color(0xFF5566FF)
private val CardBlue: Color @Composable get() = MaterialTheme.colorScheme.surface
private val CardHeaderBg: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val SubHeaderBg: Color @Composable get() = MaterialTheme.colorScheme.background
private val BorderLight: Color @Composable get() = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)
private val AvatarBg: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant

// Content area padding
private val ContentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 100.dp)

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
    viewModel: MatchDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Tab State
    val tabs = listOf("Info", "Live", "Scorecard", "Squads")
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background
        )
    )

    // Material3's default text style adds lineHeight = 24.sp and letterSpacing = 0.5.sp to every Text,
    // which is what made everything look loose and wide. Reset both for this whole screen.
    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(
            lineHeight = TextUnit.Unspecified,
            letterSpacing = 0.sp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            if (uiState.isLoading && uiState.details == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF4338CA))
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
                    // Header
                    CustomHeader(
                        title = "${details.summary.team1.shortName} vs ${details.summary.team2.shortName}",
                        subtitle = details.summary.seriesName,
                        onBackClick = onBackClick
                    )

                    // Match Card
                    MatchCard(summary = details.summary)

                    // Main Tabs (pills)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tabs.size) { index ->
                            val isSelected = selectedTabIndex == index
                            val pill = RoundedCornerShape(20.dp)
                            Box(
                                modifier = Modifier
                                    .then(
                                        if (isSelected) Modifier.shadow(
                                            elevation = 4.dp,
                                            shape = pill,
                                            ambientColor = Color(0x4D5566FF),
                                            spotColor = Color(0x4D5566FF)
                                        ) else Modifier
                                    )
                                    .clip(pill)
                                    .background(if (isSelected) AccentBlue else Color.Transparent)
                                    .border(1.dp, if (isSelected) AccentBlue else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), pill)
                                    .clickable { selectedTabIndex = index }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = tabs[index],
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Content Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                            )
                            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    ) {
                        when (selectedTabIndex) {
                            0 -> InfoTab(details = details)
                            1 -> LiveTab(details = details)
                            2 -> ScorecardTab(scorecard = uiState.scorecard, state = details.summary.state)
                            3 -> SquadsTab(squads = uiState.squads, state = details.summary.state)
                        }
                    }
                }
            }
        }
    }
}

// ─── SHARED BUILDING BLOCKS ───────────────────────────────────────────

/** White rounded card with 1px border + soft shadow (the inline-styled cards in the HTML). */
@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, shape, ambientColor = Color(0x05000000), spotColor = Color(0x05000000))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, BorderLight, shape),
        content = content
    )
}

/** Card header strip: #f8f9fe, padding 14/16, weight 600, bottom border. */
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
    Divider(color = BorderLight)
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
        modifier = modifier.padding(bottom = 16.dp)
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

// ─── INFO TAB ─────────────────────────────────────────────────────────

@Composable
private fun InfoTab(details: MatchDetails) {
    LazyColumn(contentPadding = ContentPadding) {
        item {
            SectionCard(modifier = Modifier.padding(bottom = 24.dp)) {
                CardHeader { CardHeaderTitle("Match Details") }
                
                val titleStr = buildString {
                    append(details.summary.team1.shortName)
                    append(" vs ")
                    append(details.summary.team2.shortName)
                    if (details.summary.matchDesc.isNotBlank()) append(" • ${details.summary.matchDesc}")
                    if (details.summary.series.isNotBlank()) append(" • ${details.summary.series}")
                }
                InfoRow("Match", titleStr, emphasized = true)
                InfoRow("Series", details.summary.series)
                if (details.summary.venue.isNotBlank()) {
                    InfoRow("Venue", details.summary.venue)
                }
                
                if (details.summary.startTime > 0L) {
                    val dateStr = java.text.SimpleDateFormat("MMM dd, yyyy • hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(details.summary.startTime))
                    InfoRow("Date & Time", dateStr)
                }
                
                InfoRow("Toss", if (details.toss.isNotBlank()) details.toss else "-", isLast = true)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, emphasized: Boolean = false, isLast: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, modifier = Modifier.width(100.dp), color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(
            value,
            modifier = Modifier.weight(1f),
            color = TextMain,
            fontSize = if (emphasized) 14.sp else 13.sp,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium
        )
    }
    if (!isLast) Divider(color = BorderLight)
}

// ─── HEADER ───────────────────────────────────────────────────────────

@Composable
private fun CustomHeader(title: String, subtitle: String, onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextMain, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

// ─── MATCH CARD ───────────────────────────────────────────────────────

@Composable
private fun MatchCard(summary: MatchSummary) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .shadow(8.dp, shape, ambientColor = Color(0x0D5566FF), spotColor = Color(0x0D5566FF))
            .clip(shape)
            .background(CardBlue)
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
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = summary.format.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Team 1
        TeamCardRow(name = summary.team1.shortName, score = summary.team1Score ?: "", logoUrl = summary.team1.logoUrl)
        Spacer(modifier = Modifier.height(8.dp))
        // Team 2
        TeamCardRow(name = summary.team2.shortName, score = summary.team2Score ?: "", logoUrl = summary.team2.logoUrl)

        // Status
        if (summary.status.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (summary.isLive) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(AccentRed, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = summary.status,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (summary.isLive) AccentRed else TextMain
                )
            }
        }
    }
}

@Composable
private fun TeamCardRow(name: String, score: String, logoUrl: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
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

        if (score.isNotBlank()) {
            val parts = score.split("(")
            val runsText = parts[0].trim()
            val oversText = if (parts.size > 1) "(${parts[1]}" else ""

            // Score and overs sit on ONE line in the prototype: "174/10 (72.6)"
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextMain)) {
                        append(runsText)
                    }
                    if (oversText.isNotEmpty()) {
                        append(" ")
                        withStyle(SpanStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary)) {
                            append(oversText)
                        }
                    }
                },
                textAlign = TextAlign.End
            )
        } else {
            Text("Yet to bat", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
        }
    }
}

@Composable
private fun TeamLogo(url: String?) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(4.dp)
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Inside,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ─── LIVE TAB ─────────────────────────────────────────────────────────

@Composable
private fun LiveTab(details: MatchDetails) {
    LazyColumn(contentPadding = ContentPadding) {
        // Top summary (CRR, RRR, Partnership)
        val crr = details.summary.liveScore?.runRate ?: ""
        val rrr = details.summary.liveScore?.requiredRunRate ?: ""
        val partnership = details.partnership

        if (crr.isNotBlank() || rrr.isNotBlank() || details.partnership.isNotBlank()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .background(CardHeaderBg, RoundedCornerShape(12.dp))
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

        if (details.currentBatsmen.isNotEmpty()) {
            item {
                SectionCard(modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(
                        "Batting",
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextMain
                    )
                    Divider(color = BorderLight)

                    details.currentBatsmen.forEachIndexed { index, bat ->
                        LiveBatterRow(
                            bat = bat,
                            isLast = index == details.currentBatsmen.lastIndex
                        )
                    }
                }
            }
        }

        if (details.currentBowlers.isNotEmpty()) {
            item {
                SectionCard(modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(
                        "Bowling",
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextMain
                    )
                    Divider(color = BorderLight)

                    details.currentBowlers.forEachIndexed { index, bowl ->
                        LiveBowlerRow(
                            bowl = bowl,
                            isLast = index == details.currentBowlers.lastIndex
                        )
                    }
                }
            }
        }

        // Key Stats (Bubbles)
        val hasKeyStats = details.latestPerformance.isNotEmpty() || details.oversLeft.isNotBlank()
                          
        if (hasKeyStats) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (details.oversLeft.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .background(CardHeaderBg, RoundedCornerShape(16.dp))
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
                        var perfIndex by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
                        val perf = details.latestPerformance[perfIndex % details.latestPerformance.size]
                        
                        Box(
                            modifier = Modifier
                                .background(CardHeaderBg, RoundedCornerShape(16.dp))
                                .clickable { perfIndex++ }
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

        // Recent Balls
        val recentBallsStr = details.recentBalls
        val recentBalls = if (recentBallsStr.isNotBlank()) {
            recentBallsStr.split(" ", "|").map { it.trim() }.filter { it.isNotBlank() }
        } else emptyList()
        
        if (recentBalls.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .background(CardBlue, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        "Recent (Last ${recentBalls.size} balls)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextMain
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recentBalls) { ball ->
                            val bgColor = when (ball) {
                                "W", "Wicket" -> AccentRed
                                "4", "Four" -> AccentBlue
                                "6", "Six" -> Color(0xFF9B51E0)
                                else -> MaterialTheme.colorScheme.surface
                            }
                            val colored = ball == "W" || ball == "4" || ball == "6"
                            val textColor = if (colored) Color.White else TextMain
                            val borderColor = if (colored) bgColor else BorderLight

                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(bgColor, CircleShape)
                                    .border(1.dp, borderColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(ball, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        if (details.lastWicket.isNotBlank()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Last Wicket: ${details.lastWicket}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (details.commentary.isNotEmpty()) {
            item { SectionTitle("Commentary") }
            items(details.commentary) { comm ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = comm.ball,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextMain,
                        modifier = Modifier.width(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        if (comm.event.isNotBlank()) {
                            val isWicket = comm.event.equals("WICKET", ignoreCase = true)
                            val isBoundary = comm.event.equals("FOUR", ignoreCase = true) || comm.event.equals("SIX", ignoreCase = true)
                            val bgColor = when {
                                isWicket -> Color(0x1ADE474E)
                                isBoundary -> Color(0x1A5566FF)
                                else -> Color(0xFFF3F4F6)
                            }
                            val textColor = when {
                                isWicket -> AccentRed
                                isBoundary -> AccentBlue
                                else -> Color(0xFF4B5563)
                            }

                            Box(
                                modifier = Modifier
                                    .padding(bottom = 4.dp)
                                    .background(bgColor, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(comm.event.uppercase(), fontSize = 11.sp, color = textColor, fontWeight = FontWeight.Bold)
                            }
                        }

                        val parts = comm.text.split(",", limit = 2)
                        if (parts.size > 1 && parts[0].contains(" to ")) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                        append(parts[0])
                                    }
                                    append(",")
                                    append(parts[1])
                                },
                                color = TextMain,
                                fontSize = 14.sp,
                                lineHeight = 21.sp
                            )
                        } else {
                            Text(comm.text, color = TextMain, fontSize = 14.sp, lineHeight = 21.sp)
                        }
                    }
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
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextMain)) { append(value) }
        },
        fontSize = 13.sp
    )
}

@Composable
private fun LiveBatterRow(bat: BatsmanLive, isLast: Boolean) {
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
            Text(
                text = bat.name + if (bat.onStrike) " *" else "",
                color = if (bat.onStrike) Color(0xFF00B050) else AccentBlue,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextMain)) { append(bat.runs.toString()) }
                    withStyle(SpanStyle(color = TextSecondary, fontSize = 13.sp)) { append(" (${bat.balls})") }
                }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("4s: ${bat.fours}", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            Text("6s: ${bat.sixes}", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            Text("SR: ${bat.strikeRate}", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
    if (!isLast) Divider(color = BorderLight, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun LiveBowlerRow(bowl: BowlerLive, isLast: Boolean) {
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
            Text(
                text = bowl.name,
                color = AccentBlue,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextMain)) { append(bowl.wickets.toString()) }
                    withStyle(SpanStyle(color = TextSecondary, fontSize = 13.sp)) { append("-${bowl.runs}") }
                }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("O: ${bowl.overs}", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            Text("M: ${bowl.maidens}", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            Text("ECO: ${bowl.economy}", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
    if (!isLast) Divider(color = BorderLight, modifier = Modifier.padding(horizontal = 16.dp))
}

// ─── SCORECARD TAB ────────────────────────────────────────────────────

private fun inningLabel(index: Int): String {
    val n = index + 1
    val suffix = when (n) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix Inn"
}

@Composable
private fun HeaderCell(text: String, width: Dp, bold: Boolean = false) {
    Text(
        text,
        modifier = Modifier.width(width),
        textAlign = TextAlign.End,
        fontSize = 11.sp,
        maxLines = 1,
        softWrap = false,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = TextSecondary
    )
}

@Composable
private fun StatCell(text: String, width: Dp, strong: Boolean = false) {
    Text(
        text,
        modifier = Modifier.width(width),
        textAlign = TextAlign.End,
        fontSize = 13.sp,
        maxLines = 1,
        softWrap = false,
        fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
        color = if (strong) TextMain else TextSecondary
    )
}

@Composable
private fun ScorecardTab(scorecard: Scorecard?, state: com.musicsportsapp.features.sports.domain.model.MatchState) {
    if (scorecard == null || scorecard.innings.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state == com.musicsportsapp.features.sports.domain.model.MatchState.UPCOMING) {
                Text("Scorecard yet to take place", color = Color.Gray)
            } else {
                Text("Scorecard unavailable", color = Color.Gray)
            }
        }
        return
    }

    var selectedInning by remember { mutableIntStateOf(0) }

    LazyColumn(contentPadding = ContentPadding) {
        // Innings switcher
        if (scorecard.innings.size > 1) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                        .background(CardHeaderBg, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    val tabShape = RoundedCornerShape(8.dp)
                    scorecard.innings.forEachIndexed { index, _ ->
                        val selected = selectedInning == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (selected) Modifier.shadow(
                                        2.dp, tabShape,
                                        ambientColor = Color(0x0D000000),
                                        spotColor = Color(0x0D000000)
                                    ) else Modifier
                                )
                                .clip(tabShape)
                                .background(if (selected) Color.White else Color.Transparent)
                                .clickable { selectedInning = index }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                inningLabel(index),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) AccentBlue else TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        if (scorecard.innings.isNotEmpty()) {
            val inning = scorecard.innings[selectedInning]

            // Batting & extras card
            item {
                SectionCard(modifier = Modifier.padding(bottom = 16.dp)) {
                    CardHeader {
                        CardHeaderTitle(inning.battingTeam)
                        Text(
                            text = buildAnnotatedString {
                                append(inning.score)
                                append(" ")
                                withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)) {
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
                    Divider(color = BorderLight)

                    inning.batting.forEach { bat ->
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(bat.name, fontWeight = FontWeight.Medium, color = TextMain, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(bat.status, fontSize = 11.sp, color = TextSecondary)
                            }
                            StatCell(bat.runs.toString(), ColR, strong = true)
                            StatCell(bat.balls.toString(), ColB)
                            StatCell(bat.fours.toString(), Col4s)
                            StatCell(bat.sixes.toString(), Col6s)
                            StatCell(bat.strikeRate.toString(), ColSR)
                        }
                        Divider(color = BorderLight)
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
                        Text(inning.extras, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextMain)
                    }
                }
            }

            // Fall of Wickets card (only when the data is available)
            if (inning.fallOfWickets.isNotEmpty()) {
                item {
                    SectionCard(modifier = Modifier.padding(bottom = 16.dp)) {
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
                        Divider(color = BorderLight)

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
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextMain)) {
                                            append("${fow.scoreAtWicket}-${index + 1}")
                                        }
                                        withStyle(SpanStyle(color = TextSecondary)) {
                                            append("  ${fow.player}")
                                        }
                                    },
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${fow.over} ov", fontSize = 13.sp, color = TextSecondary)
                            }
                            if (index < inning.fallOfWickets.lastIndex) Divider(color = BorderLight)
                        }
                    }
                }
            }

            // Bowling card
            item {
                SectionCard(modifier = Modifier.padding(bottom = 24.dp)) {
                    // Bowling team = the other team in this scorecard
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
                    Divider(color = BorderLight)

                    inning.bowling.forEachIndexed { index, bowl ->
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                bowl.name,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                fontWeight = FontWeight.Medium,
                                color = TextMain,
                                fontSize = 13.sp
                            )
                            StatCell(bowl.overs, ColO)
                            StatCell(bowl.maidens.toString(), ColM)
                            StatCell(bowl.runs.toString(), ColRuns)
                            StatCell(bowl.wickets.toString(), ColW, strong = true)
                            StatCell(bowl.economy.toString(), ColEco)
                        }
                        if (index < inning.bowling.lastIndex) Divider(color = BorderLight)
                    }
                }
            }
        }
    }
}

// ─── SQUADS TAB ───────────────────────────────────────────────────────

@Composable
private fun SquadsTab(squads: Squad?, state: com.musicsportsapp.features.sports.domain.model.MatchState) {
    if (squads == null || squads.teams.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state == com.musicsportsapp.features.sports.domain.model.MatchState.UPCOMING) {
                Text("Squads yet to be announced", color = Color.Gray)
            } else {
                Text("Squads unavailable", color = Color.Gray)
            }
        }
        return
    }

    // The prototype shows every team as its own stacked card (no team tab switcher)
    LazyColumn(contentPadding = ContentPadding) {
        items(squads.teams.entries.toList()) { entry ->
            val teamName = entry.key
            val teamSquad = entry.value

            SectionCard(modifier = Modifier.padding(bottom = 24.dp)) {
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
    Divider(color = BorderLight)
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
    if (!isLast) Divider(color = BorderLight)
}