package com.musicsportsapp.features.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.musicsportsapp.features.sports.domain.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailsScreen(
    onBackClick: () -> Unit,
    viewModel: MatchDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Tab State
    val tabs = listOf("Info", "Scorecard", "Squads")
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = uiState.details?.summary?.seriesName ?: "Match Details",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFDEFF2),
                    titleContentColor = Color(0xFF1A1D2D)
                )
            )
        },
        containerColor = Color(0xFFF8F9FE)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                    // Match Header
                    MatchHeader(summary = details.summary)
                    
                    // Tab Row
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.White,
                        contentColor = Color(0xFF4338CA),
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = Color(0xFF4338CA)
                            )
                        }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = { 
                                    Text(
                                        text = title, 
                                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selectedTabIndex == index) Color(0xFF4338CA) else Color(0xFF6B7280)
                                    ) 
                                }
                            )
                        }
                    }
                    
                    // Tab Content
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (selectedTabIndex) {
                            0 -> InfoTab(details = details)
                            1 -> ScorecardTab(scorecard = uiState.scorecard)
                            2 -> SquadsTab(squads = uiState.squads)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchHeader(summary: MatchSummary) {
    Surface(
        color = Color(0xFFFDEFF2),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team 1
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    TeamLogo(summary.team1.logoUrl)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(summary.team1.shortName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(summary.team1Score?.split("(")?.get(0)?.trim() ?: "", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineSmall)
                    val overs = summary.team1Score?.substringAfter("(", "")?.substringBefore(")") ?: ""
                    if (overs.isNotEmpty()) {
                        Text("($overs)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    }
                }
                
                Text("VS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color(0xFFD1D5DB))
                
                // Team 2
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    TeamLogo(summary.team2.logoUrl)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(summary.team2.shortName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(summary.team2Score?.split("(")?.get(0)?.trim() ?: "", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineSmall)
                    val overs = summary.team2Score?.substringAfter("(", "")?.substringBefore(")") ?: ""
                    if (overs.isNotEmpty()) {
                        Text("($overs)", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = summary.status,
                style = MaterialTheme.typography.labelLarge,
                color = if (summary.isLive) Color(0xFFEF4444) else Color(0xFF4338CA),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
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

// ─── TABS ─────────────────────────────────────────────────────────────

@Composable
private fun InfoTab(details: MatchDetails) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (details.currentBatsmen.isNotEmpty() || details.currentBowlers.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Live Action", fontWeight = FontWeight.Bold, color = Color(0xFF4338CA), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Batsmen
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Batsman", modifier = Modifier.weight(2f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Gray)
                            Text("R", modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Gray)
                            Text("B", modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Gray)
                            Text("4s", modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Gray)
                            Text("6s", modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Gray)
                            Text("SR", modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Gray)
                        }
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFF3F4F6))
                        details.currentBatsmen.forEach { bat ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(bat.name + if(bat.onStrike) "*" else "", modifier = Modifier.weight(2f), fontWeight = if(bat.onStrike) FontWeight.Bold else FontWeight.Normal, color = Color(0xFF1A1D2D))
                                Text(bat.runs.toString(), modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, color = Color(0xFF1A1D2D))
                                Text(bat.balls.toString(), modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, color = Color(0xFF4B5563))
                                Text(bat.fours.toString(), modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, color = Color(0xFF4B5563))
                                Text(bat.sixes.toString(), modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, color = Color(0xFF4B5563))
                                Text(bat.strikeRate.toString(), modifier = Modifier.weight(1f), textAlign = TextAlign.End, color = Color(0xFF4B5563))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Bowlers
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Bowler", modifier = Modifier.weight(2f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Gray)
                            Text("O", modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Gray)
                            Text("M", modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Gray)
                            Text("R", modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Gray)
                            Text("W", modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Gray)
                            Text("ECO", modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Gray)
                        }
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFF3F4F6))
                        details.currentBowlers.forEach { bowl ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(bowl.name, modifier = Modifier.weight(2f), fontWeight = FontWeight.Medium, color = Color(0xFF1A1D2D))
                                Text(bowl.overs, modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, color = Color(0xFF4B5563))
                                Text(bowl.maidens.toString(), modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, color = Color(0xFF4B5563))
                                Text(bowl.runs.toString(), modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, color = Color(0xFF4B5563))
                                Text(bowl.wickets.toString(), modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, color = Color(0xFF1A1D2D))
                                Text(bowl.economy.toString(), modifier = Modifier.weight(1f), textAlign = TextAlign.End, color = Color(0xFF4B5563))
                            }
                        }
                    }
                }
            }
        }
        
        if (details.commentary.isNotEmpty()) {
            item {
                Text("Commentary", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            items(details.commentary) { comm ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(
                        modifier = Modifier.width(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(comm.ball, fontWeight = FontWeight.Bold, color = Color(0xFF4338CA))
                        if (comm.event.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .background(Color(0xFFFEE2E2), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(comm.event, fontSize = 10.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(comm.text, color = Color(0xFF374151), style = MaterialTheme.typography.bodyMedium)
                }
                Divider(color = Color(0xFFF3F4F6))
            }
        }
    }
}

@Composable
private fun ScorecardTab(scorecard: Scorecard?) {
    if (scorecard == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Scorecard unavailable", color = Color.Gray)
        }
        return
    }
    
    var selectedInning by remember { mutableIntStateOf(0) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        if (scorecard.innings.size > 1) {
            TabRow(
                selectedTabIndex = selectedInning,
                containerColor = Color(0xFFF3F4F6),
                contentColor = Color(0xFF1A1D2D)
            ) {
                scorecard.innings.forEachIndexed { index, inning ->
                    Tab(
                        selected = selectedInning == index,
                        onClick = { selectedInning = index },
                        text = { Text(inning.battingTeam, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
        }
        
        if (scorecard.innings.isNotEmpty()) {
            val inning = scorecard.innings[selectedInning]
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${inning.battingTeam} Innings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("${inning.score} (${inning.overs})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Batsmen header
                    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFF9FAFB)).padding(8.dp)) {
                        Text("Batsman", modifier = Modifier.weight(2f), fontSize = 12.sp, color = Color.Gray)
                        Text("R", modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Gray)
                        Text("B", modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Gray)
                        Text("4s", modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Gray)
                        Text("6s", modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Gray)
                        Text("SR", modifier = Modifier.weight(0.8f), textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Gray)
                    }
                }
                
                items(inning.batting) { bat ->
                    Column {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Column(modifier = Modifier.weight(2f)) {
                                Text(bat.name, fontWeight = FontWeight.Medium, color = Color(0xFF1A1D2D), fontSize = 14.sp)
                                Text(bat.status, fontSize = 11.sp, color = Color.Gray, lineHeight = 12.sp)
                            }
                            Text(bat.runs.toString(), modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, color = Color(0xFF1A1D2D))
                            Text(bat.balls.toString(), modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, color = Color(0xFF4B5563))
                            Text(bat.fours.toString(), modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, color = Color(0xFF4B5563))
                            Text(bat.sixes.toString(), modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, color = Color(0xFF4B5563))
                            Text(bat.strikeRate.toString(), modifier = Modifier.weight(0.8f), textAlign = TextAlign.End, color = Color(0xFF4B5563))
                        }
                        Divider(color = Color(0xFFF3F4F6))
                    }
                }
                
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Extras", fontWeight = FontWeight.Medium)
                        Text(inning.extras, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", fontWeight = FontWeight.Bold)
                        Text("${inning.score} (${inning.overs} Overs)", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Bowlers header
                    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFF9FAFB)).padding(8.dp)) {
                        Text("Bowler", modifier = Modifier.weight(2f), fontSize = 12.sp, color = Color.Gray)
                        Text("O", modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Gray)
                        Text("M", modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Gray)
                        Text("R", modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Gray)
                        Text("W", modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Gray)
                        Text("ECO", modifier = Modifier.weight(0.8f), textAlign = TextAlign.End, fontSize = 12.sp, color = Color.Gray)
                    }
                }
                
                items(inning.bowling) { bowl ->
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                        Text(bowl.name, modifier = Modifier.weight(2f), fontWeight = FontWeight.Medium, color = Color(0xFF1A1D2D), fontSize = 14.sp)
                        Text(bowl.overs, modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, color = Color(0xFF4B5563))
                        Text(bowl.maidens.toString(), modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, color = Color(0xFF4B5563))
                        Text(bowl.runs.toString(), modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, color = Color(0xFF4B5563))
                        Text(bowl.wickets.toString(), modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, color = Color(0xFF1A1D2D))
                        Text(bowl.economy.toString(), modifier = Modifier.weight(0.8f), textAlign = TextAlign.End, color = Color(0xFF4B5563))
                    }
                    Divider(color = Color(0xFFF3F4F6))
                }
            }
        }
    }
}

