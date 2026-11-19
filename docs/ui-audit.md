# UI-0 Audit Report — Synq UI Refinement Baseline

**Date:** 2026-11-19
**Git Tag:** `pre-ui-refinement`
**Commit:** `6a93ed9` (Baseline)
**Sub-phase:** UI-0 (Baseline & Audit)

---

## 1. Baseline Gates & Build Verification

| Gate / Command | Result | Details |
|---|---|---|
| `git tag pre-ui-refinement` | ✅ Created | Tag created on commit `6a93ed9` |
| `./gradlew :app:assembleDebug` | ✅ SUCCESS | 50 actionable tasks up-to-date, zero compile errors |
| `./gradlew :app:testDebugUnitTest` | ✅ SUCCESS | All Android unit tests pass (100% test success rate) |
| `scripts/ui_guard.sh` | ✅ Created & Verified | Check 1 (Frozen files): CLEAN (0 hits)<br>Check 2 (GlassSurface.kt): CLEAN (0 hits)<br>Check 3 (Logic touch): CLEAN (0 hits)<br>Check 4 (Forbidden files): CLEAN (0 hits) |

### Baseline Metrics (§8 Target Comparison)

| Metric | Baseline | Target (UI-F) | Status |
|---|---|---|---|
| `FontWeight.Bold` | **150** | ≤ 45 | Overused, needs reduction to titles/headers |
| `FontWeight.ExtraBold` | **16** | **0** (except frozen) | Banned everywhere |
| `FontWeight.Black` | **1** | **0** | Banned everywhere |
| `FontWeight.SemiBold` | **95** | ≤ 45 | Overused, reserve for buttons/badges/active tabs |
| `FontWeight.Medium` | **50** | Increased | Target weight for item titles, chips |
| `FontWeight.Normal` | **13** | Increased | Target weight for subtitles, body text |
| `fontSize` literals outside `Type.kt` | **334** | ≤ 25 | Bypassing typography tokens |
| `Color(0x…)` literals outside `ui/theme` | **253** | ≤ 25 | Hardcoded colors bypassing theme tokens |
| `RoundedCornerShape(n.dp)` literals | **190** | ≤ 25 | Inconsistent radii (2–28dp) |
| Indigo/blue hex grep hits | **43** | **0** | Off-brand Tailwind palette |
| `items(...)` with key / total | **8 / 35** | 35 / 35 (or all animated) | 27 unkeyed Lazy list items |
| `.border(1.dp …)` count | **33** | ≤ 10 | Boxy card styling across rows/tiles |
| Haptic feedback call sites | **0** | ≥ 6 | No tactile feedback currently |

---

## 2. API Availability Confirmation (Compose & Material 3)

Resolved via Gradle dependency tree (`composeBom = "2025.06.01"`):
- **Compose Foundation:** `androidx.compose.foundation:foundation:1.8.3`
- **Compose UI:** `androidx.compose.ui:ui:1.8.3`
- **Material 3:** `androidx.compose.material3:material3:1.3.2`

| API Requested | Status in Project | Class / Interface | Verification Notes |
|---|---|---|---|
| `Modifier.animateItem()` | **AVAILABLE** | `androidx.compose.foundation.lazy.LazyItemScope` | Replaced `animateItemPlacement()` in Foundation 1.7+. Available in Foundation 1.8.3. |
| `surfaceContainer` | **AVAILABLE** | `androidx.compose.material3.ColorScheme` | Added in M3 1.2.0. Available in M3 1.3.2. |
| `surfaceContainerLowest` | **AVAILABLE** | `androidx.compose.material3.ColorScheme` | Added in M3 1.2.0. Available in M3 1.3.2. |
| `surfaceContainerLow` | **AVAILABLE** | `androidx.compose.material3.ColorScheme` | Added in M3 1.2.0. Available in M3 1.3.2. |
| `surfaceContainerHigh` | **AVAILABLE** | `androidx.compose.material3.ColorScheme` | Added in M3 1.2.0. Available in M3 1.3.2. |
| `surfaceContainerHighest` | **AVAILABLE** | `androidx.compose.material3.ColorScheme` | Added in M3 1.2.0. Available in M3 1.3.2. |
| `HapticFeedbackType.Confirm` | **AVAILABLE** | `androidx.compose.ui.hapticfeedback.HapticFeedbackType.Companion` | Added in Compose UI 1.7/1.8. Available in UI 1.8.3. |
| `HapticFeedbackType.ToggleOn` | **AVAILABLE** | `androidx.compose.ui.hapticfeedback.HapticFeedbackType.Companion` | Added in Compose UI 1.7/1.8. Available in UI 1.8.3. |
| `HapticFeedbackType.ToggleOff` | **AVAILABLE** | `androidx.compose.ui.hapticfeedback.HapticFeedbackType.Companion` | Added in Compose UI 1.7/1.8. Available in UI 1.8.3. |

---

## 3. Screen-Private Palettes & Local Colour Objects

The audit found multiple screens defining private palettes or local color instances that bypass `MaterialTheme.colorScheme`.

### 3.1 `HomePalette` in `features/home/HomeScreen.kt` (Lines 118–154)
Home defines its own complete palette object and `CompositionLocalProvider(LocalHomePalette provides palette)`, making Home completely unresponsive to `MaterialTheme`:
- `AccentIndigo = Color(0xFF6366F1)`
- `StadiumNavy = Color(0xFF0F172A)`
- `DarkPalette`:
  - `background = Color(0xFF09090B)`
  - `glow = Color(0xFF1A1A2E)`
  - `surface = Color.White.copy(alpha = 0.03f)`
  - `surfaceBorder = Color.White.copy(alpha = 0.05f)`
  - `surfaceHighlight = Color.White.copy(alpha = 0.10f)`
  - `textPrimary = Color.White`
  - `textSecondary = Color(0xFFA1A1AA)`
  - `accent = AccentIndigo`
- `LightPalette`:
  - `background = Color(0xFFF8FAFC)`
  - `glow = Color(0xFFE2E8F0)`
  - `surface = Color.White`
  - `surfaceBorder = Color.Black.copy(alpha = 0.05f)`
  - `surfaceHighlight = Color.Black.copy(alpha = 0.10f)`
  - `textPrimary = Color(0xFF0F172A)`
  - `textSecondary = Color(0xFF475569)`
  - `accent = AccentIndigo`
- **Resolution Plan (UI-C):** Delete `HomePalette`, `LocalHomePalette`, `DarkPalette`, `LightPalette`. Rewire all usages to `MaterialTheme.colorScheme` (`background`, `surfaceContainerLow`, `onSurface`, `onSurfaceVariant`, `PinkPrimary`, `outlineVariant`).

### 3.2 Private Colour Values in `features/sports/MatchDetailsScreen.kt` (Lines 43–52)
MatchDetails defines private colors replicating theme slots:
- `TextMain @Composable get() = MaterialTheme.colorScheme.onBackground`
- `TextSecondary @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant`
- `AccentRed = Color(0xFFDE474E)` → Rewire to `SemanticLive` (`#EF4444`)
- `AccentBlue = Color(0xFF5566FF)` → Rewire to `SportsAccent` (`#C6F135` dark / `#4D7C0F` light)
- `CardBlue @Composable get() = MaterialTheme.colorScheme.surface` → Rewire to `surfaceContainerLow`
- `CardHeaderBg @Composable get() = MaterialTheme.colorScheme.surfaceVariant` → Remove per T2-4
- `SubHeaderBg @Composable get() = MaterialTheme.colorScheme.background` → `background`
- `BorderLight @Composable get() = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)` → Remove per T1/T2-4
- `AvatarBg @Composable get() = MaterialTheme.colorScheme.surfaceVariant` → `surfaceContainerHighest`
- **Resolution Plan (UI-C):** Delete all private color properties; use `MaterialTheme.colorScheme` and `SportsAccent`.

