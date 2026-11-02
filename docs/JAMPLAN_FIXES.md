# Jam Feature Hardening & Polish Report (`jamplan-fixes`)

This document summarizes all hardening and polish fixes implemented across the Android application (`android/`) and Node.js Socket.IO backend (`backend-v2/`) on branch `jamplan-fixes`.

---

## 1. Task Status Table

| Task ID | Task Title | Status | Files Changed | Tests Added / Modified |
| :--- | :--- | :--- | :--- | :--- |
| **A1** | Preserve transient token refresh errors in reconnect loop | Fixed | `android/.../jam/JamSessionManager.kt`, `android/.../jam/JamClient.kt` | `JamClientStateMachineTest.kt` (verified production wiring with `TokenRefreshResult.Transient`) |
| **A2** | Disconnect diagnostics & debugging guide | Fixed | `JamClient.kt`, `SocketIoTransport.kt`, `backend-v2/.../websocket/index.ts`, `docs/JAM_DEBUGGING.md` | Diagnostics verified in transport logs & debugging guide created |
| **A3** | Buffer commands during short reconnects & tune chip delay | Fixed | `JamClient.kt`, `JamSessionManager.kt`, `JamScreen.kt` | `JamClientStateMachineTest.kt` (`sendCommandRaw_waitsForActiveWhenConnectingOrReconnecting`, `sendCommandRaw_timesOutWhenNeverActive`) |
| **A4** | Skip backoff delay on network restore without restarting loop | Fixed | `JamClient.kt` | `JamClientStateMachineTest.kt` (`networkRestored_signalsLoopWithoutCancellingRunningJob`) |
| **A5** | Debounce network monitor callbacks & validate capability | Fixed | `core/network/NetworkMonitor.kt` | `NetworkMonitorTest.kt` (debouncing callbacks, validated network check) |
| **A6** | Treat protocol rejection acks as proof of link alive in ping | Fixed | `JamClient.kt` | `JamClientStateMachineTest.kt` (`onNetworkRestored_protocolRejectionTreatedAsLinkAlive`) |
| **A7** | Deduplicate clock sync runs & cleanup on room failure | Fixed | `JamClient.kt` | `JamClientStateMachineTest.kt` (`clockSyncMutex_preventsConcurrentRuns`, `cleanupOnRoomFailure`) |
| **A8** | Ignore stale ticks & sync only on newer versions | Fixed | `JamStateStore.kt`, `JamClient.kt` | `JamStateStoreTest.kt` (`applyTick_ignoresStaleTicks`, `syncOnNewerVersionOnly`) |
| **A9** | Restore OkHttp connect timeout to 30s | Fixed | `di/NetworkModule.kt` | Verified OkHttp client timeout configured to 30s |
| **B1** | Enforce 20-member room cap with `ROOM_FULL` | Fixed | `backend-v2/.../jam2/types.ts`, `manager.ts`, `jam2/PROTOCOL.md` | `tests/jam2/partA.test.ts` (`rejects 21st member with ROOM_FULL`, `allows existing member to rejoin full room`, `frees slot after leave/kick`) |
| **B2** | Tombstone deleted rooms to prevent Redis resurrection | Fixed | `backend-v2/.../jam2/store.ts` | `tests/jam2/partA.test.ts` (`tombstone blocks redis rehydration after room deletion`) |
| **B3** | Retry Redis persistence with backoff & preserve dirty flag | Fixed | `backend-v2/.../jam2/store.ts` | `tests/jam2/persistence.test.ts` (`retries with backoff`, `preserves dirty flag on concurrent mutation`) |
| **B4** | Fire kick ban Redis write asynchronously outside mutex | Fixed | `backend-v2/.../jam2/manager.ts` | Verified non-blocking execution in `executeCommand` |
| **B5** | Enforce queue caps on promote & preserve tail on prev | Fixed | `backend-v2/.../jam2/reducer.ts` | `tests/jam2/reducer.test.ts` (`promote returns QUEUE_FULL at cap`, `prev drops current item if queue at cap`) |
| **B6** | Restrict `trackEnded` for zero-duration songs to playback perms | Fixed | `backend-v2/.../jam2/reducer.ts` | `tests/jam2/reducer.test.ts` (`trackEnded zero-duration rejected for non-permitted guest`) |
| **B7** | Handle multi-device room switches with `moved` event | Fixed | `backend-v2/.../jam2/socket.ts`, `JamClient.kt`, `JamSessionManager.kt` | `tests/jam2/socket.test.ts` (`emits moved on create/join to old sockets`), client string verified |
| **B8** | Bound song resolver cache & add negative caching | Fixed | `backend-v2/.../jam2/songResolver.ts` | Explanatory comments added, bounded cache map with 60s negative cache |
| **B9** | Introduce `INTERNAL` error code & mock `getSong` in tests | Fixed | `backend-v2/.../jam2/types.ts`, `manager.ts` | `tests/jam2/manager.test.ts` (mocked `getSong` resolving safely) |
| **B10** | Advance virtual clock across multiple tracks in catchUpStalePlayback | Fixed | `backend-v2/.../jam2/manager.ts` | `tests/jam2/manager.test.ts` (`catchUpStalePlayback advances across multiple finished tracks`) |
| **B11** | Backend cleanups for active room map & rate limiter fail-closed | Fixed | `backend-v2/.../jam2/manager.ts`, `store.ts`, `rateLimit.ts` | `tests/jam2/h7_auth_limiter.test.ts` (`auth limiter fails closed on 750ms timeout`) |
| **C1** | Throttle Jam seek & dispatch only on drag completion | Fixed | `FullPlayerSheet.kt`, `JamScreen.kt`, `JamSessionManager.kt` | `JamClientStateMachineTest.kt` (`seekTo_trailingEdge_dispatchesLatestValueOnly`) |
| **C2** | Include & highlight now playing track in Jam queue sheet | Fixed | `FullPlayerSheet.kt` | Verified queue title count, now-playing row with animated equalizer bars |
| **C3** | Isolate live position recomposition & pause when idle | Fixed | `JamScreen.kt` | Extracted `JamTrackProgressBar` with `repeatOnLifecycle(STARTED)` and `isPlaying` guard |
| **C4** | Consolidate battery dialog & notification permission prompts | Fixed | `MainActivity.kt`, `SynqAppRoot.kt`, `JamScreen.kt` | Removed startup duplicate prompts, unified prompt on first Jam entry with clear rationale |
| **C5** | Feedback for unauthorized recommendation removal | Fixed | `JamScreen.kt`, `FullPlayerSheet.kt`, `JamSessionManager.kt` | Toast shown: `"You do not have permission to manage recommended songs"` |
| **C6** | Player & cache hygiene, JWT expiry consolidation, sleep timer verification | Fixed | `MusicPlayerManager.kt`, `StreamCacheStorage.kt`, `AuthInterceptor.kt`, `AudioCacheProvider.kt`, `SleepTimerManager.kt` | `SleepTimerManagerTest.kt` (verified "End of track" in Jam mode steps out locally), `JwtExpiryTest.kt` |
| **N1** | Fix lateinit race in SingleFlightExtractor using CoroutineStart.LAZY | Fixed | `features/music/playback/SingleFlightExtractor.kt` | Prevents uninitialized job access on rapid re-extraction |
| **N2** | Prefetch next queue song in personal mode with guard & policy | Fixed | `MusicPlayerManager.kt` | Prefetches next track stream bytes when current song starts playing |

