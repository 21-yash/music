package com.synq.features.sports

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SportsCricket
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.synq.RootViewModel
import com.synq.features.sports.domain.model.MatchFormat
import com.synq.features.sports.domain.model.MatchSummary
import com.synq.ui.AppHeader
import com.synq.ui.motion.MotionTokens
import com.synq.ui.motion.pressable
import com.synq.ui.motion.shimmer
import com.synq.ui.theme.OnSportsAccent
import com.synq.ui.theme.SportsAccent

private val SemanticLive = Color(0xFFEF4444)

private val InternationalTeams = setOf(
    // ICC Full Members
    "india", "ind",
    "australia", "aus",
    "england", "eng",
    "south africa", "sa", "rsa",
    "pakistan", "pak",
    "new zealand", "nz",
    "sri lanka", "sl",
    "west indies", "wi", "windies",
    "bangladesh", "ban",
    "afghanistan", "afg",
    "zimbabwe", "zim",
    "ireland", "ire",

    // ICC Leading Associates & ODI/T20I teams
    "netherlands", "ned", "nl",
    "scotland", "sco",
    "namibia", "nam",
    "nepal", "nep",
    "united states", "usa", "us",
    "united arab emirates", "uae",
    "oman", "oma",
    "canada", "can",
    "papua new guinea", "png",
    "hong kong", "hk", "hkg",
    "uganda", "uga",
    "kenya", "ken",

    // Asian Associates & Women's teams
    "malaysia", "mas", "mly",
    "china", "chn",
    "thailand", "tha",
    "indonesia", "ina", "idn",
    "singapore", "sin", "sgp",
    "japan", "jpn",
    "kuwait", "kuw",
    "bahrain", "bhr",
    "qatar", "qat",
    "saudi arabia", "ksa",
    "maldives", "mdv",
    "bhutan", "bhu",
    "myanmar", "mya",
    "cambodia", "cam",
    "mongolia", "mgl",
    "south korea", "korea", "kor",
    "philippines", "phi",

    // Americas Associates (including Argentina!)
    "argentina", "arg",
    "brazil", "bra",
    "bermuda", "ber",
    "chile", "chi",
    "peru", "per",
    "mexico", "mex",
    "belize", "blz",
    "panama", "pan",
    "costa rica", "crc",
    "bahamas", "bah",
    "cayman islands", "cay",
    "suriname", "sur",

    // African Associates & Women's teams
    "rwanda", "rwa",
    "tanzania", "tan",
    "nigeria", "nga",
    "ghana", "gha",
    "sierra leone", "sle",
    "botswana", "bot",
    "malawi", "mwi",
    "mozambique", "moz",
    "eswatini", "swz",
    "lesotho", "les",
    "seychelles", "sey",
    "cameroon", "cmr",
    "mali", "mli",

    // European Associates
    "italy", "ita",
    "jersey", "jer",
    "guernsey", "gye",
    "germany", "ger",
    "spain", "esp",
    "denmark", "den",
    "france", "fra",
    "austria", "aut",
    "belgium", "bel",
    "norway", "nor",
    "sweden", "swe",
    "finland", "fin",
    "isle of man", "iom",
    "gibraltar", "gib",
    "malta", "mlt",
    "portugal", "por",
    "czech republic", "cze",
    "romania", "rou",
    "bulgaria", "bul",
    "hungary", "hun",
    "switzerland", "sui",
    "luxembourg", "lux",
    "cyprus", "cyp",
    "greece", "gre",
    "croatia", "cro",
    "serbia", "srb",
    "slovenia", "svn",
    "turkey", "tur",

    // East Asia-Pacific
    "vanuatu", "van",
    "fiji", "fij",
    "samoa", "sam",
    "cook islands", "cok"
)

private fun cleanTeamName(name: String): String {
    var s = name.lowercase().trim()
    // 1. Remove bracketed / parenthesized tokens like (w), (women), (a)
    s = s.replace("(w)", "").replace("(women)", "").replace("(a)", "")
    // 2. Remove hyphens/underscores before 'w' or 'a' like -w, _w, -a, _a
    s = s.replace("-w", "").replace("_w", "")
        .replace("-a", "").replace("_a", "")

    // 3. Repeatedly strip known suffixes (order independent loop)
    val suffixesToRemove = listOf(
        " women's", " womens", " women", " woman",
        " a team", " 'a'", " team", " lions", " xi",
        " u19", " u-19", " under-19", " under 19", " emerging",
        " a", " b", " w"
    )
    var changed = true
    while (changed) {
        val prev = s
        for (suffix in suffixesToRemove) {
            if (s.endsWith(suffix)) {
                s = s.removeSuffix(suffix).trim()
            }
        }
        changed = (s != prev)
    }
    return s.trim()
}

