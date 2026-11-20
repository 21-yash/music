# UI_REFINEMENT.md — Synq UI refinement spec
 
> **Audience:** coding agents working on `android/app/src/main/java/com/synq/`.
> **Owner's goal:** make the existing UI feel calmer, better-organised, more consistent and more alive — like a professional music app (Spotify-class) — **without changing layout structure, features, buttons, navigation or logic.**
> This is a *restyle*, not a redesign. If a change would alter behaviour, STOP and ask.
 
---
 
## 0. How to use this file
 
1. Read this whole file before touching code.
2. Work **one phase at a time** (§6). After each phase: run the gates, write the report (§10), **stop and wait for the owner's approval.**
3. Add this block to `ROADMAP.md` (owner pastes it; agents never self-promote phases):

```md
## Phase 9.6 — UI refinement
**Status:** CURRENT   (sub-phases UI-0 … UI-F are defined in UI_REFINEMENT.md)

- Restyle only: tokens, typography, density, motion, consistency
- Frozen: bottom nav bar + mini player (see UI_REFINEMENT.md §3)
**Do not build in this phase:** anything from Phase 10+ (notifications, widgets, FCM).
```

4. Existing `ROADMAP.md` rules still apply: do not implement anything from a `PENDING` phase.

---

## 1. Diagnosis (why this work exists)

Measured on the current code (baseline, commit `6a93ed9`):

| Problem | Evidence |
|---|---|
| Everything is bold | 150× `Bold`, 16× `ExtraBold`, 1× `Black`, 95× `SemiBold` vs only 50× `Medium`, 13× `Normal` (~80% of text is heavy → no hierarchy) |
| Typography theme is bypassed | ~340 hardcoded `fontSize = X.sp` vs ~53 uses of `MaterialTheme.typography`; 20 distinct sizes incl. 9, 10, 10.5, 11.5, 13.5 |
| Palette is stock Tailwind | `0F172A` slate-900, `1E293B` slate-800, `6366F1` indigo-500, `EC4899` pink-500 → "AI-generated" look |
| Colour identity is fragmented | 6 different pinks (`EC4899 FF2D7A FF2A5F FF2D55 DB2777 F472B6`); indigo and blue/green/purple tiles unrelated to brand; ~30 hardcoded `0F172A` literals |
| Shapes inconsistent | 12 different corner radii (2…28dp), ~190 hardcoded `RoundedCornerShape(n.dp)` |
| Duplicated components | 3 separate private `SectionHeader`s (Home, Library, Search) |
| No life | 0 spring/tween specs, 0 haptics, 0 `animateItem`, 0 shimmer/skeleton, spinners only; 27 of 35 `items(...)` calls have no `key` |
| UI too large *and* undifferentiated | Header padding 24dp; Home match card 280×180 with 28dp radius and a 20sp **ExtraBold** score; 10sp ExtraBold badges; song title 16–17sp on top of artwork |
| Boxy | 33× `.border(1.dp …)`; every song row is its own bordered, rounded card with a filled circle behind the three-dots (11 places); text sits on a black gradient over artwork |
| Match Details ignores the theme | `MatchDetailsScreen.kt` defines private colours (`AccentBlue`, `CardHeaderBg`, `CardBlue`, `BorderLight`, `AvatarBg`, `AccentRed`) — same treatment as `HomePalette` |
| Home ignores the theme | `HomeScreen.kt` has a private `HomePalette` (`AccentIndigo 6366F1`, `StadiumNavy 0F172A`, own surfaces + radial glow) — theme changes would not reach Home |

**Visual north star (keep this feel):** the **three-dot song menu** and the **Add-to-Playlist sheet** — hot pink `#FF2D7A` accent, deep pink-tinted dark cards, soft rounded corners. The rest of the app should feel like it belongs to those two.

---

## 2. Hard rules (never violate)

1. **No logic changes.** Do not edit ViewModels, repositories, DAOs, DTOs, `Screen.kt`, nav routes, `onClick`/callback bodies, state classes, DI modules, workers, Socket.IO, Media3 code.
2. **No removed or renamed UI elements.** Every button, icon-button, menu item, tab, chip, FAB, switch, and dialog that exists today must still exist and do the same thing.
3. **No layout restructuring** — except the Tier-2 items in §4.6b that the owner has explicitly ticked. Don't turn a `Row` into a `Column`, add/remove screens, reorder sections, or change navigation structure. Otherwise only parameters of existing modifiers (padding, size, shape, colour, weight, font size) may change.
4. **No new dependencies** in `build.gradle*` / `libs.versions.toml` (everything needed is in Compose BOM `2025.06.01`).
5. **Touch targets stay ≥ 48dp.** You may shrink an icon *inside* an `IconButton`; do not shrink the clickable box below 48dp (use `minimumInteractiveComponentSize()` if needed).
6. **Both themes must work.** The app has a dark/light toggle (`RootViewModel.isDarkTheme`). Every colour change needs a light-theme value.
7. **One concern per commit; one screen per commit** in UI-C. Commit message format: `ui(<phase>): <screen/component> — <what>`.
8. **Ambiguity → ask.** If a literal colour/size doesn't clearly map to a token or role, list it in the report under "Needs decision" instead of guessing.
9. **Don't "improve" things not listed here** (no new features, no copy rewrites beyond §6 UI-E, no icon swaps).
10. If a needed change falls under a later sub-phase, name it and stop — don't build it early.

