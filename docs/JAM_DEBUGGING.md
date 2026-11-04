# Jam Debugging & Diagnostic Guide

This guide details how to diagnose Jam connection drops, reconnect loops, and socket lifecycle transitions across the Android client and the Node.js backend.

---

## 1. Reading Disconnect Reasons

Socket.IO / Engine.IO disconnect events provide explicit string reasons explaining why a connection was terminated.

| Disconnect Reason | Root Cause | Meaning & Action |
|-------------------|------------|------------------|
| `ping timeout` | Network / Device | The client did not receive a server heartbeat pong within the timeout window (typically 25s). Indicates radio dormancy, poor signal, network packet loss, or aggressive device battery sleep. |
| `transport close` | Network / Device | The underlying TCP/WebSocket connection was closed cleanly by the peer or socket layer (e.g. Wi-Fi dropped, cell tower handover, TCP reset, or server restart). |
| `transport error` | Network / Carrier / Middlebox | A transport-level network exception occurred during transmission (e.g. TCP Reset (RST), connection refused, host unreachable, SSL handshake error, or carrier NAT table expiration). |
| `client namespace disconnect` | Client App Logic | The Android app explicitly closed the socket connection via `disconnect()` (e.g. failing a health-check ping, user leaving the Jam, or switching rooms). |
| `server namespace disconnect` | Server Logic | The server explicitly kicked or terminated the client session (e.g. kicked by host, session ended). |
| `parse error` | Serialization | Malformed payload received on the transport. |

---

## 2. Android Client Diagnostics (`adb logcat`)

### Logcat Tags to Filter
Filter logcat output using the following tags:
```bash
adb logcat -s JamClient:V SocketIoTransport:V JamSessionMgr:V NetworkMonitor:V
```

Or regex match on all Jam tags:
```bash
adb logcat | grep -E "JamClient|SocketIoTransport|JamSessionMgr|NetworkMonitor"
```

### Key Android Log Patterns
- **NetworkMonitor Event Logs** (includes network id, validation, transport, and decision):
  ```text
  I/NetworkMonitor: Available: id=102, validated=false, transport=CELLULAR -> queued
  I/NetworkMonitor: CapabilitiesChanged: id=102, validated=true, transport=CELLULAR -> handover started
  I/NetworkMonitor: CapabilitiesChanged: id=101, validated=true, transport=WIFI -> ignored
  I/NetworkMonitor: Lost: id=101, validated=false, transport=WIFI -> ignored
  ```
- **Transport Disconnect & Reason** (includes active network transport type):
  ```text
  I/SocketIoTransport: Disconnected from server, reason: transport error
  I/JamClient: Transport Disconnected: reason=transport error, transport=CELLULAR
  ```
- **Reconnect Trigger**:
  ```text
  I/JamClient: Starting reconnect loop for room ABCD. Trigger: transport disconnected (transport error), transport=CELLULAR
  ```
- **Connect Error**:
  ```text
  W/SocketIoTransport: Connect error: <error message>
  W/JamClient: Transport ConnectError: <error message>
  ```
- **Health Check Ping Failure**:
  ```text
  W/JamClient: Health-check ping failed, retrying in 500ms...
  I/JamClient: Starting reconnect loop for room ABCD. Trigger: health-check failed, transport=WIFI
  ```
- **Token Refresh Transient Failure**:
  ```text
  W/JamClient: Token refresh transient failure, retrying...
  ```

---

## 3. Interpreting Network vs Socket Failures & Carrier Diagnostics

### Did Android drop the socket, switch networks, or did the socket die while network was intact?

Correlating `NetworkMonitor` logs with `JamClient` logs tells the exact story:

1. **Android switched networks (Handover)**:
   - `NetworkMonitor` logs:
     ```text
     I/NetworkMonitor: Available: id=102, validated=false, transport=CELLULAR -> queued
     I/NetworkMonitor: CapabilitiesChanged: id=102, validated=true, transport=CELLULAR -> handover started
     ```
   - Followed by `JamClient`:
     ```text
     I/JamClient: Transport Disconnected: reason=transport close, transport=CELLULAR
     ```
   - **Diagnosis**: The device physically changed its default route (e.g. walked away from Wi-Fi, handover to mobile data). `NetworkMonitor` detected the change and triggered proactive reconnection.

