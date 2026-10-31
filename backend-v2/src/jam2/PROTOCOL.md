# Synq Jam 2.0 Wire Protocol Specification

Version: **2**  
Transport: **Socket.IO 4.8+**  
Namespace: Default (`/`)

---

## 1. Overview & Architectural Principles

Jam 2.0 replaces the legacy host-streams-position model with a **server-authoritative room**:

1. **Server owns state**: The server owns playback status, current position anchor, queue, history, and room membership.
2. **Host is an admin role**: The host does not broadcast their playback position. Any member with appropriate permissions can control playback or queue songs.
3. **Resilient handover**: If a host disconnects silently, a 20-second grace timer is started. If the host does not return, host duties automatically pass to the earliest-joined online member.
4. **Recommended tail**: The server automatically fetches and maintains a tail of recommended songs (`recommended: true`) behind user-added songs. Recommended items auto-play when user queue is exhausted.
5. **Deterministic sync**: Position is anchored to server timestamps. Expected client playback position at any given time $T$ is:
   $$\text{expectedPosition} = \begin{cases} \text{positionMs} + (T - \text{anchorServerTs}) & \text{if status} = \text{'playing'} \\ \text{positionMs} & \text{if status} \in \{\text{'paused'}, \text{'idle'}\} \end{cases}$$
   Clamped between $0$ and song duration in milliseconds.

---

## 2. Connection & Authentication

All Socket.IO connections must provide authentication credentials in the handshake `auth` payload:

```json
{
  "auth": {
    "apiKey": "<APP_API_KEY>",
    "token": "<JWT_ACCESS_TOKEN>"
  }
}
```

- If `apiKey` or `token` is missing or invalid, the connection is rejected at handshake.
- The server extracts `userId` and `username` from the verified token and attaches them to `socket.data.user`.

---

## 3. Client $\to$ Server Events

All client-to-server events use Socket.IO acknowledgements (`ack`).
Ack signatures return a union of success or failure:
- Success: `{ ok: true, ...data }`
- Failure: `{ ok: false, code: Jam2ErrorCode, message: string }`

### Error Codes (`Jam2ErrorCode`)
- `PROTOCOL`: Incompatible client protocol version (must be 2).
- `NOT_FOUND`: Room not found or invalid room code.
- `NOT_MEMBER`: Socket or user is not a member of the room.
- `FORBIDDEN`: Action denied due to insufficient permissions, or kicked from room within 10 minutes ("You were removed from this Jam by the host.").
- `INVALID`: Malformed payload or validation error.
- `QUEUE_FULL`: Queue reached user capacity (50 user-added songs).
- `RATE_LIMITED`: Too many commands, failed joins, or time requests.

---

### 3.1 `jam2:create`
Creates a new Jam room. The caller becomes the host.

**Request payload:**
```json
{
  "protocol": 2,
  "avatarUrl": "https://example.com/avatar.jpg" // optional
}
```

**Acknowledgement response:**
```json
{
  "ok": true,
  "room": <RoomSnapshot>
}
```

---

### 3.2 `jam2:join`
Joins or resumes a session in an existing Jam room.
- If the user was kicked from this room within the last 10 minutes, the server returns `{ ok: false, code: "FORBIDDEN", message: "You were kicked from this room. Try again later." }`.
- **Version Epoch Rule:** The full `room` snapshot received from `jam2:join` represents a new synchronization epoch. The client must accept it unconditionally and set its local version to `room.version`. Subsequent state updates or diffs reject any payload with `version <= localVersion`.

**Request payload:**
```json
{
  "protocol": 2,
  "code": "ABCD",
  "avatarUrl": "https://example.com/avatar.jpg" // optional
}
```

**Acknowledgement response:**
```json
{
  "ok": true,
  "room": <RoomSnapshot>
}
```

---

### 3.3 `jam2:sync`
Requests a fresh, full snapshot of the room without mutating state or triggering room broadcasts. Used by clients after reconnection or packet-loss detection.
- **Version Epoch Rule:** Like `jam2:join`, the snapshot returned in `jam2:sync` establishes a new epoch that clients accept unconditionally.

**Request payload:**
```json
{
  "code": "ABCD"
}
```