---

## 3. Frozen & Keep lists

### 3.1 FROZEN — files must be byte-identical after every phase

| File | Why |
|---|---|
| `navigation/AppBottomNavBar.kt` | Owner is happy with it |
| `features/music/components/MiniPlayer.kt` | Owner is happy with it |

Verified: these two files hardcode their own sizes/radii and **do not read** `MaterialTheme.typography/colorScheme/shapes`. They consume only `glassSurface`, `glassContentColors()`, `GlassAccent`, `GlassProgressBrush` (from `ui/GlassSurface.kt`).
→ Global theme changes are safe for them. The **only** intended visible change inside them is via token remap: selected nav icon and mini-player progress bar turn pink instead of indigo (§4.2). Do not edit `GlassSurface.kt` except the accent value lines (§4.2); the glass *fill* colour stays as is (see §9, decision D2).

### 3.2 KEEP AS IS (do not restyle)

- App logo drawable and the squircle logo clip in `SynqBrand.kt` (wordmark "q" colour follows token, see D1).
- Provider brand icons in Search (JioSaavn green, YouTube red).
- Album/artist/playlist artwork, circular artist avatars (size may follow §4.6, shape stays).
- Fullscreen player's **dynamic ambient background**, scrub bar behaviour, and **lyrics text size/line-height** (readability). Literal→token swaps and weight cleanup in `FullPlayerSheet.kt` are allowed; palette-driven colours are not touched.
- Semantic colours: **Live/Error red `#EF4444`**, **Synced/Success green `#10B981`**, **warning amber** (match status text). Move them into named semantic tokens (§4.2) — don't recolour.
- Existing `SynqLoadingIndicator` (keep for buttons/inline loads; skeletons are added *in addition*).
- All user-facing strings, except those listed in UI-E.
- Three-dot menu and Add-to-Playlist sheet **appearance**: token swap only; result must look pixel-similar to today.

---

## 4. Design tokens (single source of truth)

All values below are **proposals the owner can tweak in one place** (`ui/theme/`). Agents must not hardcode any of them anywhere else.

### 4.1 File layout (create/modify)

```
ui/theme/Color.kt       – raw palette + semantic tokens         (rewrite)
ui/theme/Gradients.kt   – named Brush objects                    (new)
ui/theme/Shapes.kt      – MaterialTheme.shapes                   (new)
ui/theme/Type.kt        – type scale                             (rewrite values)
ui/theme/Theme.kt       – colour schemes wired to tokens         (edit)
ui/theme/Spacing.kt     – spacing + density constants            (new)
ui/motion/Motion.kt     – pressScale, specs, haptic helper       (new)
ui/motion/Skeleton.kt   – shimmer modifier + skeleton shapes     (new)
```

### 4.2 Colour

**The pink family (exactly four, defined once):**

| Token | Hex | Role |
|---|---|---|
| `PinkPrimary` | `#FF2D7A` | The accent (from the 3-dot menu / Jam). Buttons, selected states, active icons, links, FAB |
| `PinkSoft` | `#FF6584` | Gradient end, secondary highlights, secondary accents |
| `PinkGlow` | `#FF8FA3` | Light tint: highlights, text-on-dark accents, gradient start in hero gradients |
| `PinkDeep` | `#D0165E` | Pressed/containers, gradient end in hero gradients, pink **text on light theme** (contrast) |

No other pink hex may appear in code. The logo's old `#FF2D55` becomes `PinkPrimary` for everything coded (D1).

**Dark theme (tinted to match pink — replaces slate):**

| Token / Material slot | Hex |
|---|---|
| `background` | `#141116` |
| `surface`, `surfaceContainer` | `#1E1822` |
| `surfaceContainerLowest` | `#100D12` |
| `surfaceContainerLow` (cards on bg) | `#1A141E` |
| `surfaceContainerHigh` (menus, sheets, dialogs) | `#261E2B` |
| `surfaceContainerHighest` (unselected chips, search fields) | `#2E2535` |
| `surfaceVariant` | `#2E2535` |
| `onBackground`, `onSurface` | `#F7F3F8` |
| `onSurfaceVariant` (secondary text) | `#A79EAD` |
| `outline` / `outlineVariant` | `#5A4F61` / `#3A2F42` |
| `primary` / `onPrimary` | `PinkPrimary` / `#FFFFFF` |
| `primaryContainer` / `onPrimaryContainer` | `PinkDeep` / `#FFFFFF` |
| `secondary` / `onSecondary` | `PinkSoft` / `#FFFFFF` |
| `secondaryContainer` / `onSecondaryContainer` | `#2E2535` / `#F7F3F8` |
| `tertiary` / `onTertiary` | `PinkGlow` / `#3A0A1C` |
| `tertiaryContainer` / `onTertiaryContainer` | `#3A1B2C` / `#F7F3F8` (must be set — Search tiles read it) |
| `error` | `#EF4444` |

**Light theme (tinted, not slate):**

| Slot | Hex |
|---|---|
| `background` | `#FBF4F7` |
| `surface`, `surfaceContainerLowest` | `#FFFFFF` |
| `surfaceContainerLow` / `surfaceContainer` | `#FBF7F9` / `#F7EFF3` |
| `surfaceContainerHigh` / `Highest` / `surfaceVariant` | `#F3E8EE` / `#EDDFE7` / `#F3E8EE` |
| `onBackground`, `onSurface` | `#1C1420` |
| `onSurfaceVariant` | `#6B6070` |
| `outline` / `outlineVariant` | `#B9A9B4` / `#E6D6DF` |
| `primary`, `secondary`, `tertiary` | same pink tokens as dark |
| `tertiaryContainer` / `onTertiaryContainer` | `#F6D5E2` / `#1C1420` |
| Pink **text** on light surfaces | use `PinkDeep` (contrast), fills stay `PinkPrimary` |