2. **Android dropped the network entirely (Loss of connectivity)**:
   - `NetworkMonitor` logs:
     ```text
     I/NetworkMonitor: Lost: id=101, validated=false, transport=WIFI -> ignored
     ```
   - Followed by `isOnline=false` in state.
   - **Diagnosis**: Radio turned off, airplane mode enabled, or zero signal available.

3. **Socket died while Android network was 100% intact (Carrier NAT / Middlebox Timeout)**:
   - `NetworkMonitor` logs: **NONE** (no `Available`, no `CapabilitiesChanged`, no `Lost`). Android's default network remains active and validated.
   - `JamClient` logs:
     ```text
     I/SocketIoTransport: Disconnected from server, reason: transport error
     I/JamClient: Transport Disconnected: reason=transport error, transport=CELLULAR
     I/JamClient: Starting reconnect loop for room ABCD. Trigger: transport disconnected (transport error), transport=CELLULAR
     ```
   - **Diagnosis**: Android's OS-level network connection was continuous and stable. The TCP socket was severed upstream by the cellular carrier's Carrier-Grade NAT (CGNAT) or an intermediate proxy.

### Diagnosing Jio vs Airtel vs Wi-Fi Differences

- **Jio CGNAT vs Airtel vs Wi-Fi**:
  - **Jio Mobile Data**: Jio employs aggressive Carrier-Grade NAT (CGNAT) state-table timeouts (~45 to 60 seconds on idle or low-cadence TCP streams). If no bidirectional packet travels through the socket within that window, Jio's middlebox discards the state mapping. The next outgoing packet from the client or incoming packet from the server encounters an expired route or receives a TCP `RST`, surfacing as Engine.IO `transport error`.
  - **Airtel Mobile Data**: Airtel's CGNAT timeout is significantly longer (several minutes), allowing long-lived WebSockets with default heartbeat intervals to survive without frequent reconnects.
  - **Wi-Fi**: Home and office Wi-Fi routers generally maintain NAT translation tables for 5 to 15+ minutes, producing zero idle disconnects.

- **How to confirm on a user device**:
  1. **Wi-Fi Baseline**: Connect the device to Wi-Fi for 10-15 minutes in a Jam. If `transport error` does NOT occur, server-side infrastructure and client code are verified healthy.
  2. **A/B Carrier Comparison**: Run Jam on Device A (Jio mobile data) and Device B (Airtel mobile data or Wi-Fi) simultaneously. If Device A logs `reason: transport error, transport=CELLULAR` every 60-90s while Device B never disconnects, the root cause is Jio's CGNAT timeout.
  3. **Inspection of NetworkMonitor**: Verify that `NetworkMonitor` shows 0 network loss events during the disconnects, proving the cellular connection itself remained active.

---

## 4. Server-Side Log Fields (`backend-v2`)

The backend logs socket events structured with Bunyan / Pino JSON formatting.

### Key Log Fields
- `socketId`: Unique Socket.IO identifier for the connection.
- `userId`: Authenticated user ID owning the connection (from JWT).
- `reason`: The Socket.IO disconnect reason.
- `durationMs`: How long the socket was connected before disconnecting (milliseconds).
- `code`: Jam room code when applicable.

### Sample Server Disconnect Log
```json
{
  "level": 30,
  "time": 1730548800000,
  "pid": 12345,
  "hostname": "backend-v2",
  "socketId": "s7b_9hN12K8l",
  "userId": "6724a180b2a4c1001",
  "reason": "ping timeout",
  "durationMs": 142050,
  "msg": "Socket.IO client disconnected"
}
```

### Analyzing Scenarios
- **Phone screen turns off and disconnects after 30s**:
  - Client log: `Disconnected from server, reason: ping timeout`
  - Server log: `"reason": "ping timeout", "durationMs": ...`
  - Diagnosis: Device power management / Doze mode put the Wi-Fi/cellular radio or background process to sleep. Check battery optimization exemption.
- **Client performs health-check self-recovery**:
  - Client log: `Health-check failed` followed by client-initiated `disconnect()`.
  - Server log: `"reason": "client namespace disconnect"`.
  - Diagnosis: Client detected an unresponsive socket and cleanly recycled the transport.
- **Immediate disconnect on connect (< 100ms)**:
  - Server log: `Socket.IO connection rejected: invalid auth token` or `missing auth token`.
  - Diagnosis: Expired or malformed JWT token passed during socket handshake.

