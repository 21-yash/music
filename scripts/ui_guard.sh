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