> Set **all** `surfaceContainer*` slots explicitly in both schemes. Material3 dropdown menus, sheets, dialogs and cards read them; leaving defaults gives off-brand purple-grey.

**Sports accent (scoped; not part of the four pinks):** the Sports tab, `SportsScreen` and `MatchDetailsScreen` use a second accent, because an all-pink sports screen reads poorly. Token `SportsAccent` — **dark `#C6F135` (lime), light `#4D7C0F`**; `onSportsAccent` dark `#141116`, light `#FFFFFF` (placeholder — owner picks the final colour by testing; see D11).
Use it **only** for: selected filter chips (Live/Upcoming/Recent), match-detail tabs (Info/Live/Scorecard/Squads), format badge text (TEST/ODI/T20), the leading team's score, in-match progress/highlight elements.
Never use it for buttons, FABs, dialogs, links, or anything outside the two sports screens. `SemanticLive` red stays for LIVE badges. Pink remains the app-wide accent (buttons, nav, headers).

**Semantic tokens (not pink, not themable away):**
`SemanticLive = #EF4444`, `SemanticSuccess = #10B981`, `SemanticWarning = <the amber currently used for match status — find it, name it>`. `DarkTertiary` emerald and all indigo (`6366F1`, `4F46E5`, `818CF8`) are **deleted**. Exception: the Home Jam tile's blue gradient (`JamGradient`, D12).

**Compatibility aliases (so untouched files keep compiling) — keep until UI-F, then remove:**
```kotlin
val GlassAccent       = PinkPrimary      // in GlassSurface.kt (value line only)
val GlassAccentPink   = PinkSoft         // in GlassSurface.kt (value line only)
// GlassProgressBrush already = horizontalGradient(GlassAccent, GlassAccentPink) → becomes pink→soft-pink automatically
val SynqPink          = PinkPrimary      // SynqBrand.kt
val SynqPinkLight     = PinkGlow
val SynqPinkDark      = PinkDeep
// FullPlayerSheet BrandPink (FF2A5F), Jam/JamIntroDialog private `Accent` (FF2D7A) → replace usages with tokens in UI-C
```
`SynqBackground`/`SynqSurface` in `SynqBrand.kt` are currently unused → delete them (the theme now owns those values).

### 4.3 Gradients (`Gradients.kt` — exactly these)

```kotlin
val PinkGradient      = Brush.linearGradient(listOf(PinkPrimary, PinkSoft))              // buttons, hero chips
val PinkGradientHero  = Brush.linearGradient(listOf(PinkGlow, PinkPrimary, PinkDeep))    // logo-like hero surfaces, avatars
val PinkGradientSoft  = Brush.verticalGradient(listOf(PinkPrimary.copy(alpha = .28f), Color.Transparent)) // tinted tops of headers/tiles
val JamGradient       = Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF06B6D4)))   // Home Jam tile ONLY; deliberate blue exception (D12); defined here, nowhere else
// Scrims use MaterialTheme.colorScheme.background, never a hardcoded dark hex.
```
Rule: **a gradient is an accent, not a surface.** Max one gradient element per viewport region; lists/rows never use gradients.

### 4.4 Shapes (`Shapes.kt`) and radius mapping

```kotlin
Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp),
       large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(24.dp))
// + pill = RoundedCornerShape(50) via CircleShape / RoundedCornerShape(percent = 50)
```

| Existing literal | → Use |
|---|---|
| 6, 8 | `shapes.small` |
| 10, 12, 14 | `shapes.medium` |
| 16, 20 | `shapes.large` |
| 22, 24, 28 | `shapes.extraLarge` |
| 100 / chips / pills | `CircleShape` or pill |
| 2 (progress bars/hairlines), logo squircle `0.22f` | leave |
| Frozen files | leave |

### 4.5 Typography — Spotify-like hierarchy

**Principle: hierarchy comes from size and colour (`onSurface` vs `onSurfaceVariant`), not from weight.** Inter stays the only family (D6).

Rewrite `AppTypography` to this scale and **use roles, not literals**:

| Role (M3 slot) | Size / line-height | Weight | Use for |
|---|---|---|---|
| `headlineLarge` | 28 / 34 | Bold | Auth hero only |
| `headlineMedium` | 24 / 30 | Bold | **Screen titles** ("Good afternoon", "Discover Music", "Cricket") |
| `headlineSmall` | 20 / 26 | SemiBold | Sheet/dialog titles; playlist/album/artist page titles |
| `titleLarge` | 18 / 24 | Bold | **Section headers** ("Recently Played", "Trending Music") |
| `titleMedium` | 15 / 20 | **Medium** | Item titles: song, playlist, artist, team names |
| `titleSmall` | 14 / 20 | Medium | Compact item titles, tab labels (selected may use SemiBold) |
| `bodyLarge` | 16 / 24 | Normal | Paragraph text, dialog body |
| `bodyMedium` | 14 / 20 | Normal | Default body |
| `bodySmall` | 13 / 18 | Normal | **Secondary line** under titles (artist • album), `onSurfaceVariant` |
| `labelLarge` | 14 / 20 | SemiBold | Buttons |
| `labelMedium` | 12 / 16 | Medium | Chips, badges, "See all", timestamps |
| `labelSmall` | 11 / 14 | Medium | Smallest allowed text (counts, tiny badges) |