private fun isNationalTeam(name: String, shortName: String): Boolean {
    val n = cleanTeamName(name)
    val sn = cleanTeamName(shortName)
    val nWithoutW = if (n.endsWith("w") && n.length > 2) n.dropLast(1) else n
    val snWithoutW = if (sn.endsWith("w") && sn.length > 2) sn.dropLast(1) else sn
    val snWithoutA = if (sn.endsWith("a") && sn.length > 2) sn.dropLast(1) else sn

    return InternationalTeams.contains(n) ||
            InternationalTeams.contains(sn) ||
            InternationalTeams.contains(nWithoutW) ||
            InternationalTeams.contains(snWithoutW) ||
            InternationalTeams.contains(snWithoutA)
}

private fun MatchSummary.matchesCategory(targetCategory: String): Boolean {
    if (targetCategory.equals("All", ignoreCase = true)) return true

    val s = series.lowercase()
    val d = matchDesc.lowercase()
    val t1 = team1.name.lowercase()
    val t2 = team2.name.lowercase()
    val sn1 = team1.shortName.lowercase()
    val sn2 = team2.shortName.lowercase()

    val isWomen = s.contains("women") || d.contains("women") ||
            t1.contains("women") || t2.contains("women") ||
            sn1.contains("women") || sn2.contains("women") ||
            t1.endsWith(" w") || t2.endsWith(" w") ||
            sn1.endsWith("-w") || sn2.endsWith("-w") ||
            sn1.endsWith("(w)") || sn2.endsWith("(w)") ||
            s.contains("wbbl") || s.contains("wpl") ||
            s.contains("wt20") || s.contains("wodi")

    val isLeague = s.contains("league") || s.contains("ipl") || s.contains("bbl") || s.contains("psl") ||
            s.contains("cpl") || s.contains("sa20") || s.contains("ilt20") || s.contains("hundred") ||
            s.contains("blast") || s.contains("super smash") || s.contains("t20 challenge") ||
            s.contains("premier league") || s.contains("bpl") || s.contains("mlc") || s.contains("lpl") ||
            s.contains("wbbl") || s.contains("wpl")

    val bothNational = isNationalTeam(team1.name, team1.shortName) && isNationalTeam(team2.name, team2.shortName)
    val eitherNational = isNationalTeam(team1.name, team1.shortName) || isNationalTeam(team2.name, team2.shortName)
    val isTour = s.contains("tour of") || s.contains("world cup") || s.contains("asia cup") ||
            s.contains("icc ") || s.contains("tri-series") || s.contains("champions trophy") ||
            s.contains("t20i series") || s.contains("odi series") || s.contains("t20i") ||
            s.contains("wodi") || s.contains("wt20i") || s.contains("international")

    val isInternational = !isLeague && (bothNational || (isTour && eitherNational))

    return when (targetCategory.lowercase()) {
        "women" -> isWomen
        "league" -> isLeague
        "international" -> isInternational
        "domestic" -> !isInternational && !isLeague
        else -> true
    }
}

private val MatchSummary.category: String
    get() = when {
        matchesCategory("International") -> "International"
        matchesCategory("League") -> "League"
        matchesCategory("Women") -> "Women"
        else -> "Domestic"
    }

/**
 * In-memory filter and sort state that persists across screens and tab navigation
 * until the application process is closed.
 */
object SportsFilterState {
    var selectedCategory by mutableStateOf("All")
    var selectedFormat by mutableStateOf("All")
    var sortOption by mutableStateOf("Default")
    var selectedTab by mutableStateOf("live")

    val isFilterActive: Boolean
        get() = selectedCategory != "All" || selectedFormat != "All" || sortOption != "Default"