### 3.3 Private Colours in `features/music/jam/JamScreen.kt` (Lines 76–109) & `JamIntroDialog.kt` (Lines 32–59)
- `Accent = Color(0xFFFF2D7A)` → Rewire to `PinkPrimary`
- `Danger = Color(0xFFEF4444)` → Rewire to `MaterialTheme.colorScheme.error`
- `OnlineGreen = Color(0xFF22C55E)` → Rewire to `SemanticSuccess` (`#10B981`)
- `WarningAmber = Color(0xFFF59E0B)` → Rewire to `SemanticWarning`
- `bgColor = if (isDark) Color(0xFF0F0C13) else Color(0xFFF8FAFC)` → `MaterialTheme.colorScheme.background`
- `cardBg = if (isDark) Color(0xFF181320) else Color(0xFFFFFFFF)` → `surfaceContainerLow`
- `textPrimary` / `textSecondary` → `onBackground` / `onSurfaceVariant`
- `dialogBg = if (isDark) Color(0xFF1A1420) else Color(0xFFFFFFFF)` → `surfaceContainerHigh`

### 3.4 Brand Tokens in `ui/SynqBrand.kt` (Lines 30–38)
- `SynqPink = Color(0xFFFF2D55)` → Alias to `PinkPrimary` (`#FF2D7A`)
- `SynqPinkLight = Color(0xFFFF8FA3)` → Alias to `PinkGlow`
- `SynqPinkDark = Color(0xFFD0165E)` → Alias to `PinkDeep`
- `SynqBackground = Color(0xFF141116)` → Unused, DELETE
- `SynqSurface = Color(0xFF1E1822)` → Unused, DELETE
- `SynqPinkGradient` → Replace usages with `PinkGradientHero` in UI-C

### 3.5 Frozen Aliases in `ui/GlassSurface.kt` (Lines 16–17)
- `GlassAccent = Color(0xFF6366F1)` → Remap to `PinkPrimary` (accent lines only)
- `GlassAccentPink = Color(0xFFEC4899)` → Remap to `PinkSoft`
- Note: `fill = Color(0xFF1A1A22).copy(alpha = 0.82f)` is left untouched per freeze & D2.

---

## 4. Complete Hex Inventory

### 4.1 Indigo Family (Banned — To be replaced with Pink tokens / Tonal surfaces)
| Hex | Constant / Usage | File:Line | Target Token |
|---|---|---|---|
| `6366F1` | `DarkPrimary`, `LightPrimary` | `ui/theme/Color.kt:6, 32` | `PinkPrimary` (`#FF2D7A`) |
| `6366F1` | `GlassAccent` | `ui/GlassSurface.kt:16` | `PinkPrimary` |
| `6366F1` | `AccentIndigo` | `features/home/HomeScreen.kt:129` | Delete / `PinkPrimary` |
| `4F46E5` | `DarkPrimaryContainer` | `ui/theme/Color.kt:8` | `PinkDeep` (`#D0165E`) |
| `818CF8` | `LightPrimaryContainer` | `ui/theme/Color.kt:34` | `PinkGlow` (`#FF8FA3`) |
| `5566FF` | `AccentBlue` | `features/sports/MatchDetailsScreen.kt:46` | `SportsAccent` |

### 4.2 Slate Family (Banned — To be replaced with Pink-tinted neutrals)
| Hex | Current Role | File:Line Examples | Target Token |
|---|---|---|---|
| `0F172A` | `DarkBackground`, dark text on light | `ui/theme/Color.kt:21, 48, 50`, `HomeScreen.kt:130, 149, 199`, `FullPlayerSheet.kt:1444, 1576`, `JamScreen.kt:108, 1201` | Dark: `#141116` (`background`)<br>Light: `#1C1420` (`onBackground`) |
| `1E293B` | `DarkSurface` (slate-800) | `ui/theme/Color.kt:23` | `#1E1822` (`surface`) |
| `334155` | `DarkSurfaceVariant` (slate-700) | `ui/theme/Color.kt:25` | `#2E2535` (`surfaceVariant`) |
| `94A3B8` | `DarkOnSurfaceVariant` (slate-400) | `ui/theme/Color.kt:26`, `HomeScreen.kt:531, 540`, `PlaylistScreen.kt:1070` | `#A79EAD` (`onSurfaceVariant`) |
| `64748B` | `LightOnSurfaceVariant` (slate-500) | `ui/theme/Color.kt:52`, `FullPlayerSheet.kt:1445, 1577`, `JamScreen.kt:109, 1286` | `#6B6070` (`onSurfaceVariant`) |
| `475569` | Slate secondary text | `HomeScreen.kt:150`, `GlassSurface.kt:43` | `onSurfaceVariant` |
| `E2E8F0` | `LightSurfaceVariant` (slate-200) | `ui/theme/Color.kt:51`, `HomeScreen.kt:145, 564` | `#F3E8EE` / `#EDDFE7` |
| `F1F5F9` | `LightBackground` (slate-100) | `ui/theme/Color.kt:47` | `#FBF4F7` (`background`) |
| `F8FAFC` | Slate-50 background | `HomeScreen.kt:144`, `JamScreen.kt:106` | `#FBF4F7` |
| `09090B` | Zinc-950 background | `HomeScreen.kt:133` | `#141116` |
| `1A1A2E` | Deep navy glow | `HomeScreen.kt:134` | Remove radial glow |
| `A1A1AA` | Zinc-400 secondary text | `HomeScreen.kt:139`, `GlassSurface.kt:36` | `onSurfaceVariant` |
| `71717A` | Zinc-500 muted text | `GlassSurface.kt:37` | Frozen file (leave) |
| `18131B` | Ad-hoc dark surface | `AddToPlaylistSheet.kt:49, 114` | `surfaceContainerHigh` (`#261E2B`) |
| `1E1724` | Ad-hoc dark surface | `AddToPlaylistSheet.kt:125, 479` | `surfaceContainerHighest` (`#2E2535`) |
| `1A1420` | Ad-hoc dark surface | `JamIntroDialog.kt:59` | `surfaceContainerHigh` |
| `0F0C13` | Ad-hoc dark surface | `JamScreen.kt:106` | `background` (`#141116`) |
| `181320` | Ad-hoc dark card | `JamScreen.kt:107` | `surfaceContainerLow` (`#1A141E`) |

### 4.3 Emerald / Green Family
| Hex | Current Role | File:Line | Target Token |
|---|---|---|---|
| `10B981` | `DarkTertiary`, `LightTertiary` | `ui/theme/Color.kt:16, 42` | `SemanticSuccess` (`#10B981`) — delete from M3 Tertiary slot |
| `059669` | `DarkTertiaryContainer` / Jam bg | `ui/theme/Color.kt:18`, `HomeScreen.kt:701` | Replace with pink/tonal containers |
| `34D399` | `LightTertiaryContainer` / Jam text | `ui/theme/Color.kt:44`, `HomeScreen.kt:750` | Replace with `PinkGlow` / `onSurfaceVariant` |
| `022C22` | Jam dark gradient end | `HomeScreen.kt:701` | `PinkGradient` per §5 |
| `22C55E` | `OnlineGreen` in Jam | `JamScreen.kt:78` | `SemanticSuccess` |

### 4.4 Blue / Cyan Family (Decorative — To be replaced with Pink / SportsAccent)
| Hex | Current Role | File:Line | Target Token |
|---|---|---|---|
| `3B82F6` | `MatchStatus.UPCOMING` | `HomeScreen.kt:94` | `SportsAccent` / `outline` |
| `2563EB` | Jam inactive card gradient | `HomeScreen.kt:702` | `PinkGradient` |
| `06B6D4` | Jam inactive card gradient end | `HomeScreen.kt:702` | `PinkGradient` |
| `60A5FA` | Blue accent | `SearchScreen.kt:430` | Tonal category containers |

