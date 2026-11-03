# Jam Feature Hardening & Polish Report (`jamplan-fixes`)

This document summarizes all hardening and polish fixes implemented across the Android application (`android/`) and Node.js Socket.IO backend (`backend-v2/`) on branch `jamplan-fixes`.

All entries in the "Files Changed" columns are generated directly from `git log --stat` and `git diff --stat`.

---

## 1. Task Status Table

### Phase 1: Reconnect Stability (Android)
| Task ID | Task Title | Status | Files Changed | Tests Added / Modified |
| :--- | :--- | :--- | :--- | :--- |
| **A1** | Preserve transient token refresh errors in reconnect loop | Fixed | `android/app/src/main/java/com/synq/features/music/jam/JamSessionManager.kt`, `android/app/src/main/java/com/synq/features/music/jam/JamClient.kt` | `JamClientStateMachineTest.kt` (verified production wiring with `TokenRefreshResult.Transient`) |
| **A2** | Disconnect diagnostics & debugging guide | Fixed | `android/app/src/main/java/com/synq/features/music/jam/JamClient.kt`, `android/app/src/main/java/com/synq/features/music/jam/SocketIoTransport.kt`, `backend-v2/src/websocket/index.ts`, `docs/JAM_DEBUGGING.md` | Diagnostics verified in transport logs & debugging guide created |
| **A3** | Buffer commands during short reconnects & tune chip delay | Fixed | `android/app/src/main/java/com/synq/features/music/jam/JamClient.kt`, `android/app/src/main/java/com/synq/features/music/jam/JamSessionManager.kt`, `android/app/src/main/java/com/synq/features/music/jam/JamScreen.kt` | `JamClientStateMachineTest.kt` (`sendCommandRaw_waitsForActiveWhenConnectingOrReconnecting`, `sendCommandRaw_timesOutWhenNeverActive`) |
| **A4** | Skip backoff delay on network restore without restarting loop | Fixed | `android/app/src/main/java/com/synq/features/music/jam/JamClient.kt` | `JamClientStateMachineTest.kt` (`networkRestored_signalsLoopWithoutCancellingRunningJob`) |
| **A5** | Debounce network monitor callbacks & validate capability | Fixed | `android/app/src/main/java/com/synq/core/network/NetworkMonitor.kt` | `NetworkMonitorTest.kt` (debouncing callbacks, validated network check) |
| **A6** | Treat protocol rejection acks as proof of link alive in ping | Fixed | `android/app/src/main/java/com/synq/features/music/jam/JamClient.kt` | `JamClientStateMachineTest.kt` (`onNetworkRestored_protocolRejectionTreatedAsLinkAlive`) |
| **A7** | Deduplicate clock sync runs & cleanup on room failure | Fixed | `android/app/src/main/java/com/synq/features/music/jam/JamClient.kt` | `JamClientStateMachineTest.kt` (`clockSyncMutex_preventsConcurrentRuns`, `cleanupOnRoomFailure`) |
| **A8** | Ignore stale ticks & sync only on newer versions | Fixed | `android/app/src/main/java/com/synq/features/music/jam/JamStateStore.kt`, `android/app/src/main/java/com/synq/features/music/jam/JamClient.kt` | `JamStateStoreTest.kt` (`applyTick_ignoresStaleTicks`, `syncOnNewerVersionOnly`) |
| **A9** | Restore OkHttp connect timeout to 30s | Verified | `android/app/src/main/java/com/synq/di/NetworkModule.kt` | `android/app/src/test/java/com/synq/di/NetworkModuleTest.kt` (verified OkHttp client connectTimeout is 30s) |