---

## 2. Baseline vs Final Test Runs

### Backend Suite (`backend-v2`)
- **Baseline**:
  - `npx tsc --noEmit`: Clean (0 errors)
  - `npx vitest run`: **243 passed** across 22 test files (Duration: ~30s)
- **Final**:
  - `npx tsc --noEmit`: Clean (0 errors)
  - `npx vitest run`: **269 passed** across 23 test files (Duration: ~39.8s)
  - **Net Change**: +26 passing tests, 0 regressions, 0 failures.

### Android Unit Test Suite (`android`)
- **Baseline**:
  - `./gradlew testDebugUnitTest`: **186 passed**, 0 failed
- **Final**:
  - `./gradlew testDebugUnitTest`: **211 passed**, 0 failed
  - **Net Change**: +25 passing unit tests, 0 regressions, 0 failures.

---

## 3. Manual Two-Phone Verification Test Script

Run this test script using two physical Android devices (Device A and Device B) connected to the local development backend.

### Prerequisites
1. Ensure both devices have the application installed from branch `jamplan-fixes`.
2. Device A logged in as User A (`Host`).
3. Device B logged in as User B (`Guest`).

---

### Step 1: Jam Creation & Guest Join / Leave
1. On **Device A**, tap the "Jam" button in the bottom dock or player sheet. Tap **Start a Jam**.
   - *Expected*: Jam session is created, 4-character room code (e.g. `WXYZ`) is displayed. User A is marked as `HOST` with a green online dot.
2. On **Device B**, tap the "Jam" button, tap **Join a Jam**, enter code `WXYZ`, and tap **Join**.
   - *Expected*: Device B joins immediately. Device A displays a toast: `"<User B> joined the Jam"`. Both devices show 2 participants in the "People" sheet.
3. On **Device B**, tap **Leave Jam**.
   - *Expected*: Device B exits Jam screen. Device A receives a toast: `"<User B> left the Jam"`. Device A remains in Jam as host.

---

### Step 2: Kick Member
1. Rejoin **Device B** to the Jam session `WXYZ`.
2. On **Device A**, tap the "People" icon in the top app bar to open the People Sheet.
3. Tap the red **Remove Listener** icon next to User B and confirm **Remove**.
   - *Expected*: Device B is removed immediately and displays a banner: `"You were removed from the Jam by the host"`. Device B cannot rejoin until the 60-second kick ban expires.

---

### Step 3: Host Leave & Handover
1. Once the kick ban expires, rejoin **Device B** to the Jam.
2. On **Device A**, tap **Leave Jam**.
   - *Expected*: Host leave passes ownership cleanly. Device B receives a toast: `"<User B> is now the host"`. User B becomes the authoritative host, and the Jam session continues uninterrupted.