@Composable
private fun SquadsTab(squads: Squad?) {
    if (squads == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Squads unavailable", color = Color.Gray)
        }
        return
    }
    
    val teamNames = squads.teams.keys.toList()
    if (teamNames.isEmpty()) return
    
    var selectedTeamIndex by remember { mutableIntStateOf(0) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTeamIndex,
            containerColor = Color(0xFFF3F4F6),
            contentColor = Color(0xFF1A1D2D)
        ) {
            teamNames.forEachIndexed { index, name ->
                Tab(
                    selected = selectedTeamIndex == index,
                    onClick = { selectedTeamIndex = index },
                    text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }
        
        val teamSquad = squads.teams[teamNames[selectedTeamIndex]] ?: return
        
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Text("Playing XI", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            }
            items(teamSquad.playingXI) { player ->
                PlayerRow(player)
            }
            
            if (teamSquad.bench.isNotEmpty()) {
                item {
                    Text("Bench", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                }
                items(teamSquad.bench) { player ->
                    PlayerRow(player)
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(player: PlayerRef) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFE5E7EB)),
            contentAlignment = Alignment.Center
        ) {
            if (player.imageUrl != null) {
                AsyncImage(
                    model = player.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(player.name.firstOrNull()?.toString() ?: "", color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(player.name, fontWeight = FontWeight.Medium, color = Color(0xFF1A1D2D))
                if (player.isCaptain) {
                    Text(" (c)", fontSize = 12.sp, color = Color(0xFF4338CA), fontWeight = FontWeight.Bold)
                }
                if (player.isKeeper) {
                    Text(" (wk)", fontSize = 12.sp, color = Color.Gray)
                }
            }
            Text(player.role, fontSize = 12.sp, color = Color(0xFF6B7280))
        }
    }
    Divider(color = Color(0xFFF3F4F6))
}