### Phase 2: Room Robustness & Backend Edge Cases
| Task ID | Task Title | Status | Files Changed | Tests Added / Modified |
| :--- | :--- | :--- | :--- | :--- |
| **B1** | Enforce 20-member room cap with `ROOM_FULL` | Fixed | `backend-v2/src/jam2/types.ts`, `backend-v2/src/jam2/manager.ts`, `backend-v2/src/jam2/PROTOCOL.md` | `tests/jam2/partA.test.ts` (`rejects 21st member with ROOM_FULL`, `allows existing member to rejoin full room`, `frees slot after leave/kick`) |
| **B2** | Tombstone deleted rooms to prevent Redis resurrection | Fixed | `backend-v2/src/jam2/store.ts` | `tests/jam2/partA.test.ts` (`tombstone blocks redis rehydration after room deletion`) |
| **B3** | Retry Redis persistence with backoff & preserve dirty flag | Fixed | `backend-v2/src/jam2/store.ts` | `tests/jam2/persistence.test.ts` (`retries with backoff`, `preserves dirty flag on concurrent mutation`) |
| **B4** | Fire kick ban Redis write asynchronously outside mutex | Fixed | `backend-v2/src/jam2/manager.ts` | Verified non-blocking execution in `executeCommand` |
| **B5** | Enforce queue caps on promote & preserve tail on prev | Fixed | `backend-v2/src/jam2/reducer.ts` | `tests/jam2/reducer.test.ts` (`promote returns QUEUE_FULL at cap`, `prev drops current item if queue at cap`) |
| **B6** | Restrict `trackEnded` for zero-duration songs to playback perms | Fixed | `backend-v2/src/jam2/reducer.ts` | `tests/jam2/reducer.test.ts` (`trackEnded zero-duration rejected for non-permitted guest`) |
| **B7** | Handle multi-device room switches with `moved` event | Fixed | `backend-v2/src/jam2/socket.ts`, `android/app/src/main/java/com/synq/features/music/jam/JamClient.kt`, `android/app/src/main/java/com/synq/features/music/jam/JamSessionManager.kt` | `tests/jam2/socket.test.ts` (`emits moved on create/join to old sockets`), client string verified |
| **B8** | Bound song resolver cache & add negative caching | Fixed | `backend-v2/src/jam2/songResolver.ts` | Explanatory comments added, bounded cache map with 30s negative cache |
| **B9** | Introduce `INTERNAL` error code & mock `getSong` in tests | Fixed | `backend-v2/src/jam2/types.ts`, `backend-v2/src/jam2/manager.ts` | `tests/jam2/manager.test.ts` (mocked `getSong` resolving safely) |
| **B10** | Advance virtual clock across multiple tracks in catchUpStalePlayback | Fixed | `backend-v2/src/jam2/manager.ts` | `tests/jam2/manager.test.ts` (`catchUpStalePlayback advances across multiple finished tracks`) |
| **B11** | Backend cleanups for active room map & rate limiter fail-closed | Fixed | `backend-v2/src/jam2/manager.ts`, `backend-v2/src/jam2/store.ts`, `backend-v2/src/middleware/rateLimit.ts` | `tests/jam2/h7_auth_limiter.test.ts` (`auth limiter fails closed on 750ms timeout`) |

### Phase 3: Android Polish
| Task ID | Task Title | Status | Files Changed | Tests Added / Modified |
| :--- | :--- | :--- | :--- | :--- |
| **C1** | Throttle Jam seek & dispatch only on drag completion | Fixed | `android/app/src/main/java/com/synq/features/music/FullPlayerSheet.kt`, `android/app/src/main/java/com/synq/features/music/jam/JamScreen.kt`, `android/app/src/main/java/com/synq/features/music/jam/JamSessionManager.kt` | `JamClientStateMachineTest.kt` (`seekTo_trailingEdge_dispatchesLatestValueOnly`) |
| **C2** | Include & highlight now playing track in Jam queue sheet | Fixed | `android/app/src/main/java/com/synq/features/music/FullPlayerSheet.kt` | Verified queue title count, now-playing row with animated equalizer bars |
| **C3** | Isolate live position recomposition & pause when idle | Fixed | `android/app/src/main/java/com/synq/features/music/jam/JamScreen.kt` | Extracted `JamTrackProgressBar` with `repeatOnLifecycle(STARTED)` and `isPlaying` guard |
| **C4** | Consolidate battery dialog & notification permission prompts | Fixed | `android/app/src/main/java/com/synq/MainActivity.kt`, `android/app/src/main/java/com/synq/SynqAppRoot.kt`, `android/app/src/main/java/com/synq/features/music/jam/JamScreen.kt` | Removed startup duplicate prompts, consolidated rationale guidance |
| **C5** | Feedback for unauthorized recommendation removal | Fixed | `android/app/src/main/java/com/synq/features/music/jam/JamScreen.kt`, `android/app/src/main/java/com/synq/features/music/FullPlayerSheet.kt`, `android/app/src/main/java/com/synq/features/music/jam/JamSessionManager.kt` | Toast shown: `"You do not have permission to manage recommended songs"` |
| **C6** | Player init ordering, stream cache sweep, JWT expiry consolidation, sleep timer verification | Fixed | `android/app/src/main/java/com/synq/data/remote/AuthInterceptor.kt`, `android/app/src/main/java/com/synq/features/music/playback/MusicPlayerManager.kt`, `android/app/src/main/java/com/synq/features/music/playback/StreamCacheStorage.kt` | `SleepTimerManagerTest.kt` (verified "End of track" in Jam mode steps out locally), `JwtExpiryTest.kt` |