**Weight rules**
- `Bold` allowed only for: screen titles, section headers, brand wordmark, scoreboard numerals (via `.copy(fontWeight = Bold)`), big hero numbers.
- `SemiBold` allowed only for: buttons, selected tab/chip label, badge text, sheet titles.
- **`ExtraBold` and `Black`: banned everywhere** (except frozen files).
- Everything else is `Medium` or `Normal`.
- **A single list row has at most one non-Normal weight** (title Medium + subtitle Normal).
- Numbers that change or align (scores, timers, durations, counts): add `fontFeatureSettings = "tnum"`.

**How to pick a size — role first, never "one step down":**
1. Identify the element's **role** (screen title, section header, row title, row subtitle, badge, score, button…).
2. Take the **target from §4.6 for that role** — an absolute value, not a percentage of what's there now.
3. If the current value is far from the target (e.g. a `10sp ExtraBold` badge, a `28dp`-radius card), **jump straight to the target.** Do not move it one notch. If the current value is *already* right, leave it.
4. Only then map the target to the closest type role above.
Old numbers (9, 10.5, 13.5, 17, 19, 22…) are symptoms, not inputs — they are never a reason to choose a new value.

Literal `fontSize`/`fontWeight` allowed **only** (a) in frozen files, (b) in lyrics, (c) as `.copy(...)` on a role with a one-line comment why.

### 4.6 Targets by element type (target-first, replaces "shrink by %")

**Design from the target, not from the current value.** Spotify-class apps feel refined because every element type has *one* size used everywhere, and because hierarchy ratios are stable — not because everything is 12% smaller. Ratio to preserve: **screen title : section header : row title : secondary text ≈ 24 : 18 : 15 : 13.**

| Element type | Target | Notes |
|---|---|---|
| Screen title | 24sp Bold | One per screen |
| Header subtitle | 13sp Normal, `onSurfaceVariant` | |
| Section header | 18sp Bold; "See all" 12sp Medium pink | Section gap 24dp; header→content 12dp |
| Row title / subtitle | 15sp Medium / 13sp Normal | Subtitle always `onSurfaceVariant` |
| List row artwork | 48dp, `shapes.small` (8) | Row height ≥ 56dp incl. padding |
| Row trailing icon | 20dp bare icon, 48dp hit area | See T2-2 |
| Hero tile artwork (Recently Played, Featured, Best Of, Jam) | 128dp square, `shapes.medium` (12) | One size for **all** horizontal tile rows; text per T2-3 |
| Artist circle | 80dp + 13sp Medium name below | |
| Match card (Home "Live Action") | ~264 × 148dp, `shapes.large` (16) | Team 14sp Medium, score 18sp Bold + `tnum`, tournament line 12sp |
| Status / format badge | 11sp SemiBold, letter-spacing 0.4sp, pill | Never ExtraBold; no 10sp |
| Chip | 32dp tall, 12sp Medium, horizontal padding 14dp | Selected = pink fill |
| Button | 44dp tall (primary 48), `labelLarge` | Hit area ≥ 48dp |
| Tab label | 14sp Medium; selected SemiBold + pink | |
| Sheet / dialog title | 20sp SemiBold | |
| Screen horizontal padding | 20dp | Header top 12dp, bottom 8dp |
| Card inner padding | 16dp (compact rows 12dp) | |
| Icon (decorative / in-row) | 20dp; 18dp in chips/badges; 16dp minimum | |
| Corner radii | per §4.4 only | |
| Frozen files, lyrics, fullscreen artwork | unchanged | |

**Deviation rule:** ±2dp/±1sp from a target needs no comment. Anything else must be justified in the audit table (§6 UI-0) and approved by the owner. If an element type isn't in this table, the agent proposes a target in the audit — it does not invent one silently.

### 4.6b Hierarchy & polish — what makes it better, not just smaller

Shrinking alone makes an app look cramped. These are the things that produce the "professional" feel. They are split into two tiers.