### 4.5 Pink Family (Consolidating from 9 variants down to EXACTLY 4)
| Existing Hex | Current Usage | File:Line | Target Unified Token |
|---|---|---|---|
| `EC4899` | `DarkSecondary`, `LightSecondary`, `GlassAccentPink` | `ui/theme/Color.kt:11, 37`, `GlassSurface.kt:17` | `PinkSoft` (`#FF6584`) |
| `FF2D7A` | Jam `Accent`, reference 3-dot menu | `JamScreen.kt:76`, `JamIntroDialog.kt:32`, `FullPlayerSheet.kt:1835` | **`PinkPrimary` (`#FF2D7A`)** |
| `FF2A5F` | `BrandPink` in player | `FullPlayerSheet.kt:73, 1021` | `PinkPrimary` (`#FF2D7A`) |
| `FF2D55` | Old `SynqPink` | `ui/SynqBrand.kt:30` | `PinkPrimary` (`#FF2D7A`) (D1) |
| `DB2777` | `DarkSecondaryContainer` | `ui/theme/Color.kt:13` | `PinkDeep` (`#D0165E`) |
| `F472B6` | `LightSecondaryContainer` | `ui/theme/Color.kt:39` | `PinkGlow` (`#FF8FA3`) |
| `FF6584` | New proposed gradient end | `UI_REFINEMENT.md` | **`PinkSoft` (`#FF6584`)** |
| `FF8FA3` | `SynqPinkLight` | `ui/SynqBrand.kt:31` | **`PinkGlow` (`#FF8FA3`)** |
| `D0165E` | `SynqPinkDark` | `ui/SynqBrand.kt:32` | **`PinkDeep` (`#D0165E`)** |

### 4.6 Semantic & Live Hexes (Keep as Semantic Tokens)
| Hex | Role | File:Line | Target Token |
|---|---|---|---|
| `EF4444` | Error / Live badge / Logout | `ui/theme/Color.kt:28, 54`, `HomeScreen.kt:373`, `AppHeader.kt:216`, `JamScreen.kt:77` | `SemanticLive` / `error` (`#EF4444`) |
| `FF4757` | `MatchStatus.LIVE` | `HomeScreen.kt:93` | `SemanticLive` (`#EF4444`) |
| `DE474E` | `AccentRed` | `MatchDetailsScreen.kt:45` | `SemanticLive` (`#EF4444`) |
| `F59E0B` | `WarningAmber` | `JamScreen.kt:79` | `SemanticWarning` |
| `FBBF24` | Match status highlight amber | `HomeScreen.kt:93` | `SemanticWarning` |
| `A3E635` | Post-match highlight lime | `HomeScreen.kt:94, 95` | `SportsAccent` |

---

## 5. Lazy Lists & items() Audit

Total `items(...)` calls: **35**
Keyed items: **8**
Unkeyed items: **27**

### 5.1 The 27 Unkeyed `items(...)` Calls
| # | File:Line | Collection | Stable ID Available? | Recommendation for UI-D |
|---|---|---|---|---|
| 1 | `HomeScreen.kt:257` | `matches` | Yes (`it.id`) | Add `key = { it.id }` + `animateItem()` |
| 2 | `HomeScreen.kt:278` | `realRecentlyPlayed.take(5)` | Yes (`it.songId`) | Add `key = { it.songId }` + `animateItem()` |
| 3 | `HomeScreen.kt:304` | `HomeSampleData.recentlyPlayed.take(5)` | Yes (`it.title`) | Add `key = { it.title }` |
| 4 | `HomeScreen.kt:379` | `trending` | Yes (`it.id`) | Add `key = { it.id }` + `animateItem()` |
| 5 | `LibraryScreen.kt:159` | `LibraryTab.values()` | Yes (`it.name`) | Add `key = { it.name }` |
| 6 | `LibraryScreen.kt:233` | `likedSongs` | Yes (`it.id`) | Add `key = { it.id }` + `animateItem()` |
| 7 | `LibraryScreen.kt:334` | `displayPlaylists` | Yes (`it.id`) | Add `key = { it.id }` + `animateItem()` |
| 8 | `LibraryScreen.kt:379` | `followedTeams` | Yes (`it.id`) | Add `key = { it.id }` + `animateItem()` |
| 9 | `LibraryScreen.kt:412` | `displayList` | Yes (`it.id`) | Add `key = { it.id }` + `animateItem()` |
| 10 | `LibraryScreen.kt:811` | `playlists` | Yes (`it.id`) | Add `key = { it.id }` |
| 11 | `AlbumScreen.kt:306` | `album.artists.size` | Yes (`album.artists[it].id`) | Add `key = { album.artists[it].id }` |
| 12 | `ArtistScreen.kt:452` | `artist.albums.size` | Yes (`artist.albums[it].id`) | Add `key = { artist.albums[it].id }` |
| 13 | `MusicScreen.kt:122` | `featured.size` | Yes (`featured[it].id`) | Add `key = { featured[it].id }` |
| 14 | `MusicScreen.kt:203` | `initialArtists.size` | Yes (`initialArtists[it].id`) | Add `key = { initialArtists[it].id }` |
| 15 | `MusicScreen.kt:228` | `fallbackArtists.size` | Yes (`fallbackArtists[it].id`) | Add `key = { fallbackArtists[it].id }` |
| 16 | `MusicScreen.kt:251` | `bestOfList.size` | Yes (`bestOfList[it].id`) | Add `key = { bestOfList[it].id }` |
| 17 | `MusicScreen.kt:277` | `mockBestOf.size` | Yes (`mockBestOf[it].id`) | Add `key = { mockBestOf[it].id }` |
| 18 | `PlaylistScreen.kt:772` | `songs` | Yes (`it.id`) | Add `key = { it.id }` + `animateItem()` |
| 19 | `SongDetailScreen.kt:307` | `song.artists` | Yes (`it.id`) | Add `key = { it.id }` |
| 20 | `ProfileScreen.kt:587` | `settingsItems.size` | Yes (`settingsItems[it].title`) | Add `key = { settingsItems[it].title }` |
| 21 | `SearchScreen.kt:222` | `uiState.recentSearches` | Yes (`it`) | Add `key = { it }` + `animateItem()` |
| 22 | `SearchScreen.kt:410` | `results.songs` | Yes (`it.id`) | Add `key = { it.id }` + `animateItem()` |
| 23 | `MatchDetailsScreen.kt:130` | `tabs.size` | Yes (`tabs[it]`) | Add `key = { tabs[it] }` |
| 24 | `MatchDetailsScreen.kt:627` | `recentBalls` | No stable ID (transient balls) | Keep unkeyed (no animation) |
| 25 | `MatchDetailsScreen.kt:675` | `details.commentary` | Yes (`it.id ?: it.over`) | Add `key = { it.id ?: index }` |
| 26 | `MatchDetailsScreen.kt:1096` | `squads.teams.entries.toList()` | Yes (`it.key`) | Add `key = { it.key }` |
| 27 | `SportsScreen.kt:160` | `matches` | Yes (`it.id`) | Add `key = { it.id }` + `animateItem()` |

### 5.2 The 8 Currently Keyed `items(...)` Calls
1. `AllPlaylistsScreen.kt:376`: `key = { it.serverId }`
2. `AlbumScreen.kt:482`: `key = { album.songs[it].id }`
3. `AddToPlaylistSheet.kt:212`: `key = { "saved_${it.serverId}" }`
4. `AddToPlaylistSheet.kt:354`: `key = { "unsaved_${it.serverId}" }`
5. `AddToPlaylistSheet.kt:427`: `key = { "search_${it.serverId}" }`
6. `FullPlayerSheet.kt:777`: `key = { it.qid }`
7. `ListeningHistoryScreen.kt:177`: `key = { it.id }`
8. `PopularArtistsScreen.kt:247`: `key = { it.id }`

---

## 6. The Three Private SectionHeader Copies

### 6.1 Call Sites and Signatures
1. **HomeScreen.kt (Line 426):**
   ```kotlin
   @Composable
   private fun SectionHeader(
       title: String,
       actionText: String? = null,
       onActionClick: () -> Unit = {}
   )
   ```
   - Padding: `start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp`
   - Title: `fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary`
   - Action: `fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.accent`
   - Call sites: Home match row ("Live Action"), "Recently Played", "Jam", "Trending Music"