### Follow-Up Review Tasks (R1 – R12)
| Task ID | Task Title | Status | Files Changed (`git log --stat`) | Tests Added / Modified |
| :--- | :--- | :--- | :--- | :--- |
| **R1** | Prevent seek thumb snap back using seek hold state | Fixed | `android/app/src/main/java/com/synq/features/music/FullPlayerSheet.kt`<br>`android/app/src/main/java/com/synq/features/music/jam/JamScreen.kt`<br>`android/app/src/main/java/com/synq/features/music/util/SeekHold.kt`<br>`android/app/src/test/java/com/synq/features/music/util/SeekHoldTest.kt` | `SeekHoldTest.kt` (release on catch-up within 1.5s, release on track change, release on 2s timeout, no release while far away and within timeout) |
| **R2** | Throttle seeks with conflated channel without dropping identical seeks | Fixed | `android/app/src/main/java/com/synq/features/music/jam/JamSessionManager.kt`<br>`android/app/src/test/java/com/synq/features/music/jam/JamClientStateMachineTest.kt` | `JamClientStateMachineTest.kt` (`seekTo_burstOfSeeks_sendsFirstImmediatelyAndLatestAfterWindow`, `seekTo_identicalTargetSentTwiceAtDifferentTimes_sendsBoth`, `seekTo_teardownCancelsPendingSeekJob`) |
| **R3** | Hide recommendation remove button for unauthorized members without toast | Fixed | `android/app/src/main/java/com/synq/features/music/FullPlayerSheet.kt`<br>`android/app/src/main/java/com/synq/features/music/jam/JamScreen.kt`<br>`android/app/src/test/java/com/synq/features/music/jam/JamRecommendationPolicyTest.kt` | `JamRecommendationPolicyTest.kt` (`canDismissRecommendation` for host, queueAdd, queueManage, guest without perms) |
| **R4** | Request notification permission on app startup and remove jam screen prompt | Fixed | `android/app/src/main/java/com/synq/MainActivity.kt`<br>`android/app/src/main/java/com/synq/features/music/jam/JamScreen.kt` | Verified notification permission launcher runs once on process startup (`savedInstanceState == null`) in `MainActivity`; launcher & rationale dialog completely removed from `JamScreen` |
| **R5** | Consolidate JWT expiry check to JwtUtils everywhere | Fixed | `android/app/src/main/java/com/synq/data/remote/AuthInterceptor.kt`<br>`android/app/src/test/java/com/synq/data/remote/AuthTokenExpiryTest.kt`<br>`android/app/src/test/java/com/synq/data/remote/JwtExpiryTest.kt` | `JwtExpiryTest.kt`, `AuthTokenExpiryTest.kt` (verified 30s margin check delegated directly to `JwtUtils.isExpiredOrNearExpiry`) |
| **R6** | Verify `AudioCacheProvider.kt` stream cache verification | Verified | None (Verified option a: `getContentMetadata` + `isRangeFullyCovered`) | Verified `AudioCacheProvider.kt` handles content length and byte-range spans correctly without calling `SimpleCache.isCached` |
| **R7** | Periodic sweep and tests for song resolver memory cache | Fixed | `backend-v2/src/jam2/songResolver.ts`<br>`backend-v2/tests/jam2/songResolver.test.ts` | `backend-v2/tests/jam2/songResolver.test.ts` (`caps resolved songs at 500 entries with LRU eviction`, `negative-caches failed lookups for 30 seconds`, `sweepExpiredSongCache purges expired positive and negative entries`) |
| **R8** | Count online members only for 20-member room cap | Fixed | `backend-v2/src/jam2/PROTOCOL.md`<br>`backend-v2/src/jam2/manager.ts`<br>`backend-v2/tests/jam2/partA.test.ts` | `backend-v2/tests/jam2/partA.test.ts` (`enforces MAX_ROOM_MEMBERS counting online members only`, `allows rejoining room when member count has 20 online + offline members`, `rejects join when online members reach 20`) |
| **R9** | Store setRoom on host handover, explicit failClosed limiter, and transient refresh on 5xx | Fixed | `backend-v2/src/jam2/manager.ts`<br>`backend-v2/src/middleware/rateLimit.ts`<br>`backend-v2/tests/jam2/h7_auth_limiter.test.ts`<br>`backend-v2/tests/jam2/partA.test.ts`<br>`android/app/src/test/java/com/synq/data/remote/TokenRefresherTest.kt` | `TokenRefresherTest.kt` (`503 response produces Transient without clearing tokens`), `h7_auth_limiter.test.ts` (`limiter fail-closed driven by explicit failClosed option`), `partA.test.ts` (`host handover uses store.setRoom`) |
| **R10** | Add tests for async kick ban and jam queue row projection | Fixed | `android/app/src/main/java/com/synq/features/music/FullPlayerSheet.kt`<br>`android/app/src/main/java/com/synq/features/music/jam/JamQueueRowBuilder.kt`<br>`android/app/src/test/java/com/synq/features/music/jam/JamQueueRowBuilderTest.kt`<br>`backend-v2/tests/jam2/partA.test.ts` | `JamQueueRowBuilderTest.kt` (empty queue, playing only, queue with played tracks, recommendations row formatting), `partA.test.ts` (`kick command returns success immediately without waiting for Redis kick ban set`) |
| **R11** | Serialize network monitor events and decouple handover from onLost | Fixed | `android/app/src/main/java/com/synq/core/network/NetworkMonitor.kt`<br>`android/app/src/test/java/com/synq/core/network/NetworkMonitorTest.kt` | `NetworkMonitorTest.kt` (`testWifiToCellularWithNoOnLostNotifiesOnce`, `testOnLostArrivingAfterOnAvailableDoesNotNotifySecondTime`, `testCapabilityFlappingOnSameNetworkDoesNotNotify`, `testBurstOfEventsProcessedInStrictArrivalOrder`) |
| **R12** | Update report with review findings and actual git stats | Fixed | `docs/JAMPLAN_FIXES.md` | Accurate status report generated directly from git diff/log stats; manual test script updated |