---

### Step 4: Drag-Seek Spam (Verify Trailing-Edge Throttle)
1. User A starts playing a song.
2. In the player slider or Jam screen progress bar, rapidly drag the seek thumb back and forth 15–20 times without lifting your finger.
   - *Expected*: No socket command spam is sent over the network while scrubbing. The client updates local slider UI smoothly.
3. Release the slider.
   - *Expected*: Exactly one seek command is dispatched at the released position. Both phones synchronize audio to that exact timestamp within ~250ms.

---

### Step 5: 10-Second Airplane Mode Recovery
1. While music is actively playing in the Jam on **Device B**, turn ON **Airplane Mode** on Device B.
   - *Expected*: Audio continues playing from ExoPlayer cache or finishes buffer. Device B displays a subtle "Reconnecting…" chip after ~2s.
2. While Airplane Mode is still active on Device B, tap **Add Song** to add a song from search.
   - *Expected*: Device B does not instantly fail with an error; it buffers the command waiting up to 4s for active connection.
3. Within 10 seconds, turn OFF **Airplane Mode**.
   - *Expected*: Device B's network monitor detects the validated network, signals the reconnect loop to skip backoff delay, reconnects immediately without restarting the loop, and dispatches the buffered add-song command cleanly.

---

### Step 6: Wi-Fi to Cellular Handover
1. With music playing, disable Wi-Fi on **Device B** so it switches to Mobile Data (LTE/5G).
   - *Expected*: `NetworkMonitor` debounces the transition and verifies `NET_CAPABILITY_VALIDATED`. The app reconnects seamlessly without entering an infinite reconnect loop or triggering false `auth_failed` errors.

---

### Step 7: 21st Member Rejection (`ROOM_FULL`)
1. In a test room with 20 joined members (or simulated via test script/sockets), attempt to join as a 21st user.
   - *Expected*: Join is rejected with error code `ROOM_FULL`. The Android client displays the exact server message: `"This Jam is full (20 people)"`.
2. Have an existing member leave.
   - *Expected*: A slot opens, and the new user is able to join immediately.
3. Have an existing member drop connection and rejoin while room has 20 members.
   - *Expected*: Existing members are recognized and allowed to rejoin their existing session.

---

### Step 8: Multi-Device Login Session Move
1. Log in to User B's account on **Device B** and join Jam `WXYZ`.
2. On a third phone or tablet (or second profile), log in with the **same User B account** and create or join another Jam.
   - *Expected*: **Device B** receives `jam2:closed` with reason `moved` and displays: `"You joined another Jam on a different device"`, tearing down the old session cleanly.

---

### Step 9: Unauthorized Recommendation Removal
1. With User B as a guest without `queueManage` permission, scroll to the "Recommended for this Jam" section in JamScreen or FullPlayerSheet.
2. Tap the `X` button on a recommended song.
   - *Expected*: The song is not removed. A clear Toast is displayed: `"You do not have permission to manage recommended songs"`.

---

### Step 10: Sleep Timer "End of Track" in Jam Mode
1. On **Device B** while in a Jam, open the Sleep Timer menu and select **End of track**.
2. Let the current track play to completion.
   - *Expected*: When the track ends, Device B steps out locally (`stepOutLocally()`), pausing audio for User B only, while Device A continues playing for the rest of the Jam. The sleep timer resets to "Off".

---

## 4. Decisions and Questions for Review

1. **Trailing-Edge Throttle for Seek (Task C1)**:
   - *Decision*: Throttled `JamSessionManager.seekTo` using a trailing-edge window (250ms). When multiple seeks arrive within the window, the latest value is scheduled and sent after the window expires. In the UI (`FullPlayerSheet` and `JamScreen`), drag gestures maintain local state and only invoke `onSeekTo` on drag release (`onValueChangeFinished`), completely eliminating transport churn during user scrub.

2. **Redis Tombstoning (Task B2)**:
   - *Decision*: Implemented an in-memory 60-second tombstone set in `store.ts` during `deleteRoom`. This tombstone prevents `rehydrateFromRedis` from resurrecting a room that was just deleted, while in-memory active rooms remain unaffected.

3. **Multi-Device Handling (`moved` event) (Task B7)**:
   - *Decision*: In both `jam2:create` and `jam2:join`, old room socket connections for the same user ID are sent `jam2:closed` with `{ reason: 'moved' }`. Android displays: `"You joined another Jam on a different device"`.

4. **Zero-Duration `trackEnded` Validation (Task B6)**:
   - *Decision*: Enforced inside `backend-v2/src/jam2/reducer.ts` (`trackEnded` case) so it runs under the authoritative state reducer. For zero-duration tracks, non-system actors must be the room host or have `perms.playback == true`.

5. **Prompt Consolidation (Task C4)**:
   - *Decision*: Consolidated battery optimization dialog to `JamScreen.kt` using `BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)`. Removed the duplicate popup on cold start from `SynqAppRoot.kt`. For notification permission, removed the unprompted popup from `MainActivity.kt` and placed a clear rationale guidance dialog on first Jam entry.