**Tier 1 — styling only (approved by default):**
- **Contrast through colour, not weight:** primary text `onSurface`; secondary `onSurfaceVariant`; tertiary (timestamps, counts) `onSurfaceVariant` at 70%. One bright element per row at most.
- **Whitespace rhythm:** only 4 / 8 / 12 / 16 / 20 / 24 dp spacings; 24dp between sections, 12dp between header and content, 8dp between list rows. Consistent left edge (20dp) for every title, row and section.
- **Fewer outlines:** remove `1.dp` borders from rows and tiles (33 `.border(1.dp…)` in the code today); keep borders only on text inputs, dialogs and selected states.
- **No default shadows/elevation; no glow:** delete decorative radial glows and double gradients; use `PinkGradientSoft` once at the top of a screen at most.
- **Single scrim:** text on images uses one shared scrim (`background` colour, alpha 0 → 0.85), not ad-hoc `Black.copy(.9)`.
- **Icons:** one visual weight (filled *or* outlined per context), 20dp, `onSurfaceVariant` unless active (`PinkPrimary`).
- **Badges/labels:** uppercase only for status badges, with letter-spacing.
- **Numbers:** `tnum` on scores, times, counts so they don't jitter while updating.
- **Private palettes die:** `HomeScreen.kt` defines its own `HomePalette` (indigo `AccentIndigo`, navy `StadiumNavy`, own surfaces, radial glow "like the CSS background"). It must be rewired to `MaterialTheme.colorScheme` + tokens and deleted — otherwise Home will not receive the new theme at all.
**Tier 2 — small composition tweaks (each needs the owner's tick; agents implement only ticked items):**
 
| ✔ | ID | Change | Why |
|---|---|---|---|
| ☑ | T2-1 | **Flat song rows:** remove the per-row card (background + border + rounded clip) on `TrendingSongRow`, history rows, search results, playlist rows. Rows sit on the background; no pressed highlight or ripple (feedback = `pressable` scale only); 8dp vertical gap | The single biggest "boxy / AI" tell; Spotify rows are flat |
| ☑ | T2-2 | **Bare trailing menu icon:** remove the filled circle behind row three-dot buttons (11 places); keep a 48dp hit area, 20dp icon, `onSurfaceVariant`. The dark translucent circle stays **only** when the button sits on top of artwork | Less noise per row |
| ☑ | T2-3 | **Text below artwork in hero tiles** (Recently Played, Featured, Best Of, Jam) instead of overlaid on a black gradient; artwork square 128dp; 3-dots stays overlaid on the art | Artwork is no longer covered; consistent tile anatomy |
| ☑ | T2-4 | **One container level in Match Details:** `SectionCard` (1dp border + 16dp card) holds a filled `CardHeaderBg` header band and divider lines. Replace with a plain section title above one borderless container; separate rows with spacing, not dividers | Removes layered boxes |
| — | T2-5 | *Removed after verification:* Search category tiles are already 72dp tall, 12dp radius, left-aligned; their colours come from `primaryContainer/secondaryContainer/tertiaryContainer` at 35% alpha, so they follow the new theme automatically (no code change; see `tertiaryContainer` in §4.2) | — |
| ☑ | T2-6 | **Home Jam tile uses the same tile anatomy** as other hero tiles (128dp square, label below). **Its colour stays blue** (owner decision D12), via the `JamGradient` token | Consistency of shape, deliberate colour contrast |

**Owner approved T2-1, T2-2, T2-3, T2-4 and T2-6 (T2-5 removed).** If a Tier-2 item were not ticked, the agent applies only the Tier-1 portion for those elements (e.g. smaller sizes/lighter weights inside the existing card) and notes it in the report.

### 4.7 Motion & feedback (`ui/motion/`)

| Piece | Spec |
|---|---|
| `Modifier.pressable(onClick, onLongClick, pressedScale)` | Replaces plain `pressScale`. Collects `PressInteraction` from a `MutableInteractionSource` shared with `combinedClickable(indication = null)`. **Press:** animate scale to `pressedScale` (rows 0.98, tiles/cards 0.97, chips/buttons 0.96) with a stiff spring. **Release/Cancel:** wait until >=120ms since Press (so a quick tap is still visible), then `spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)` back to 1f (small overshoot = the 'pop'). Scale via `graphicsLayer`. **Must be the outermost modifier** (before clip/shadow/border/background) |
| `Modifier.animateItem()` on `LazyColumn/Row` items | placement spring `StiffnessMediumLow`. Requires stable `key` — add `key = { it.id }` **only where a real stable id exists** (27 of 35 `items(...)` lack keys) |
| Image fade-in | Coil `crossfade(true)` / `crossfade(200)` on `ImageRequest`s |
| Skeleton | `Modifier.shimmer()` (1200ms linear loop, `surfaceContainerHigh`→`Highest`); **static** when system animator scale is 0. Used only where a loading flag already exists in UI state; never add VM state |
| Haptics (`LocalHapticFeedback`) | Like/unlike toggle → `ToggleOn/ToggleOff`; add-to-playlist / add-to-queue / Jam-join success → `Confirm`; long-press menu open → `LongPress`. If a type is unavailable at compile time fall back to `LongPress`/`TextHandleMove`. **No haptics on plain navigation taps; none in frozen files** |
| Screen enter | `AnimatedVisibility`/`fadeIn(220ms)` for first content only (optional) |
| NavHost transitions | **Not in scope** (D8) |

**Touch-feedback rules (owner decision):**
- Tiles, cards and rows get **no ripple and no pressed highlight** (`indication = null`); the scale animation from `pressable` is the only feedback. Do **not** change the global indication (it would alter the frozen nav bar/buttons). Buttons and `IconButton`s keep their default ripple.
- A quick tap must visibly animate, not only a press-and-hold.
- Clip, shadow and border must scale together with the content: `pressable` first, then `clip`/`shadow`/`border`/`background`, then content.

**Recently Played reorder (target behaviour):** when song X in slot k is played, X moves to slot 0; only slots 0..k-1 shift one slot right; nothing else moves and the row does not scroll. Requirements: list built distinct by `songId` (most recent first, `take(5)`) in the composable; `key = { it.songId }`; `animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow))`; keep the row at the start when the first key changes (`LazyListState.requestScrollToItem(0)` / `scrollToItem(0)`) because a keyed `LazyRow` otherwise anchors the old first item and the whole row slides; raise the moving card with `zIndex` if it passes behind others.


---

## 5. Component conventions (UI-B output)

- **`SectionHeader`**: single shared composable in `ui/components/SectionHeader.kt`. Title = `titleLarge`, optional subtitle = `bodySmall`/`onSurfaceVariant`, action = `labelMedium` + `PinkPrimary` (`PinkDeep` on light). Signature: `SectionHeader(title, subtitle = null, actionText = null, onActionClick = null)`. Replace the 3 private copies; adapt call sites by renaming parameters only (Library currently uses `onAction`).
- **Chips** (Live/Upcoming/Recent, genre chips, tab pills): unselected = `surfaceContainerHighest` + `onSurfaceVariant`; selected = `PinkPrimary` + white (on the two sports screens: `SportsAccent` + `onSportsAccent`). No per-chip rainbow colours.
- **Badges** (TEST/ODI/T20, HOST, NEW): `surfaceContainerHighest` background + `labelSmall` in `PinkGlow` (dark) / `PinkDeep` (light). `Live` badge keeps `SemanticLive`.
- **Tabs** (Match details: Info/Live/Scorecard/Squads): selected = `SportsAccent`.
- **Category tiles** (Search "Browse Categories"): already 72dp / 12dp radius with colours from theme container roles at 35% alpha — **no structural change**; they become pink-family once §4.2 defines all three containers. Only the label weight (`SemiBold` → per §4.5 `titleSmall` Medium/SemiBold as specified) and `shapes.medium` token swap apply.
- **Home "Jam Session" tile**: same anatomy as the other hero tiles (T2-6); **keeps its blue gradient** via `JamGradient` (D12). It is the one intentional non-pink block on Home.
- **Cards**: `surfaceContainerLow`/`High`, `shapes.large`, 1dp `outlineVariant` border only when on `background` and otherwise flat. No default Material elevation shadows.
- **Reference components** (`AddToPlaylistSheet`, three-dot menu): replace literals (`18131B`, `1E1724`, `FF2A5F`, `SynqPink`) with tokens (`surfaceContainerHigh`, `PinkPrimary`); appearance must stay visually equivalent.

---

## 6. Phases

Each phase ends with: gates (§8) → report (§10) → **STOP for owner approval.**

### UI-0 — Baseline & audit *(no code changes except tag + doc)*
1. `git tag pre-ui-refinement`.
2. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest` → record result (must pass before starting).
3. Create `docs/ui-audit.md` containing: counts (§8 metrics) per file; every use of indigo/blue/emerald/slate literals; every literal pink; every `items(` without key; the three `SectionHeader`s; every `fontSize` literal grouped by proposed role; Compose BOM/material3 API availability check (`animateItem`, `surfaceContainer*`, `HapticFeedbackType.Confirm/ToggleOn`).
4. Produce a **per-element proposal table** for every screen: `element | file:line | current (size, weight, colour, radius) | target from §4.6 | tier (1 or 2) | reason`. Elements that are already right are listed as "keep". Any deviation from §4.6 targets beyond ±2dp/1sp needs a reason here.
5. List every private palette / local colour object (known: `HomePalette` in `HomeScreen.kt`; private colour vals in `MatchDetailsScreen.kt`; grep for others) and how it maps to theme tokens.
6. Include the Tier-2 checklist (§4.6b) for the owner to tick.
7. STOP. Owner approves the audit table and ticks Tier-2 items.

### UI-A — Foundations *(theme files only; no screen files)*
Touch only: `ui/theme/*`, new `ui/motion/*`, `ui/GlassSurface.kt` (accent value lines), `ui/SynqBrand.kt` (pink values → aliases, delete unused).
- Implement §4.1–4.5 and 4.7 helpers (helpers exist but are unused yet).
- Keep §4.2 compatibility aliases so nothing else breaks.
- **Expected visible result:** tinted dark/light backgrounds; theme-driven accents turn pink; indigo gone wherever it flowed from the theme; global weight/size change wherever `MaterialTheme.typography` is used (~53 sites). Nothing else.
- Frozen files byte-identical.

### UI-B — Shared components
`SectionHeader` merge, `SongItem.kt` (highest-leverage row: title `titleMedium`, subtitle `bodySmall`, artwork 48, trailing icon per §4.6 (+T2-1/T2-2 if ticked), `shapes.small` artwork), `AddToPlaylistSheet.kt` (token swap only), chip/badge/tile helpers **only if the same pattern appears ≥3 times** (otherwise skip), `AppHeader.kt` (screen title role, padding per §4.6, logo size unchanged).

### UI-C — Screen passes *(one screen = one commit; order below)*
1. `HomeScreen`
2. `MusicScreen`, `PopularArtistsScreen`
3. `SearchScreen`
4. `LibraryScreen`, `AllPlaylistsScreen`
5. `ProfileScreen`
6. `SportsScreen`, `MatchDetailsScreen`
7. `PlaylistScreen`, `AlbumScreen`, `ArtistScreen`, `SongDetailScreen`, `ListeningHistoryScreen`
8. `AuthScreen`
9. `FullPlayerSheet` (tokens + weights only; ambient background and lyrics untouched)
10. `JamScreen`, `JamIntroDialog` **last** (largest file, 31× `Bold`; already hot pink — mostly weights, shapes, token swap)

Per-screen procedure:
1. Replace literal `fontSize`/`fontWeight` with roles (§4.5).
2. Replace literal colours with tokens/`colorScheme` (§4.2). Classify each slate literal: dark surface → `background/surface*`; text → `onBackground/onSurface/onSurfaceVariant`; indigo/blue/green/purple decorative → pink tokens or tonal surfaces; semantic → semantic tokens.
3. `RoundedCornerShape(n.dp)` → `MaterialTheme.shapes.*` (§4.4).
4. Apply the §4.6 element targets and the Tier-1 polish list; apply only the Tier-2 items the owner ticked.
5. Apply §5 component conventions where this screen has them.
6. Build + guard script; commit the restyle.
7. **Motion commit for the same screen** (merged from UI-D): `pressable`, haptics, `key` + `animateItem()` (not on drag-reorder lists: Full Player queue, Jam queue), shimmer only where a loading flag already exists, Coil `crossfade`. Do not add `pressable` to rows that already use `SongItem`. Nothing in frozen files.
8. One report for the screen covering both commits; owner tests once.

Screen-specific notes (from the app recording):
- **Home:** first rewire `HomePalette` to the theme and delete it (`AccentIndigo`, `StadiumNavy`, radial glow); greeting → `headlineMedium`; "Live Action" match cards, "Recently Played" and "Jam" tiles → §4.6 targets (match card ~264×148 / radius 16 / 18sp Bold score instead of 20sp ExtraBold; tiles 128dp); `StatusBadge` 10sp ExtraBold → 11sp SemiBold; Jam tile → same anatomy, keeps blue via `JamGradient` (D12); `TrendingSongRow` is a private composable (54dp art, 16/14sp text, bordered card, filled 36dp circle behind the three-dots) → §4.6 row targets, border removed (T1), flat row + bare icon if T2-1/T2-2 are ticked.
- **Music:** genre chips (Pop/Hip-Hop/Bollywood/Punjabi) → uniform chip style; "Featured Releases" NEW badge → `SemanticLive`-free, use `PinkPrimary` badge; Popular Artists circles → 80dp; Top Playlists rows → flat if T2-1 ticked, otherwise `surfaceContainerLow` with no border.
- **Search:** keep provider icons; Browse Categories → tonal tiles; history rows `bodyMedium`, clear icons 16.
- **Sports / MatchDetails:** first replace MatchDetails' private colour vals with tokens; filter chips and tabs → `SportsAccent` selected (not pink; text `onSportsAccent`); TEST/ODI/T20 badge → §5 with `SportsAccent` text; leading score → `SportsAccent`; score numerals `Bold + tnum`; status lines keep `SemanticWarning`; squad rows `titleMedium` + `bodySmall`.
- **Playlist/Album/Artist:** play FAB keeps `PinkPrimary`; page title `headlineSmall`; "Sort" chip → chip style; cover art unchanged.
- **Jam:** keep `PinkPrimary`/`PinkGradient`; delete private `Accent` and gradient vals; reduce weights; sheet/dialog titles `headlineSmall`; avatars use `PinkGradientHero`.

### UI-D — Motion & feedback *(merged into UI-C; this phase only covers leftovers)*
Motion is now done per screen inside UI-C (step 7). UI-D covers only what remains: shared components not yet animated, screens that were skipped, and any item the owner lists. Rules in §4.7 apply unchanged.

### UI-E — Copy & small personality *(strings limited to this list)*
- Home header subtitle "Welcome to Synq • Music & Sports" → a contextual line built **only from state already available in `HomeScreen`** (e.g. "2 matches live", or "Continue: <last played title>"); fallback = current static string. No new data fetching.
- Empty states (no queue, no history, no playlists, no Jam tracks): one friendly line + pink icon, using existing empty-state composables only.
- No other string changes.

### UI-F — Final audit
Run all metrics (§8), write before/after table, remove compatibility aliases that now have zero references, update `ROADMAP.md` (`Phase 9.6 → DONE`), summarise leftover "Needs decision" items.

---

## 7. Guard script (create as `scripts/ui_guard.sh`, run after every phase)

```bash
#!/usr/bin/env bash
# usage: scripts/ui_guard.sh [base-ref]   (default: pre-ui-refinement)
set -u
BASE=${1:-pre-ui-refinement}
SRC=android/app/src/main/java/com/synq

echo "== 1. Frozen files (must print nothing) =="
git diff --name-only "$BASE" -- android | grep -E "navigation/AppBottomNavBar.kt|features/music/components/MiniPlayer.kt" \
  && echo "FAIL: frozen file modified"

echo "== 2. GlassSurface.kt diff (only accent value lines allowed) =="
git diff -U0 "$BASE" -- $SRC/ui/GlassSurface.kt | grep -E '^[+-][^+-]'

echo "== 3. Logic-touching lines in diff (review each hit; expected: only appended modifiers) =="
git diff -U0 "$BASE" -- $SRC/features $SRC/ui $SRC/navigation \
  | grep -E '^[+-][^+-]' \
  | grep -E 'viewModel|ViewModel|navController|navigate\(|\.launch|LaunchedEffect|collectAs|[Rr]epository|\.emit|onValueChange|Screen\.'

echo "== 4. Forbidden files touched (must print nothing) =="
git diff --name-only "$BASE" | grep -E 'ViewModel|Repository|Dao|Dto|Entity|/di/|/data/|/domain/|Screen\.kt$|\.gradle|libs.versions' \
  | grep -v 'features/.*/.*Screen.kt'   # UI screens are allowed; Screen.kt (nav routes) is not

echo "== 5. Metrics =="
J=android/app/src/main/java
for w in Bold ExtraBold Black SemiBold Medium Normal; do
  printf "FontWeight.%-10s %s\n" $w "$(grep -rho "FontWeight\.$w" $J | wc -l)"; done
echo "fontSize literals outside Type.kt: $(grep -rE 'fontSize *= *[0-9.]+\.sp' $J | grep -v theme/Type.kt | wc -l)"
echo "Color(0x…) literals outside ui/theme:  $(grep -rhoE 'Color\(0x[0-9A-Fa-f]{8}\)' $J | wc -l) (total; theme files included)"
echo "RoundedCornerShape(n.dp) literals:    $(grep -rhoE 'RoundedCornerShape\([0-9]+\.dp\)' $J | wc -l)"
echo "indigo/blue hex anywhere:             $(grep -rEi '6366F1|4F46E5|818CF8|0F172A|1E293B|334155' $J | wc -l)"
echo "items() with key / total:             $(grep -rE '\bitems\(.*key *=' $J | wc -l) / $(grep -rE '\bitems\(' $J | wc -l)"
echo ".border(1.dp) count:                 $(grep -rE '\.border\(1\.dp' $J | wc -l)"
echo "haptic call sites:                    $(grep -rE 'performHapticFeedback' $J | wc -l)"
```

---

## 8. Gates & acceptance targets

**Every phase (must pass before reporting):**
1. `./gradlew :app:assembleDebug` succeeds with no new warnings from changed files.
2. `./gradlew :app:testDebugUnitTest` passes (existing Android unit tests untouched).
3. Guard script §7 checks 1, 2 (accent lines only), and 4 print nothing; check 3 hits are explained in the report.
4. Both dark and light theme verified on every screen touched (owner will review screenshots; agent lists screens affected).

**Final targets (UI-F):**

| Metric | Baseline | Target |
|---|---|---|
| `FontWeight.ExtraBold` / `Black` | 16 / 1 | **0** (frozen files excepted) |
| `FontWeight.Bold` | 150 | ≤ 45 |
| `FontWeight.SemiBold` | 95 | ≤ 45 |
| `fontSize` literals outside `Type.kt` | ~340 | ≤ 25 (frozen files + documented exceptions) |
| Distinct pink hexes in code | 6 | **4**, all in `Color.kt` |
| indigo / slate hex (§7 grep) | many | **0** | (the blue `JamGradient` on the Home Jam tile is a named exception)
| `RoundedCornerShape(n.dp)` literals | ~190 | ≤ 25 (frozen, hairlines, logo) |
| Private `SectionHeader` copies | 3 | **1** shared |
| Screen-private palettes (`HomePalette`, MatchDetails colour vals) | 2+ | **0** |
| `.border(1.dp …)` on rows/tiles | 33 | ≤ 10 (inputs, dialogs, selected states) |
| Filled circle behind row three-dots (if T2-2 ticked) | 11 | 0 on rows (kept only over artwork) |
| Haptic call sites | 0 | ≥ 6 |
| `items(...)` that animate and have a stable id | 8 keyed | all of those that now use `animateItem()` have keys |
| Frozen files diff | – | **empty** |

---

## 9. Defaults the owner may override (flagged assumptions)

| # | Decision | Default |
|---|---|---|
| D1 | Wordmark "q" and other coded uses of old logo pink `#FF2D55` | become `PinkPrimary` `#FF2D7A` (logo **drawable** unchanged) |
| D2 | `glassSurface` fill (`#1A1A22` neutral) used by frozen nav/mini-player | **left unchanged** to honour the freeze; review against tinted background in UI-A — if it looks off, owner decides whether to retint it |
| D3 | Lyrics text sizes | unchanged |
| D4 | Heading font | Inter only (no second family) |
| D5 | Light theme | fully included with tinted light tokens |
| D6 | Provider icons (JioSaavn/YouTube) | keep their brand colours |
| D7 | Greeting with username ("Good afternoon, yash09") | **not** added |
| D8 | NavHost screen transitions | out of scope (touches navigation) |
| D9 | Jam screens | token swap + weights + shapes only; no structural change |
| D11 | Sports accent colour | **Owner will decide by on-device testing.** Implement `SportsAccent`/`onSportsAccent` as the *only* place the colour is defined (`Color.kt`), initial value lime `#C6F135` / light `#4D7C0F`, with amber `#FFB020` / `#B45309` left as a commented alternative. Nothing in sports code may hardcode the colour, so changing it is a one-line edit. Avoid indigo, blue, teal |
| D12 | Home Jam tile colour | **Stays blue** (`JamGradient`, `#2563EB`→`#06B6D4`) so Home is not all pink. Only this tile; the Jam screens themselves stay pink |
| D10 | Selected nav icon + mini-player progress bar turn pink | **intended** side effect of the `GlassAccent` remap |

---

## 10. Report format (end of every phase)

```md
### Phase <id> report
**Changed files:** (list)            **New files:** (list)
**What changed (visually):** (bullets, per screen)
**Gates:** build ✅/❌ · unit tests ✅/❌ · guard checks 1/2/4 ✅/❌
**Guard check 3 hits explained:** (each hit → why it is styling-only)
**Metrics delta:** (before → after for §8 rows touched)
**Needs decision:** (anything ambiguous; do not implement until answered)
**Skipped on purpose:** (e.g. skeletons with no loading flag)
**Next phase:** <id> — awaiting approval. Nothing from it has been started.
```