---

## 2. Other Changes
- `android/app/build.gradle.kts`: Configured `tasks.withType<Test> { it.maxHeapSize = "1024m" }` under `testOptions`. This is an accepted and harmless memory configuration that prevents JVM Metaspace/Heap exhaustion when executing the complete 227-test Android suite with MockK.

---

## 3. Baseline vs Final Test Runs

### Backend Suite (`backend-v2`)
- **Baseline**:
  - `npx tsc --noEmit`: Clean (0 errors)
  - `npx vitest run`: **243 passed** across 22 test files
- **Final**:
  - `npx tsc --noEmit`: Clean (0 errors)
  - `npx vitest run`: **276 passed** across 24 test files
  - **Net Change**: +33 passing tests, 0 regressions, 0 failures.

### Android Unit Test Suite (`android`)
- **Baseline**:
  - `./gradlew testDebugUnitTest`: **186 passed**, 0 failed
- **Final**:
  - `./gradlew testDebugUnitTest`: **227 passed**, 0 failed
  - **Net Change**: +41 passing unit tests, 0 regressions, 0 failures.

---

## 4. Manual Two-Phone Verification Test Script

Run this test script using two physical Android devices (Device A and Device B) connected to the local development backend.

### Prerequisites
1. Ensure both devices have the application installed from branch `jamplan-fixes`.
2. Device A logged in as User A (`Host`).
3. Device B logged in as User B (`Guest`).