**Acknowledgement response:**
```json
{
  "ok": true,
  "room": <RoomSnapshot>
}
```

---

### 3.4 `jam2:leave`
Explicitly leaves the current room.
- If the host leaves, host role immediately transfers to the earliest-joined online member.
- If the room becomes empty, it is destroyed.

**Request payload:**
```json
{
  "code": "ABCD"
}
```

**Acknowledgement response:**
```json
{
  "ok": true
}
```

---

### 3.4 `jam2:leave`
Explicitly leaves the current room.
- If the host leaves, host role immediately transfers to the earliest-joined online member (falling back to offline members if no other member is online).
- If the room becomes empty, it is destroyed.

**Request payload:**
```json
{
  "code": "ABCD"
}
```

**Acknowledgement response:**
```json
{
  "ok": true
}
```

---

### 3.5 `jam2:end`
Closes the Jam room for all participants. **Host only.**
- Emits `jam2:closed { "reason": "ended" }` to all participants.

**Request payload:**
```json
{
  "code": "ABCD"
}
```

**Acknowledgement response:**
```json
{
  "ok": true
}
```

---

### 3.6 `jam2:time`
Ping for server clock synchronization (calculating client-server round-trip time and clock offset).

**Request payload:**
`{}` or empty.

**Acknowledgement response:**
```json
{
  "ok": true,
  "serverNow": 1729000000000
}
```

---

### 3.7 `jam2:cmd`
Dispatches a state-mutating command to the room.

**Acknowledgement response (Standard):**
```json
{
  "ok": true,
  "version": 42
}
```

**Acknowledgement response for `trackEnded`:**
```json
{
  "ok": true,
  "applied": true,
  "version": 42
}
```

**Acknowledgement response for `queueAdd` and `queueAddMany`:**
```json
{
  "ok": true,
  "added": 5,
  "skipped": 1,
  "skippedDuplicate": 1,
  "skippedFull": 0,
  "version": 42
}
```

#### Command Types

| Command `type` | Parameters | Required Permission | Description |
|---|---|---|---|
| `play` | none | `playback` | Resumes playback or starts first track if idle |
| `pause` | none | `playback` | Pauses playback and freezes `positionMs` |
| `seek` | `positionMs: number` | `playback` | Seeks current track to `positionMs` |
| `next` | none | `playback` | Skips to next track in queue |
| `prev` | none | `playback` | If position > 3s, restarts track; otherwise pops previous from history |
| `playItem` | `qid: string` | `playback` | Jumps directly to a specific queued track. Extracts and plays that item; all preceding and following items remain in the queue in their relative order, and only the previously playing track is pushed to history |
| `trackEnded` | `trackSeq: number` | Any member | Reports that track finished playing. Strictly validated (see below) |
| `queueAdd` | `song: Song`, `playNext?: boolean` | `queueAdd` | Appends song or inserts immediately after nowPlaying. Moves existing to front if `playNext` is true. |
| `queueAddMany` | `songs: Song[]` (1-20) | `queueAdd` | Batch adds multiple songs to the user queue, skipping duplicates |
| `promote` | `qid: string` | `queueAdd` | Moves a recommended item into user section and marks as added by user |
| `queueRemove` | `qid: string` | Own item: none. Recs: `queueAdd`. Others: `queueManage`. | Removes item from queue |
| `queueMove` | `qid: string`, `toIndex: number` | `queueManage` | Reorders user songs within queue |
| `setMemberPerms` | `userId: string`, `perms: Record<string, boolean>` | Host only | Updates permissions for a specific member |
| `setDefaults` | `perms: Record<string, boolean>`, `applyToExisting?: boolean` | Host only | Updates default permissions for future joiners, and optionally updates all current members when `applyToExisting: true` |
| `kick` | `userId: string` | Host only | Kicks a member (banned for 10m; kicked socket receives `jam2:closed { reason: 'kicked' }`) |
| `transferHost` | `userId: string` | Host only | Transfers host role to another member |