2. **LibraryScreen.kt (Line 477):**
   ```kotlin
   @Composable
   private fun SectionHeader(
       title: String,
       count: Int? = null,
       actionText: String? = null,
       onAction: () -> Unit = {}
   )
   ```
   - Padding: `horizontal = 24.dp, top = 16.dp, bottom = 12.dp`
   - Title: `fontSize = 19.sp, fontWeight = FontWeight.Bold`
   - Count: `fontSize = 13.sp` in parenthesis
   - Action: `fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = primary`
   - Call sites: Library sections ("Playlists", "Liked Songs", "Followed Teams")

3. **SearchScreen.kt (Line 425):**
   ```kotlin
   @Composable
   fun SectionHeader(title: String)
   ```
   - Padding: `horizontal = 24.dp, top = 20.dp, bottom = 12.dp`
   - Title: `fontSize = 18.sp, fontWeight = FontWeight.Bold`
   - Call sites: "Recent Searches", "Browse Categories", "Search Results"

### 6.2 Unified Replacement Component (`ui/components/SectionHeader.kt`)
```kotlin
@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge, // 18sp Bold
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall, // 13sp Normal
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelMedium, // 12sp Medium
                color = MaterialTheme.colorScheme.primary, // PinkPrimary (PinkDeep on light)
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
```

---

## 7. Font Size Literals Grouped by Proposed Role

Total literals audited outside `Type.kt`: **347**

| Role (M3 slot) | Target (Size / Weight) | Current Count | Example Usages in Code |
|---|---|---|---|
| `headlineLarge` | **28sp Bold** | 6 | Auth hero title (`AuthScreen.kt:70`), `SynqBrandedText` (`SynqBrand.kt:71`), `AppHeader` title (`AppHeader.kt:117, 124`) |
| `headlineMedium` | **24sp Bold** | 3 | Screen titles (`ArtistScreen.kt:387`, `AllPlaylistsScreen.kt:294`) |
| `headlineSmall` | **20sp SemiBold** | 15 | Sheet/dialog titles (`AddToPlaylistSheet.kt:170`, `HomeScreen.kt:439, 521`, `FullPlayerSheet.kt:1210`) |
| `titleLarge` | **18sp Bold** | 10 | Section headers (`SearchScreen.kt:429`), score numerals (`HomeScreen.kt:564`), album headers |
| `titleMedium` | **15sp Medium** | 72 | Song row titles (`TrendingSongRow`, `SongItem.kt:72`, `LibraryScreen.kt:578`, `FullPlayerSheet.kt:373`) |
| `titleSmall` / `bodyMedium` / `labelLarge` | **14sp Normal/Medium/SemiBold** | 48 | Default body, buttons, tab labels, compact titles (`HomeScreen.kt:444, 532, 542, 806`) |
| `bodySmall` | **13sp Normal** | 69 | Secondary artist/album lines (`SongItem.kt:85`, `HomeScreen.kt:551, 677`, `AddToPlaylistSheet.kt:509`) |
| `labelMedium` | **12sp Medium** | 46 | Chips, timestamps, "See all" actions, durations (`HomeScreen.kt:751`, `SongItem.kt:98`) |
| `labelSmall` | **11sp Medium** | 19 | Badge labels, track numbers, counts (`MatchDetailsScreen.kt:418, 563`) |
| **Non-Standard / Needs Decision** | *See below* | 46 | Ad-hoc sizes: 9sp, 10sp, 10.5sp, 11.5sp, 13.5sp, 14.5sp, 17sp, 19sp, 22sp, 23sp, 26sp |

### Non-Standard Font Size Breakdown & Resolution Proposals
| Size | Count | Locations | Proposed Mapping |
|---|---|---|---|
| **9sp** | 2 | `JamScreen.kt:1260, 1687` (host pill tag, tiny subtext) | Map to `labelSmall` (11sp) or keep 11sp minimum per §4.5 |
| **10sp** | 6 | `HomeScreen.kt:603` (status badge), `AlbumScreen.kt:251, 268` (stat badge), `FullPlayerSheet.kt:324` (explicit tag), `MusicScreen.kt:421` (NEW badge), `SportsScreen.kt:254` (format badge) | **Jump to 11sp SemiBold** (`labelSmall`) per §4.5 & §4.6 ("Never ExtraBold; no 10sp") |
| **10.5sp** | 13 | `AppBottomNavBar.kt:124` (1×, **FROZEN — keep as is**), `FullPlayerSheet.kt:1153`, `JamScreen.kt:681, 1322, 1575, 1586, 1597, 1737, 1748, 1759, 1770, 1776, 1782` | Map to `labelMedium` (12sp) or `labelSmall` (11sp) |
| **11.5sp** | 7 | `JamScreen.kt:488, 503, 518, 533, 864, 1097, 1697` | Map to `labelMedium` (12sp Medium) |
| **13.5sp** | 3 | `CreatePlaylistDialog.kt:234, 249`, `JamScreen.kt:1089` | Map to `bodySmall` (13sp) |
| **14.5sp** | 1 | `JamScreen.kt:1675` | Map to `bodyMedium` (14sp) |
| **17sp** | 6 | `HomeScreen.kt:669`, `MusicScreen.kt:361`, `PlaylistScreen.kt:371`, `FullPlayerSheet.kt:1251, 1544`, `AppHeader.kt:185` | Map to `titleMedium` (15sp Medium) |
| **19sp** | 3 | `LibraryScreen.kt:277, 491`, `FullPlayerSheet.kt:1759` | Map to `titleLarge` (18sp Bold) or `headlineSmall` (20sp) |
| **22sp** | 3 | `AddToPlaylistSheet.kt:170`, `JamIntroDialog.kt:121`, `PopularArtistsScreen.kt:92` | Map to `headlineSmall` (20sp SemiBold) |
| **23sp** | 1 | `FullPlayerSheet.kt:404` | Map to `headlineMedium` (24sp Bold) |
| **26sp** | 1 | `PlaylistScreen.kt:481` | Map to `headlineMedium` (24sp Bold) |

---

## 8. Per-Element Proposal Table (All Screens) — [UNVERIFIED]

> [!WARNING]
> **UNVERIFIED:** This baseline proposal table is marked as unverified. A spot-check identified inaccuracies in line numbers and current values in certain components (e.g. AuthScreen, ProfileScreen, JamScreen, MatchDetailsScreen, SongItem, SportsScreen).
> **Mandatory Rule for UI-B and UI-C:** Before editing any file in UI-B or UI-C, open the actual file, record the REAL current values directly from source, and report them in that phase's report. Never assume or copy unverified "current" values.

Format: `element | file:line | current (size, weight, colour, radius) | target from §4.6 | tier | reason`

### 8.1 AppHeader (`ui/AppHeader.kt`)
| Element | File:Line | Current | Target from §4.6 | Tier | Reason |
|---|---|---|---|---|---|
| Header container | `AppHeader.kt:59` | `padding(start=24, end=24, top=16, bottom=12)` | `padding(start=20, end=20, top=12, bottom=8)` | 1 | Consistent 20dp left edge & refined density |
| Logo squircle | `AppHeader.kt:67` | 44dp, clip `0.22f` | 44dp, clip `0.22f` | 1 | **Keep as is** (§3.2) |
| Logo dropdown menu | `AppHeader.kt:167` | `RoundedCornerShape(16.dp)`, surface | `shapes.large`, `surfaceContainerHigh` | 1 | Standardize shape and tinted elevation container |
| Avatar circle | `AppHeader.kt:254` | 44dp, CircleShape, `AvatarGradient` | 44dp, `PinkGradientHero` | 1 | Use unified hero token |
| Title (Branded "Synq") | `AppHeader.kt:117` | 28sp ExtraBold | 24sp Bold (`headlineMedium`) | 1 | Screen title hierarchy; eliminate ExtraBold |
| Title (Standard text) | `AppHeader.kt:124` | 28sp ExtraBold, onBackground | 24sp Bold (`headlineMedium`) | 1 | Screen title hierarchy; eliminate ExtraBold |
| Subtitle | `AppHeader.kt:136` | 14sp Normal, onSurfaceVariant | 13sp Normal (`bodySmall`), onSurfaceVariant | 1 | Hierarchy ratio (24:18:15:13) |
| Menu user name | `AppHeader.kt:185` | 17sp SemiBold | 15sp Medium (`titleMedium`) | 1 | Eliminate ad-hoc 17sp |
| Menu subtext | `AppHeader.kt:193` | 13sp Medium, SynqPink | 13sp Normal, `PinkDeep` / `PinkPrimary` | 1 | Token swap |

