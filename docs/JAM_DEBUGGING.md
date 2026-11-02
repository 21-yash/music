# Jam Debugging & Diagnostic Guide

This guide details how to diagnose Jam connection drops, reconnect loops, and socket lifecycle transitions across the Android client and the Node.js backend.

---

## 1. Reading Disconnect Reasons

Socket.IO / Engine.IO disconnect events provide explicit string reasons explaining why a connection was terminated.

| Disconnect Reason | Root Cause | Meaning & Action |
|-------------------|------------|------------------|
| `ping timeout` | Network / Device | The client did not receive a server heartbeat pong within the timeout window (typically 25s). Indicates radio dormancy, poor signal, network packet loss, or aggressive device battery sleep. |
| `transport close` | Network / Device | The underlying TCP/WebSocket connection was closed unexpectedly (e.g. Wi-Fi dropped, cell tower handover, TCP reset, or server restart). |
| `transport error` | Network / Device | A transport-level network exception occurred during transmission (e.g. SSL error, connection refused, host unreachable). |
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
- **Transport Disconnect & Reason**:
  ```text
  I/SocketIoTransport: Disconnected from server, reason: ping timeout
  I/JamClient: Transport Disconnected: reason=ping timeout
  ```
- **Connect Error**:
  ```text
  W/SocketIoTransport: Connect error: <error message>
  W/JamClient: Transport ConnectError: <error message>
  ```
- **Reconnect Trigger**:
  ```text
  I/JamClient: Starting reconnect loop for room ABCD. Trigger: transport disconnected (ping timeout)
  ```
- **Health Check Ping Failure**:
  ```text
  W/JamClient: Health-check ping failed, retrying in 500ms...
  I/JamClient: Starting reconnect loop for room ABCD. Trigger: health-check failed
  ```
- **Token Refresh Transient Failure**:
  ```text
  W/JamClient: Token refresh transient failure, retrying...
  ```

---

## 3. Server-Side Log Fields (`backend-v2`)

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