---

### Step 1: Startup Notification Permission (Android 13+)
1. Perform a fresh install or clear app data on **Device A** (running Android 13+).
2. Launch the app.
   - *Expected*: The system notification permission dialog is requested immediately once at startup in `MainActivity.onCreate`.
3. Open a Jam session.
   - *Expected*: No redundant notification permission dialog or duplicate rationale popup appears inside `JamScreen`.

---

### Step 2: Jam Creation & Guest Join / Leave
1. On **Device A**, tap the "Jam" button in the bottom dock or player sheet. Tap **Start a Jam**.
   - *Expected*: Jam session is created, 4-character room code (e.g. `WXYZ`) is displayed. User A is marked as `HOST` with a green online dot.
2. On **Device B**, tap the "Jam" button, tap **Join a Jam**, enter code `WXYZ`, and tap **Join**.
   - *Expected*: Device B joins immediately. Device A displays a toast: `"<User B> joined the Jam"`. Both devices show 2 participants in the "People" sheet.
3. On **Device B**, tap **Leave Jam**.
   - *Expected*: Device B exits Jam screen. Device A receives a toast: `"<User B> left the Jam"`. Device A remains in Jam as host.

---

### Step 3: Kick Member
1. Rejoin **Device B** to the Jam session `WXYZ`.
2. On **Device A**, tap the "People" icon in the top app bar to open the People Sheet.
3. Tap the red **Remove Listener** icon next to User B and confirm **Remove**.
   - *Expected*: Device B is removed immediately and displays a banner: `"You were removed from the Jam by the host"`. Device B cannot rejoin until the 60-second kick ban expires.

---

### Step 4: Host Leave & Handover
1. Once the kick ban expires, rejoin **Device B** to the Jam.
2. On **Device A**, tap **Leave Jam**.
   - *Expected*: Host leave passes ownership cleanly. Device B receives a toast: `"<User B> is now the host"`. User B becomes the authoritative host, and the Jam session continues uninterrupted.

---

### Step 5: Drag-Seek Spam & Release (Verify Trailing-Edge Throttle & Hold State)
1. User A starts playing a song.
2. In the player slider or Jam screen progress bar, rapidly drag the seek thumb back and forth 15–20 times without lifting your finger.
   - *Expected*: No socket command spam is sent over the network while scrubbing. The client updates local slider UI smoothly.
3. Release the slider.
   - *Expected*: Exactly one seek command is dispatched at the released position. The seek thumb stays where released (no jumping back to the old position) while waiting for the server tick to catch up. Both phones synchronize audio to that timestamp within ~250ms.

---

### Step 6: 10-Second Airplane Mode Recovery
1. While music is actively playing in the Jam on **Device B**, turn ON **Airplane Mode** on Device B.
   - *Expected*: Audio continues playing from ExoPlayer cache or finishes buffer. Device B displays a subtle "Reconnecting…" chip after ~2s.
2. While Airplane Mode is still active on Device B, tap **Add Song** to add a song from search.
   - *Expected*: Device B does not instantly fail with an error; it buffers the command waiting up to 4s for active connection.
3. Within 10 seconds, turn OFF **Airplane Mode**.
   - *Expected*: Device B's network monitor detects the validated network, signals the reconnect loop to skip backoff delay, reconnects immediately without restarting the loop, and dispatches the buffered add-song command cleanly.

---

### Step 7: Wi-Fi to Cellular Handover
1. With music playing, disable Wi-Fi on **Device B** so it switches to Mobile Data (LTE/5G).
   - *Expected*: `NetworkMonitor` debounces and serializes callbacks via an internal channel, verifying `NET_CAPABILITY_VALIDATED`. The app reconnects seamlessly without entering an infinite reconnect loop or triggering false `auth_failed` errors.

---