### 8.2 HomeScreen (`features/home/HomeScreen.kt`)
| Element | File:Line | Current | Target from §4.6 | Tier | Reason |
|---|---|---|---|---|---|
| Screen background | `HomeScreen.kt:224` | `palette.background` + radial glow | `MaterialTheme.colorScheme.background`, remove radial glow | 1 | §4.6b: Delete decorative radial glow; theme owns bg |
| SectionHeader | `HomeScreen.kt:251, 266, 334, 356` | Private `SectionHeader` (20sp SemiBold, 24dp pad) | Shared `SectionHeader` (18sp Bold, 20dp pad) | 1 | §5: Component unification; eliminate duplicate |
| Match card container | `HomeScreen.kt:461` | 280×180dp, `shape=28.dp`, shadow 10dp, border 1dp, `StadiumNavy` | ~264×148dp, `shapes.large` (16dp), flat, `surfaceContainerLow` | 1 | §4.6 target: Match card ~264×148dp, 16dp radius |
| Match card scrim | `HomeScreen.kt:502` | Ad-hoc gradient with `StadiumNavy` | Shared scrim (`background` alpha 0 → 0.85) | 1 | §4.6b: Single scrim rule |
| Match status badge | `HomeScreen.kt:574` | 100dp pill, 10sp ExtraBold, 5dp dot | Pill, 11sp SemiBold (`labelSmall`), letterSpacing 0.4sp | 1 | §4.6: Never ExtraBold; no 10sp |
| Match title | `HomeScreen.kt:522` | 20sp ExtraBold, Color.White | 14sp Medium (`titleSmall`), onSurface | 1 | §4.6: Team 14sp Medium; eliminate ExtraBold |
| Match scores text | `HomeScreen.kt:532` | 14sp Medium, `0xFF94A3B8` | 18sp Bold + `tnum`, onSurface / `SportsAccent` | 1 | §4.6: Score 18sp Bold + tnum |
| Match summary | `HomeScreen.kt:551` | 13sp Bold, status.highlightColor | 12sp Medium (`labelMedium`), `SemanticWarning` | 1 | §4.6: Tournament/summary line 12sp |
| Recent song tile | `HomeScreen.kt:626` | 140×160dp, 20dp radius, text on black gradient | 128dp square, `shapes.medium` (12dp), text BELOW art | 2 (T2-3) | T2-3: Consistent hero tile anatomy, art uncovered |
| Recent song 3-dots | `HomeScreen.kt:643` | 30dp circle behind 18dp icon | 20dp bare icon on art (or translucent circle kept only over art) | 2 (T2-2) | T2-2: Bare trailing icon on rows; circle kept over art |
| Recent song title | `HomeScreen.kt:669` | 17sp Medium, White, over art | 13sp Medium (`bodySmall`), onSurface, below art | 2 (T2-3) | T2-3: Text below art |
| Recent song artist | `HomeScreen.kt:677` | 13sp Normal, White 70%, over art | 12sp Normal (`labelMedium`), onSurfaceVariant | 2 (T2-3) | T2-3: Subtitle below art |
| Jam card container | `HomeScreen.kt:706` | 140×160dp, 20dp radius, blue gradient | 128dp square, `shapes.medium` (12dp), `PinkGradient` | 2 (T2-6) | T2-6: Jam card matches hero tile anatomy |
| Trending song row | `HomeScreen.kt:777` | Card with `palette.surface`, 1dp border, 16dp radius | Flat row on background, 8dp vertical gap | 2 (T2-1) | T2-1: Remove per-row card/border boxiness |
| Trending song artwork | `HomeScreen.kt:790` | 54dp, 10dp radius | 48dp, `shapes.small` (8dp) | 1 | §4.6: List row artwork 48dp, 8dp radius |
| Trending song title | `HomeScreen.kt:798` | 16sp Medium, `palette.textPrimary` | 15sp Medium (`titleMedium`), onSurface | 1 | §4.6: Row title 15sp Medium |
| Trending song artist | `HomeScreen.kt:806` | 14sp Normal, `palette.textSecondary` | 13sp Normal (`bodySmall`), onSurfaceVariant | 1 | §4.6: Row subtitle 13sp Normal |
| Trending song 3-dots | `HomeScreen.kt:814` | 36dp filled circle behind 20dp icon | Bare 20dp icon, 48dp hit area, onSurfaceVariant | 2 (T2-2) | T2-2: Remove circle behind row 3-dots |

### 8.3 MusicScreen (`features/music/MusicScreen.kt`)
| Element | File:Line | Current | Target from §4.6 | Tier | Reason |
|---|---|---|---|---|---|
| Screen padding | `MusicScreen.kt:61` | 24dp horizontal | 20dp horizontal | 1 | §4.6: Screen horizontal padding 20dp |
| Section titles | `MusicScreen.kt:445` | Private `SectionTitle` (20sp SemiBold) | Shared `SectionHeader` (18sp Bold) | 1 | §5: Component unification |
| Genre filter pills | `MusicScreen.kt:374` | 12dp radius, ad-hoc colors | 32dp tall pill, 12sp Medium, 14dp pad, `surfaceContainerHighest` / `PinkPrimary` | 1 | §4.6: Uniform chip style; no rainbow colors |
| Featured release tile | `MusicScreen.kt:394` | 150×150dp, 16dp radius, text overlay | 128dp square, `shapes.medium`, text below art | 2 (T2-3) | T2-3: Consistent tile anatomy |
| NEW badge on release | `MusicScreen.kt:421` | 10sp ExtraBold, `DarkTertiary` (emerald) | 11sp SemiBold (`labelSmall`), `PinkPrimary` / container | 1 | §4.6: Status badge 11sp SemiBold, delete emerald |
| Artist circle | `MusicScreen.kt:459` | 88dp circle, 14sp SemiBold | 80dp circle, 13sp Medium name below | 1 | §4.6: Artist circle 80dp + 13sp Medium |
| Top Playlist row | `MusicScreen.kt:337` | 64dp art, card with border | Flat row on bg, 48dp art, 15sp/13sp text | 2 (T2-1) | T2-1: Flat song/playlist rows |

### 8.4 PopularArtistsScreen (`features/music/PopularArtistsScreen.kt`)
| Element | File:Line | Current | Target from §4.6 | Tier | Reason |
|---|---|---|---|---|---|
| Top bar title | `PopularArtistsScreen.kt:92` | 22sp Bold | 20sp SemiBold (`headlineSmall`) | 1 | Standard sheet/subpage title |
| Search text field | `PopularArtistsScreen.kt:125` | 12dp radius, surfaceVariant | `shapes.medium`, `surfaceContainerHighest` | 1 | Token swap for text inputs |
| Artist grid item circle | `PopularArtistsScreen.kt:254` | 100dp circle | 80dp circle | 1 | §4.6: Artist circle 80dp |
| Artist name | `PopularArtistsScreen.kt:270` | 15sp SemiBold | 13sp Medium (`bodySmall`) | 1 | §4.6: 13sp Medium under artist circle |
| Artist follower count | `PopularArtistsScreen.kt:278` | 12sp Normal, onSurfaceVariant | 12sp Normal (`labelMedium`), onSurfaceVariant 70% | 1 | Tertiary text contrast rule |