    fun reset() {
        selectedCategory = "All"
        selectedFormat = "All"
        sortOption = "Default"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportsScreen(
    navController: NavController,
    viewModel: SportsViewModel = hiltViewModel(),
    rootViewModel: RootViewModel = hiltViewModel(),
    onMatchClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val lazyListState = rememberLazyListState()

    // Sync selected tab on entry if user previously switched to upcoming/recent
    LaunchedEffect(Unit) {
        if (uiState.selectedFilter != SportsFilterState.selectedTab) {
            viewModel.onFilterSelected(SportsFilterState.selectedTab)
        }
    }

    // Filter sheet visibility (local dialog state)
    var showFilterSheet by remember { mutableStateOf(false) }

    // Persistent filter & sort states
    val selectedCategory = SportsFilterState.selectedCategory
    val selectedFormat = SportsFilterState.selectedFormat
    val sortOption = SportsFilterState.sortOption
    val isFilterActive = SportsFilterState.isFilterActive

    val displayedMatches by remember(uiState.matches, selectedCategory, selectedFormat, sortOption) {
        derivedStateOf {
            var list = uiState.matches

            // 1. Filter by Category
            if (selectedCategory != "All") {
                list = list.filter { it.matchesCategory(selectedCategory) }
            }

            // 2. Filter by Format
            if (selectedFormat != "All") {
                list = list.filter { match ->
                    when (selectedFormat.uppercase()) {
                        "T20" -> match.format == MatchFormat.T20 || match.format == MatchFormat.T10 || match.format == MatchFormat.HUNDRED
                        "ODI" -> match.format == MatchFormat.ODI
                        "TEST" -> match.format == MatchFormat.TEST
                        else -> true
                    }
                }
            }

            // 3. Sort safely handling startTime == 0L
            when (sortOption) {
                "Newest" -> {
                    list.sortedWith { a, b ->
                        when {
                            a.startTime == b.startTime -> 0
                            a.startTime <= 0L -> 1
                            b.startTime <= 0L -> -1
                            else -> b.startTime.compareTo(a.startTime)
                        }
                    }
                }
                "Oldest" -> {
                    list.sortedWith { a, b ->
                        when {
                            a.startTime == b.startTime -> 0
                            a.startTime <= 0L -> 1
                            b.startTime <= 0L -> -1
                            else -> a.startTime.compareTo(b.startTime)
                        }
                    }
                }
                else -> {
                    if (uiState.selectedFilter == "recent") {
                        list
                    } else {
                        // Live matches shown ABOVE stumped matches
                        list.sortedWith { a, b ->
                            val aStumps = a.status.contains("stumps", ignoreCase = true)
                            val bStumps = b.status.contains("stumps", ignoreCase = true)
                            val aActiveLive = a.isLive && !aStumps
                            val bActiveLive = b.isLive && !bStumps

                            val aRank = when {
                                aActiveLive -> 0
                                aStumps -> 1
                                a.isLive -> 2
                                else -> 3
                            }
                            val bRank = when {
                                bActiveLive -> 0
                                bStumps -> 1
                                b.isLive -> 2
                                else -> 3
                            }

                            if (aRank != bRank) {
                                aRank.compareTo(bRank)
                            } else {
                                val aTime = if (a.startTime <= 0L) Long.MAX_VALUE else a.startTime
                                val bTime = if (b.startTime <= 0L) Long.MAX_VALUE else b.startTime
                                aTime.compareTo(bTime)
                            }
                        }
                    }
                }
            }
        }
    }

    val isScrolled by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 24
        }
    }
    val borderAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 0.35f else 0f,
        label = "sportsStickyBorder"
    )
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 240.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ─── 1. App Header (Scrolls naturally with content) ───
            item(key = "sports_app_header") {
                AppHeader(
                    title = "Cricket",
                    subtitle = "Live scores  •  Latest updates",
                    rootViewModel = rootViewModel,
                    navController = navController
                )
            }

            // ─── 2. Filter Pills & Filter/Sort Button (stickyHeader) ───
            stickyHeader(key = "sticky_sports_chips") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .drawBehind {
                            if (borderAlpha > 0f) {
                                drawLine(
                                    color = outlineVariantColor.copy(alpha = outlineVariantColor.alpha * borderAlpha),
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        }
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Scrollable status pills: Live, Upcoming, Recent (matches LibraryScreen size/styling)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterPill(
                                label = "Live",
                                isSelected = uiState.selectedFilter == "live",
                                onClick = {
                                    SportsFilterState.selectedTab = "live"
                                    viewModel.onFilterSelected("live")
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Filled.Circle,
                                        contentDescription = null,
                                        modifier = Modifier.size(8.dp),
                                        tint = if (uiState.selectedFilter == "live") Color.White else SemanticLive
                                    )
                                }
                            )
                            FilterPill(
                                label = "Upcoming",
                                isSelected = uiState.selectedFilter == "upcoming",
                                onClick = {
                                    SportsFilterState.selectedTab = "upcoming"
                                    viewModel.onFilterSelected("upcoming")
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = if (uiState.selectedFilter == "upcoming") OnSportsAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                            FilterPill(
                                label = "Recent",
                                isSelected = uiState.selectedFilter == "recent",
                                onClick = {
                                    SportsFilterState.selectedTab = "recent"
                                    viewModel.onFilterSelected("recent")
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = if (uiState.selectedFilter == "recent") OnSportsAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Filter / Sort Action Button (32dp circle, lights up when filter active, no dot)
                        FilterButton(
                            isActive = isFilterActive,
                            onClick = { showFilterSheet = true }
                        )
                    }
                }
            }

            // ─── 3. Content States (Skeletons / Error / Empty / Match Cards) ───
            if (uiState.isLoading && displayedMatches.isEmpty()) {
                items(3, key = { "sports_skel_$it" }) {
                    MatchCardSkeleton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    )
                }
            } else if (uiState.error != null && displayedMatches.isEmpty()) {
                item(key = "sports_error") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.error ?: "Failed to load matches",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.retry() },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Retry")
                        }
                    }
                }
            } else if (displayedMatches.isEmpty()) {
                item(key = "sports_empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.SportsCricket,
                                    contentDescription = null,
                                    tint = SportsAccent,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = if (isFilterActive) "No matches match your filters" else "No matches found",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (isFilterActive) {
                                    "Try clearing filters to see all ${uiState.selectedFilter} matches"
                                } else {
                                    when (uiState.selectedFilter) {
                                        "live" -> "No live or ongoing matches at the moment"
                                        "upcoming" -> "No scheduled matches found"
                                        else -> "No recently completed matches found"
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isFilterActive) {
                                Spacer(Modifier.height(14.dp))
                                Button(
                                    onClick = { SportsFilterState.reset() },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Text("Reset Filters", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                items(
                    items = displayedMatches,
                    key = { match -> match.id }
                ) { match ->
                    MatchCard(
                        match = match,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .pressable(
                                onClick = { onMatchClick(match.id) },
                                pressedScale = MotionTokens.CardPressScale
                            )
                    )
                }
            }
        }

        // ─── Filter & Sort Bottom Sheet ───
        if (showFilterSheet) {
            SportsFilterBottomSheet(
                selectedCategory = selectedCategory,
                selectedFormat = selectedFormat,
                sortOption = sortOption,
                isFilterActive = isFilterActive,
                onCategorySelected = { SportsFilterState.selectedCategory = it },
                onFormatSelected = { SportsFilterState.selectedFormat = it },
                onSortSelected = { SportsFilterState.sortOption = it },
                onReset = { SportsFilterState.reset() },
                onDismiss = { showFilterSheet = false }
            )
        }
    }
}

@Composable
private fun FilterButton(
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .pressable(onClick = onClick, pressedScale = MotionTokens.ChipPressScale)
            .clip(CircleShape)
            .background(
                if (isActive) SportsAccent else MaterialTheme.colorScheme.surfaceContainerHighest
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = "Filter and Sort",
            modifier = Modifier.size(16.dp),
            tint = if (isActive) OnSportsAccent else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SportsFilterBottomSheet(
    selectedCategory: String,
    selectedFormat: String,
    sortOption: String,
    isFilterActive: Boolean,
    onCategorySelected: (String) -> Unit,
    onFormatSelected: (String) -> Unit,
    onSortSelected: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter & Sort",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Permanent Reset button in place (disabled when no active filter)
                TextButton(
                    onClick = onReset,
                    enabled = isFilterActive
                ) {
                    Text(
                        text = "Reset",
                        color = if (isFilterActive) SportsAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Category Filter (International, League, Domestic, Women)
            Text(
                text = "CATEGORY",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "International", "League", "Domestic", "Women").forEach { category ->
                    val isSelected = selectedCategory.equals(category, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .pressable(
                                onClick = { onCategorySelected(category) },
                                pressedScale = MotionTokens.ChipPressScale
                            )
                            .clip(CircleShape)
                            .background(
                                if (isSelected) SportsAccent else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = category,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) OnSportsAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Format Filter (T20, ODI, Test)
            Text(
                text = "FORMAT",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "T20", "ODI", "Test").forEach { fmt ->
                    val isSelected = selectedFormat.equals(fmt, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .pressable(
                                onClick = { onFormatSelected(fmt) },
                                pressedScale = MotionTokens.ChipPressScale
                            )
                            .clip(CircleShape)
                            .background(
                                if (isSelected) SportsAccent else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = fmt,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) OnSportsAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Sort Options (Default, Newest, Oldest)
            Text(
                text = "SORT BY",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Default" to "Default", "Newest" to "Newest First", "Oldest" to "Oldest First").forEach { (key, label) ->
                    val isSelected = sortOption == key
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .pressable(
                                onClick = { onSortSelected(key) },
                                pressedScale = MotionTokens.ChipPressScale
                            )
                            .clip(CircleShape)
                            .background(
                                if (isSelected) SportsAccent else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) OnSportsAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SportsAccent,
                    contentColor = OnSportsAccent
                )
            ) {
                Text("Show Matches", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .pressable(
                onClick = onClick,
                pressedScale = MotionTokens.ChipPressScale
            )
            .clip(CircleShape)
            .background(
                if (isSelected) SportsAccent else MaterialTheme.colorScheme.surfaceContainerHighest
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (isSelected) OnSportsAccent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MatchCard(
    match: MatchSummary,
    modifier: Modifier = Modifier
) {
    val isStumps = match.status.contains("stumps", ignoreCase = true)
    val isActuallyLive = match.isLive && !isStumps

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Series name + Format badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = match.seriesName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = match.format.name,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = SportsAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Teams & Scores
            TeamRow(
                name = match.team1.shortName.ifBlank { match.team1.name },
                score = match.team1Score ?: "",
                logoUrl = match.team1.logoUrl,
                isLive = isActuallyLive
            )
            Spacer(modifier = Modifier.height(10.dp))
            TeamRow(
                name = match.team2.shortName.ifBlank { match.team2.name },
                score = match.team2Score ?: "",
                logoUrl = match.team2.logoUrl,
                isLive = isActuallyLive
            )

            // Status Row
            if (match.status.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActuallyLive) {
                        Icon(
                            imageVector = Icons.Filled.Circle,
                            contentDescription = "Live",
                            modifier = Modifier.size(7.dp),
                            tint = SemanticLive
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    } else if (isStumps) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = "Stumps",
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = match.status,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = if (isActuallyLive) FontWeight.Medium else FontWeight.Normal
                        ),
                        color = if (isActuallyLive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Series name skeleton + Format badge skeleton
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(12.dp)
                        .clip(CircleShape)
                        .shimmer()
                )
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(18.dp)
                        .clip(MaterialTheme.shapes.small)
                        .shimmer()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Team 1 Row Skeleton
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(MaterialTheme.shapes.small)
                            .shimmer()
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .width(85.dp)
                            .height(15.dp)
                            .clip(CircleShape)
                            .shimmer()
                    )
                }
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(15.dp)
                        .clip(CircleShape)
                        .shimmer()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Team 2 Row Skeleton
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(MaterialTheme.shapes.small)
                            .shimmer()
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .width(105.dp)
                            .height(15.dp)
                            .clip(CircleShape)
                            .shimmer()
                    )
                }
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(15.dp)
                        .clip(CircleShape)
                        .shimmer()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status Row Skeleton
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .shimmer()
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(12.dp)
                        .clip(CircleShape)
                        .shimmer()
                )
            }
        }
    }
}

@Composable
private fun TeamRow(name: String, score: String, logoUrl: String?, isLive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (logoUrl != null) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = "$name logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val isYetToBat = score.isBlank() || score.equals("Yet to bat", ignoreCase = true)
        if (isYetToBat) {
            Text(
                text = "Yet to bat",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val scoreParts = score.split("(")
            val runsText = scoreParts[0].trim()
            val oversText = if (scoreParts.size > 1) "(${scoreParts[1]}" else ""

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = runsText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = if (isLive) SportsAccent else MaterialTheme.colorScheme.onSurface
                )
                if (oversText.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = oversText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }
            }
        }
    }
}