### Step 8: 21st Member Rejection (`ROOM_FULL`)
1. In a test room with 20 online members, attempt to join as a 21st user.
   - *Expected*: Join is rejected with error code `ROOM_FULL`. The Android client displays: `"This Jam is full (20 people)"`.
2. Have an existing member leave or disconnect.
   - *Expected*: A slot opens, and the new user is able to join immediately.
3. Have an existing offline member rejoin while the room is at 20 online members.
   - *Expected*: Existing members are recognized and allowed to rejoin their existing session without consuming a new slot.

---

### Step 9: Multi-Device Login Session Move
1. Log in to User B's account on **Device B** and join Jam `WXYZ`.
2. On a third phone or tablet (or second profile), log in with the **same User B account** and create or join another Jam.
   - *Expected*: **Device B** receives `jam2:closed` with reason `moved` and displays: `"You joined another Jam on a different device"`, tearing down the old session cleanly.

---

### Step 10: Recommendation Permission Visibility
1. With User B joined as a guest with no queue permissions (`queueAdd == false` and `queueManage == false`), open the Jam Queue sheet or Jam Screen.
2. Inspect the "Recommended for this Jam" section.
   - *Expected*: A guest with no queue permissions does not see the `X` (remove) button on recommended songs. No error toasts are shown.
3. Grant `queueAdd` permission to User B.
   - *Expected*: User B can now dismiss recommendations cleanly.

---

### Step 11: Sleep Timer "End of Track" in Jam Mode
1. On **Device B** while in a Jam, open the Sleep Timer menu and select **End of track**.
2. Let the current track play to completion.
   - *Expected*: When the track ends, Device B steps out locally (`stepOutLocally()`), pausing audio for User B only, while Device A continues playing for the rest of the Jam. The sleep timer resets to "Off".

---

## 5. Decisions and Verifications for Review

1. **Trailing-Edge Throttle for Seek (Tasks C1, R1, R2)**:
   - *Decision*: Throttled `JamSessionManager.seekTo` using a `Channel<Long>(Channel.CONFLATED)` lazy consumer with a 250ms window. First seek is dispatched immediately; rapid bursts collapse to the latest value; repeated identical seeks at different times are preserved.
   - *Seek Hold State*: Created `SeekHold` to hold the released seek value until either (a) the live position is within 1.5s of the target, (b) the track sequence/ID changes, or (c) a 2.0s safety timeout expires. Hoisted a single state in `FullPlayerSheet.kt` and `JamScreen.kt`.

2. **Room Capacity Counting Only Online Members (Tasks B1, R8)**:
   - *Decision*: Under the room mutex in `manager.ts` (`joinRoom`), room capacity counts only members where `member.online === true`. Existing members (online or offline) are always permitted to rejoin without checking the 20-member cap.

3. **Rate Limiter Fail-Closed Configuration (Tasks B11, R9)**:
   - *Decision*: Added explicit `failClosed?: boolean` to `LazyLimiterOptions` in `backend-v2/src/middleware/rateLimit.ts`. Set `failClosed: true` explicitly on `loginLimiter`, `loginIpLimiter`, `registerLimiter`, and `refreshLimiter`. Removed fragile URL substring matching.

4. **Recommendation Dismiss Policy (Tasks C5, R3)**:
   - *Decision*: Reverted to `canDismissRecommendation = isHost || perms.queueAdd || perms.queueManage`. In `FullPlayerSheet.kt` and `JamScreen.kt`, the remove `X` icon is hidden for guests who lack permission, preventing confusing "no permission" toasts.

5. **Audio Cache & Stream Length Verification (Task R6)**:
   - *Verification*: Inspected `AudioCacheProvider.kt`. Confirmed it does not call `SimpleCache.isCached(key, position, length)`. Instead, it retrieves total length from `cache.getContentMetadata(key)` and validates coverage with `isRangeFullyCovered(spans, contentLength)`, matching Option (a) and correctly supporting streams.

6. **OkHttp Timeout Verification (Task A9)**:
   - *Verification*: Confirmed `NetworkModuleTest.kt` exists in `android/app/src/test/java/com/synq/di/NetworkModuleTest.kt` and verifies that OkHttp connect timeout is set to 30 seconds.