#### `trackEnded` Eligibility Rules
`trackEnded` is idempotent and guarded against rogue or out-of-order skip abuse:
- For a non-system actor:
  - Ignored unless `status === 'playing'` and `trackSeq` matches current `nowPlaying.trackSeq`.
  - For songs with known duration (`duration > 0`), accepted only if `expectedPosition(now) >= durationMs - 6000` (within 6 seconds of track end).
  - For songs with unknown duration (`duration === 0`), accepted only if `(now - anchorServerTs + positionMs) >= 15000` (at least 15 seconds elapsed).
  - If conditions are not met, the reducer returns `{ room unchanged, events: [] }` with no error, ensuring late or duplicate reports are completely harmless.
- System actor (internal track fallback timer and rehydration catch-up) is exempt from these duration checks.

---

## 4. Server $\to$ Client Broadcasts

### 4.1 `jam2:state`
Broadcast to all room members whenever the room state changes (version increments).

**Payload (`RoomSnapshot`):**
```json
{
  "code": "ABCD",
  "version": 5,
  "serverNow": 1729000010000,
  "hostUserId": "user_123",
  "createdAt": 1729000000000,
  "members": [
    {
      "userId": "user_123",
      "username": "Alice",
      "avatarUrl": "https://example.com/alice.png",
      "joinedAt": 1729000000000,
      "online": true,
      "lastSeenAt": 1729000010000,
      "perms": {
        "queueAdd": true,
        "playback": true,
        "queueManage": true
      }
    }
  ],
  "defaults": {
    "queueAdd": true,
    "playback": false,
    "queueManage": false
  },
  "queue": [
    {
      "qid": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
      "song": { "id": "song_1", "title": "Track Title", "artists": [{"id": "a1", "name": "Artist"}], "duration": 210 },
      "addedByUserId": "user_123",
      "addedAt": 1729000005000,
      "recommended": false
    }
  ],
  "history": [],
  "nowPlaying": {
    "item": {
      "qid": "8a0cea3c-2a6c-4cad-8acc-1a0c6a2cba5c",
      "song": { "id": "song_0", "title": "Current Song", "duration": 180 },
      "addedByUserId": "user_123",
      "addedAt": 1729000000000,
      "recommended": false
    },
    "status": "playing",
    "positionMs": 12450,
    "anchorServerTs": 1729000000000,
    "trackSeq": 1
  }
}
```

---

### 4.2 `jam2:tick`
Periodic lightweight heartbeat emitted every 10 seconds to room members while active. Used by clients for clock drift correction and keepalive without sending full snapshot diffs.

**Payload (`Jam2Tick`):**
```json
{
  "code": "ABCD",
  "serverTime": 1729000010000,
  "nowPlaying": {
    "positionMs": 12450,
    "anchorServerTs": 1729000000000,
    "status": "playing",
    "trackSeq": 1
  }
}
```

---

### 4.3 `jam2:event`
Ephemeral toast notifications for user feedback.

**Payload:**
```json
{
  "kind": "member_joined" | "member_left" | "kicked" | "host_changed" | "server_restarting" | "perm_changed",
  "userId": "user_456",
  "username": "Bob"
}
```

---

### 4.4 `jam2:closed`
Emitted when the room is terminated. Clients must navigate out of the Jam screen.

**Payload:**
```json
{
  "reason": "ended" | "kicked" | "expired"
}
```

---

## 5. Client Synchronization Guide (Android ExoPlayer)

1. **Clock Offset Estimation:**
   Client issues `jam2:time` every 30s.  
   $$\text{RTT} = t_{\text{client\_receive}} - t_{\text{client\_send}}$$  
   $$\text{serverNowEstimated} = t_{\text{serverNow}} + \frac{\text{RTT}}{2}$$  
   $$\text{clockOffset} = \text{serverNowEstimated} - t_{\text{client\_receive}}$$

2. **Playback Adjustment:**
   - Let $\Delta = \text{exoPlayer.currentPosition} - \text{expectedPosition}$.
   - If $|\Delta| > 2000\text{ ms}$: hard seek `exoPlayer.seekTo(expectedPosition)`.
   - If $300\text{ ms} < |\Delta| \le 2000\text{ ms}$: soft speed ramp (e.g. set playback speed to $0.98\times$ or $1.02\times$ until $|\Delta| \le 100\text{ ms}$, then restore $1.0\times$).
   - If $|\Delta| \le 300\text{ ms}$: maintain normal playback ($1.0\times$).