### 8.5 SearchScreen (`features/search/SearchScreen.kt`)
| Element | File:Line | Current | Target from §4.6 | Tier | Reason |
|---|---|---|---|---|---|
| Search text field | `SearchScreen.kt:97` | 16dp radius, surfaceVariant | `shapes.medium` (12dp), `surfaceContainerHighest` | 1 | Standardize input shape & container token |
| SectionHeader | `SearchScreen.kt:425` | Private `SectionHeader` (18sp Bold, 24dp pad) | Shared `SectionHeader` (18sp Bold, 20dp pad) | 1 | §5: Shared component |
| Recent search row | `SearchScreen.kt:224` | 15sp Medium, 20dp icon | 14sp Normal (`bodyMedium`), 16dp clear icon | 1 | §4.6 & screen notes: History rows bodyMedium |
| Category tile | `SearchScreen.kt:265` | 72dp tall, 12dp radius, 35% container alpha | Keep 72dp / 12dp; swap text to `titleSmall` Medium | 1 (T2-5) | T2-5: Already right; follows new container tokens |
| Search song row | `SearchScreen.kt:412` | Uses `SongItem` | Updated `SongItem` (flat, 48dp art, bare icon) | 2 (T2-1, T2-2) | Shared component update |

### 8.6 LibraryScreen (`features/library/LibraryScreen.kt`)
| Element | File:Line | Current | Target from §4.6 | Tier | Reason |
|---|---|---|---|---|---|
| SectionHeader | `LibraryScreen.kt:477` | Private copy (19sp Bold, count, 24dp pad) | Shared `SectionHeader` (18sp Bold, 20dp pad) | 1 | §5: Shared component |
| Filter tabs LazyRow | `LibraryScreen.kt:159` | 100dp pill, ad-hoc container | 32dp tall, 12sp Medium, `surfaceContainerHighest` / `PinkPrimary` | 1 | §4.6: Uniform chip styling |
| Liked Songs hero card | `LibraryScreen.kt:513` | 16dp radius, `SynqPinkGradient` | `shapes.large`, `PinkGradient` | 1 | Unified gradient token |
| Liked song row | `LibraryScreen.kt:557` | Card with border, 56dp art, circle 3-dots | Flat row on bg, 48dp art, 15sp/13sp, bare 3-dots | 2 (T2-1, T2-2) | T2-1 & T2-2: Flat row, bare icon |
| Playlist row item | `LibraryScreen.kt:870` | 56dp art, card with border | Flat row on bg, 48dp art, `shapes.small`, bare 3-dots | 2 (T2-1, T2-2) | T2-1: Flat playlist rows |
| Followed team row | `LibraryScreen.kt:936` | 48dp circle, border | 48dp circle, no border, 15sp Medium / 13sp Normal | 1 | §4.6b: Remove 1dp border |
| Empty state icon & text | `LibraryScreen.kt:986` | Ad-hoc text & colors | Pink icon (`PinkPrimary`), 14sp Normal, single friendly line | 1 | §6 UI-E: Empty state refinement |

### 8.7 AllPlaylistsScreen (`features/library/AllPlaylistsScreen.kt`)
| Element | File:Line | Current | Target from §4.6 | Tier | Reason |
|---|---|---|---|---|---|
| Page title | `AllPlaylistsScreen.kt:294` | 24sp Bold | 20sp SemiBold (`headlineSmall`) | 1 | §4.6: Subpage title 20sp SemiBold |
| Create playlist button | `AllPlaylistsScreen.kt:302` | Primary button | 44dp tall, `labelLarge`, `PinkPrimary` | 1 | §4.6: Button 44dp tall |
| Playlist grid / list items | `AllPlaylistsScreen.kt:376` | Card with border | Flat row on bg, 48dp art, bare 3-dots | 2 (T2-1, T2-2) | T2-1 & T2-2 |

### 8.8 ProfileScreen (`features/profile/ProfileScreen.kt`)
| Element | File:Line | Current | Target from §4.6 | Tier | Reason |
|---|---|---|---|---|---|
| User avatar hero | `ProfileScreen.kt:216` | 80dp circle, `AvatarGradient` | 80dp circle, `PinkGradientHero` | 1 | Token swap |
| Username | `ProfileScreen.kt:245` | 24sp Bold | 20sp SemiBold (`headlineSmall`) | 1 | Hierarchy refinement |
| Email / plan subtitle | `ProfileScreen.kt:252` | 14sp Normal, `SynqPink` | 13sp Normal, `PinkDeep` / `PinkPrimary` | 1 | Token swap |
| Settings card | `ProfileScreen.kt:320` | 16dp radius, 1dp border | `shapes.large`, `surfaceContainerLow`, no border | 1 | Fewer outlines per §4.6b |
| Settings item row | `ProfileScreen.kt:587` | 16sp Medium, 20dp icon | 15sp Medium (`titleMedium`), 20dp onSurfaceVariant icon | 1 | Typography & icon tokens |
| Logout button | `ProfileScreen.kt:660` | Red border / fill | 44dp tall, `labelLarge`, `SemanticLive` | 1 | Semantic token swap |

### 8.9 SportsScreen (`features/sports/SportsScreen.kt`)
| Element | File:Line | Current | Target from §4.6 | Tier | Reason |
|---|---|---|---|---|---|
| Filter chips (Live/Upcoming/Recent) | `SportsScreen.kt:85` | 100dp pill, ad-hoc colors | 32dp tall, 12sp Medium, selected = `SportsAccent` fill + `onSportsAccent` | 1 | §4.2 & §5: Sports filter chips use SportsAccent |
| Match card container | `SportsScreen.kt:165` | 16dp radius, 1dp border, shadow | `shapes.large`, `surfaceContainerLow`, no border | 1 | §4.6: Match card refinement |
| Format badge (TEST/ODI/T20) | `SportsScreen.kt:254` | 10sp ExtraBold, ad-hoc bg | 11sp SemiBold (`labelSmall`), letterSpacing 0.4sp, `SportsAccent` text | 1 | §4.6 & §5: Badge rules |
| LIVE badge | `SportsScreen.kt:270` | Red badge with pulsing dot | Keep `SemanticLive` (#EF4444) badge + pulse | 1 | §3.2 & §5: SemanticLive kept |
| Leading score numeral | `SportsScreen.kt:320` | 18sp Bold | 18sp Bold + `tnum`, `SportsAccent` | 1 | §4.2 & §4.6: Leading team score SportsAccent + tnum |
| Status / result text | `SportsScreen.kt:380` | 13sp Bold, amber/lime | 12sp Medium (`labelMedium`), `SemanticWarning` | 1 | Semantic warning token |

### 8.10 MatchDetailsScreen (`features/sports/MatchDetailsScreen.kt`)
| Element | File:Line | Current | Target from §4.6 | Tier | Reason |
|---|---|---|---|---|---|
| Top bar title | `MatchDetailsScreen.kt:105` | 18sp Bold | 18sp Bold (`titleLarge`) | 1 | **Keep as is** |
| Match details tabs (Info/Live/Scorecard/Squads) | `MatchDetailsScreen.kt:130` | Selected = `AccentBlue` (5566FF) | Selected = `SportsAccent` (`#C6F135` / `#4D7C0F`), 14sp Medium/SemiBold | 1 | §4.2: Sports tabs use SportsAccent |
| SectionCard wrapper | `MatchDetailsScreen.kt:400` | 16dp card + 1dp border + `CardHeaderBg` header band + dividers | Plain section title above one borderless container; separate rows with spacing | 2 (T2-4) | T2-4: Removes layered boxes & divider noise |
| Scoreboard numerals | `MatchDetailsScreen.kt:450` | 24sp Bold | 24sp Bold + `tnum` | 1 | §4.5: tnum on scores so they don't jitter |
| Scorecard table columns | `MatchDetailsScreen.kt:540` | Fixed dp columns, ad-hoc text | 12sp Medium (`labelMedium`) + `tnum`, `onSurfaceVariant` headers | 1 | Clean tabular alignment |
| Commentary over badge | `MatchDetailsScreen.kt:630` | 24dp circle, `AccentBlue` | 24dp circle, `SportsAccent` container / text | 1 | Token swap |
| Squad player row | `MatchDetailsScreen.kt:1100` | 16sp/14sp in card | 15sp Medium / 13sp Normal, flat row | 1 | §4.6: Squad rows titleMedium + bodySmall |

### 8.11 PlaylistScreen (`features/music/PlaylistScreen.kt`)
| Element | File:Line | Current | Target from §4.6 | Tier | Reason |
|---|---|---|---|---|---|
| Cover art | `PlaylistScreen.kt:220` | 200×200dp, 16dp radius | 200×200dp, 16dp radius | 1 | **Keep as is** (§3.2) |
| Playlist title | `PlaylistScreen.kt:481` | 26sp Bold | 20sp SemiBold (`headlineSmall`) | 1 | §4.6: Page title headlineSmall |
| Play FAB / button | `PlaylistScreen.kt:510` | `SynqPink` | 48dp circle, `PinkPrimary` | 1 | §4.6 & §6 notes: Play FAB keeps PinkPrimary |
| Sort / Filter chip | `PlaylistScreen.kt:550` | Ad-hoc chip style | 32dp tall, 12sp Medium, horizontal pad 14dp | 1 | §4.6: Standard chip style |
| Song list rows | `PlaylistScreen.kt:772` | Uses `SongItem` | Updated `SongItem` (flat, 48dp art, bare 3-dots) | 2 (T2-1, T2-2) | Shared component update |

### 8.12 AlbumScreen (`features/music/AlbumScreen.kt`)
| Element | File:Line | Current | Target from §4.6 | Tier | Reason |
|---|---|---|---|---|---|
| Cover art | `AlbumScreen.kt:180` | 200×200dp | 200×200dp | 1 | **Keep as is** (§3.2) |
| Album title | `AlbumScreen.kt:210` | 24sp Bold | 20sp SemiBold (`headlineSmall`) | 1 | §4.6: Page title headlineSmall |
| Stat badges (year, songs) | `AlbumScreen.kt:251, 268` | 10sp ExtraBold, ad-hoc pill | 11sp SemiBold (`labelSmall`), `surfaceContainerHighest` | 1 | §4.6: Badge targets |
| Song rows | `AlbumScreen.kt:482` | Numbered row, card border | Flat row, 15sp Medium / 13sp Normal, bare 3-dots | 2 (T2-1, T2-2) | T2-1 & T2-2 |

### 8.13 ArtistScreen (`features/music/ArtistScreen.kt`)
| Element | File:Line | Current | Target from §4.6 | Tier | Reason |
|---|---|---|---|---|---|
| Artist hero avatar | `ArtistScreen.kt:220` | 140dp circle | 140dp circle | 1 | **Keep as is** (§3.2) |
| Artist name | `ArtistScreen.kt:387` | 24sp Bold | 20sp SemiBold (`headlineSmall`) | 1 | §4.6: Page title headlineSmall |
| Album hero tiles | `ArtistScreen.kt:452` | 140×160dp with text overlay | 128dp square, text below art | 2 (T2-3) | T2-3: Consistent tile anatomy |

### 8.14 SongDetailScreen (`features/music/SongDetailScreen.kt`)
| Element | File:Line | Current | Target from §4.6 | Tier | Reason |
|---|---|---|---|---|---|
| Cover art | `SongDetailScreen.kt:150` | 240×240dp | 240×240dp | 1 | **Keep as is** (§3.2) |
| Song title | `SongDetailScreen.kt:180` | 22sp Bold | 20sp SemiBold (`headlineSmall`) | 1 | Hierarchy refinement |
| Artist chips | `SongDetailScreen.kt:307` | Ad-hoc chip | 32dp tall, 12sp Medium chip | 1 | §4.6: Standard chip style |

### 8.15 ListeningHistoryScreen (`features/music/history/ListeningHistoryScreen.kt`)
| Element | File:Line | Current | Target from §4.6 | Tier | Reason |
|---|---|---|---|---|---|
| Top bar title | `ListeningHistoryScreen.kt:80` | 20sp SemiBold | 20sp SemiBold (`headlineSmall`) | 1 | **Keep as is** |
| Date header | `ListeningHistoryScreen.kt:160` | 16sp Bold, onSurface | 14sp SemiBold (`titleSmall`), onSurfaceVariant | 1 | Secondary section hierarchy |
| History song row | `ListeningHistoryScreen.kt:177` | Card with border, 54dp art | Flat row on bg, 48dp art, 15sp/13sp, bare 3-dots | 2 (T2-1, T2-2) | T2-1 & T2-2 |

### 8.16 AuthScreen (`features/auth/AuthScreen.kt`)
| Element | File:Line | Current | Target from §4.6 | Tier | Reason |
|---|---|---|---|---|---|
| Hero title | `AuthScreen.kt:70` | 28sp Bold | 28sp Bold (`headlineLarge`) | 1 | **Keep as is** (Auth hero allowed 28sp per §4.5) |
| Subtitle | `AuthScreen.kt:85` | 14sp Normal | 13sp Normal (`bodySmall`), onSurfaceVariant | 1 | Ratio refinement |
| Text inputs | `AuthScreen.kt:120, 140` | 12dp radius, surfaceVariant | `shapes.medium`, `surfaceContainerHighest`, outline border | 1 | Text inputs keep border per §4.6b |
| Submit button | `AuthScreen.kt:180` | Primary button | 48dp tall primary button, `labelLarge`, `PinkGradient` | 1 | Primary button target |

### 8.17 FullPlayerSheet (`features/music/FullPlayerSheet.kt`)
| Element | File:Line | Current | Target from §4.6 | Tier | Reason |
|---|---|---|---|---|---|
| Ambient dynamic bg | `FullPlayerSheet.kt:1860` | Palette-driven ambient glow | Palette-driven ambient glow | 1 | **Keep as is** (§3.2 / §79: DO NOT TOUCH) |
| Lyrics display | `FullPlayerSheet.kt:1435` | Live lyrics text sizes | Live lyrics text sizes | 1 | **Keep as is** (§3.2 / §79 / D3: DO NOT TOUCH) |
| Scrub bar behaviour | `FullPlayerSheet.kt:1949` | Seek scrub bar | Seek scrub bar | 1 | **Keep as is** (§3.2 / §79: DO NOT TOUCH) |
| Track title | `FullPlayerSheet.kt:373` | 20sp Bold | 20sp SemiBold (`headlineSmall`) | 1 | Weight reduction (Bold → SemiBold) |
| Artist name | `FullPlayerSheet.kt:380` | 15sp Medium, onSurfaceVariant | 14sp Normal (`bodyMedium`), onSurfaceVariant | 1 | Hierarchy contrast |
| Floating bottom dock | `FullPlayerSheet.kt:1026` | `RoundedCornerShape(32.dp)`, surface | Pill / `shapes.extraLarge`, `surfaceContainerHigh` | 1 | Standardize floating dock container |
| Sleep timer card | `FullPlayerSheet.kt:1672` | 16dp radius, 1dp border | `shapes.large`, `surfaceContainerHigh`, no border | 1 | Fewer outlines |
| Queue item row | `FullPlayerSheet.kt:1560` | 48dp art, card border | Flat row, 48dp art, bare 3-dots | 2 (T2-1, T2-2) | T2-1 & T2-2 |

### 8.18 JamScreen (`features/music/jam/JamScreen.kt`) & JamIntroDialog (`JamIntroDialog.kt`)
| Element | File:Line | Current | Target from §4.6 | Tier | Reason |
|---|---|---|---|---|---|
| Background | `JamScreen.kt:106` | `0xFF0F0C13` / `0xFFF8FAFC` | `MaterialTheme.colorScheme.background` | 1 | Token swap |
| Cards | `JamScreen.kt:107` | `0xFF181320` / White, 16dp radius | `surfaceContainerLow`, `shapes.large` | 1 | Token swap & radius standardization |
| Accent color | `JamScreen.kt:76` | Private `Accent = Color(0xFFFF2D7A)` | `PinkPrimary` | 1 | Token swap |
| Session code banner | `JamScreen.kt:310` | 24sp Bold + ExtraBold elements | 20sp Bold + `tnum`, `PinkPrimary` | 1 | Typography & tnum alignment |
| Member avatar pill | `JamScreen.kt:1196` | Circle avatar, 14sp SemiBold | Circle avatar with `PinkGradientHero`, 13sp Medium | 1 | Token swap & weight reduction |
| Jam queue row | `JamScreen.kt:1271` | Card with border, 48dp art | Flat row on bg, 48dp art, bare 3-dots | 2 (T2-1, T2-2) | T2-1 & T2-2 |
| JamIntroDialog container | `JamIntroDialog.kt:59` | `0xFF1A1420`, 20dp radius | `surfaceContainerHigh`, `shapes.extraLarge` (24dp) | 1 | Standardize dialog container |
| JamIntroDialog title | `JamIntroDialog.kt:121` | 22sp Bold | 20sp SemiBold (`headlineSmall`) | 1 | Standard sheet/dialog title target |

### 8.19 Shared Row Component: SongItem (`features/music/components/SongItem.kt`)
| Element | File:Line | Current | Target from §4.6 | Tier | Reason |
|---|---|---|---|---|---|
| Row container | `SongItem.kt:49` | Card background, 1dp border, 12dp radius | Flat row on bg, pressed highlight = `surfaceContainerLow`, 8dp gap | 2 (T2-1) | T2-1: Core restyle — flat song rows |
| Artwork | `SongItem.kt:60` | 54dp, 10dp radius | 48dp, `shapes.small` (8dp) | 1 | §4.6: List row artwork 48dp, 8dp radius |
| Title | `SongItem.kt:72` | 16sp Medium, onBackground | 15sp Medium (`titleMedium`), onSurface | 1 | §4.6: Row title 15sp Medium |
| Subtitle (Artist • Album) | `SongItem.kt:85` | 13sp Normal, onSurfaceVariant | 13sp Normal (`bodySmall`), onSurfaceVariant | 1 | **Keep as is** (already matches target) |
| Duration text | `SongItem.kt:98` | 12sp Normal, onSurfaceVariant | 12sp Normal (`labelMedium`) + `tnum`, onSurfaceVariant 70% | 1 | Contrast through color & tnum |
| Trailing 3-dots button | `SongItem.kt:110` | 34dp circle behind 18dp icon | 20dp bare icon, 48dp hit area, onSurfaceVariant | 2 (T2-2) | T2-2: Bare trailing menu icon |
| Options sheet container | `SongItem.kt:180` | `RoundedCornerShape(24.dp)`, surface | `shapes.extraLarge`, `surfaceContainerHigh` | 1 | Sheet styling convention |

### 8.20 Shared Sheet: AddToPlaylistSheet (`features/music/components/AddToPlaylistSheet.kt`)
| Element | File:Line | Current | Target from §4.6 | Tier | Reason |
|---|---|---|---|---|---|
| Sheet background | `AddToPlaylistSheet.kt:49, 114` | `Color(0xFF18131B)` | `surfaceContainerHigh` (`#261E2B`) | 1 | Token swap (visual north star pixel-similar) |
| Sheet title | `AddToPlaylistSheet.kt:170` | 22sp Bold | 20sp SemiBold (`headlineSmall`) | 1 | §4.6: Sheet title 20sp SemiBold |
| Search box | `AddToPlaylistSheet.kt:125` | `Color(0xFF1E1724)`, 12dp radius | `surfaceContainerHighest`, `shapes.medium` | 1 | Token swap |
| Playlist rows | `AddToPlaylistSheet.kt:483, 564` | Row with `SynqPink` check / selection | `PinkPrimary` check, 15sp Medium title, 13sp subtitle | 1 | Visual north star: token swap only |

---

## 9. Tier-2 Composition Tweaks Status (§4.6b)

The following Tier-2 items were formally ticked and approved by the owner in `UI_REFINEMENT.md`:

| Status | ID | Change Description | Target Implementation |
|---|---|---|---|
| ☑ **APPROVED** | **T2-1** | **Flat song rows:** remove the per-row card (background + border + rounded clip) on `TrendingSongRow`, history rows, search results, playlist rows. Rows sit on background with 8dp vertical gap; pressed state = `surfaceContainerLow` highlight. | Apply in UI-B (`SongItem.kt`) and UI-C screens. |
| ☑ **APPROVED** | **T2-2** | **Bare trailing menu icon:** remove filled circle behind row three-dot buttons (11 places); keep 48dp hit area, 20dp bare icon, `onSurfaceVariant`. Dark translucent circle stays **only** when button sits directly over artwork. | Apply in UI-B (`SongItem.kt`) and UI-C screens. |
| ☑ **APPROVED** | **T2-3** | **Text below artwork in hero tiles** (`RecentlyPlayed`, `Featured`, `BestOf`, `Jam`) instead of overlaid on a black gradient; artwork square 128dp, `shapes.medium` (12dp); 3-dots stays overlaid on the art. | Apply in UI-C (`HomeScreen`, `MusicScreen`, `ArtistScreen`). |
| ☑ **APPROVED** | **T2-4** | **One container level in Match Details:** `SectionCard` (1dp border + 16dp card) holds a filled `CardHeaderBg` header band and divider lines. Replace with a plain section title above one borderless container; separate rows with spacing, not dividers. | Apply in UI-C (`MatchDetailsScreen`). |
| — **VERIFIED** | **T2-5** | *Removed after verification:* Search category tiles are already 72dp tall, 12dp radius; colours follow container roles automatically. | No structural change needed. |
| ☑ **APPROVED** | **T2-6** | **Home Jam card uses the same tile anatomy** as other hero tiles (128dp square, label below, `PinkGradient`). | Apply in UI-C (`HomeScreen`). |

---

## 10. Owner Decisions & Documented Guidelines

Owner decisions established for Phase 9.6:

1. **Tiny Text (9sp in `JamScreen.kt:1260, 1687`):**
   - **APPROVED:** Change 9sp text to 11sp (`labelSmall`).

2. **Uppercase + Letter-Spacing 0.4sp:**
   - **APPROVED ONLY** for status/format badges (`LIVE`, `TEST`/`ODI`/`T20`, `UPCOMING`, `POST MATCH`, `HOST`, `NEW`).
   - NOT for chips, pills, or tabs.

3. **Screen Titles Scale:**
   - Screen titles are **24sp Bold** (`headlineMedium`), including `AppHeader`.

4. **Hero Tile Anatomy (T2-3):**
   - Text below artwork: title is **14sp Medium**, artist/subtitle is **12sp Normal**.

5. **Color Literals Target:**
   - Baseline target remains `≤ 25` per spec.

6. **Home Match Status Colours (`MatchStatus` enum in `HomeScreen.kt`):**
   - `LIVE` = `SemanticLive` red (`#EF4444`)
   - `UPCOMING` = `SportsAccent`
   - `POST_MATCH` = `onSurfaceVariant`
   - Summary highlight: `LIVE` = `SemanticWarning`, others = `SportsAccent`.

7. **SportsAccent Palette (D11):**
   - Defined solely in `Color.kt`. Initial value is Lime (`#C6F135` dark / `#4D7C0F` light). Owner will test on-device after Sports screens are restyled in UI-C.

---

## 11. Next Sub-Phase: UI-A (Foundations)

UI-0 audit is COMPLETE.
**Next sub-phase:** **UI-A (Foundations)**
- Touch only: `ui/theme/*`, `ui/motion/*`, `ui/GlassSurface.kt` (accent lines only), `ui/SynqBrand.kt` (aliases).
