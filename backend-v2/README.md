# MusicSportsApp — Backend

Node.js / TypeScript / Express backend for the MusicSportsApp. Provides a REST API, real-time communication via Socket.IO, and persistent storage through MongoDB and Redis.

## Tech Stack

| Concern | Choice |
|---|---|
| Runtime | Node.js ≥ 20 |
| Language | TypeScript (strict mode) |
| Framework | Express |
| Database | MongoDB (Mongoose) |
| Cache / Realtime State | Redis (ioredis) |
| Realtime | Socket.IO |
| Validation | zod |
| Logging | pino (structured JSON) |
| Testing | vitest |

## Prerequisites

- **Node.js** ≥ 20
- **MongoDB** running locally or a remote connection string
- **Redis** running locally or a remote connection string

## Setup

1. **Install dependencies:**

   ```bash
   cd backend-v2
   npm install
   ```

2. **Configure environment variables:**

   ```bash
   cp .env.example .env
   ```

   Edit `.env` and fill in your actual values. See `.env.example` for all required variables.

3. **Start in development mode:**

   ```bash
   npm run dev
   ```

   The server will start with hot-reload (via `tsx watch`) on `http://localhost:3000`.

4. **Run tests:**

   ```bash
   npm test
   ```

5. **Type-check without emitting:**

   ```bash
   npm run lint:types
   ```

6. **Build for production:**

   ```bash
   npm run build
   npm start
   ```

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `NODE_ENV` | No | `development` (default), `production`, or `test` |
| `PORT` | No | Server port (default: `3000`) |
| `MONGODB_URI` | **Yes** | MongoDB connection string |
| `REDIS_URL` | **Yes** | Redis connection string |
| `CORS_ORIGINS` | **Yes** | Comma-separated list of allowed CORS origins |
| `JWT_ACCESS_SECRET` | **Yes** | Secret key for JWT access tokens |
| `JWT_REFRESH_SECRET` | **Yes** | Secret key for JWT refresh tokens |

All required variables are validated at startup using zod. The server will fail fast with a clear error message if any are missing or invalid.

## Project Structure

```
backend-v2/
├── src/
│   ├── config/          # Environment, MongoDB, Redis configuration
│   ├── controllers/     # HTTP request handling only — no business logic
│   ├── routes/          # API route definitions
│   ├── models/          # Mongoose models
│   ├── services/        # Application/business logic
│   ├── providers/       # External music and sports providers
│   ├── websocket/       # Socket.IO server, auth, rooms, realtime handlers
│   ├── jobs/            # Background workers and scheduled tasks
│   ├── middleware/      # Auth, validation, rate limiting, error handling
│   ├── utils/           # Logger, errors, response helpers, shared utilities
│   ├── app.ts           # Express application factory
│   └── server.ts        # Entry point — connects services and starts server
├── tests/               # vitest test files
├── .env.example         # Required environment variable names
├── .gitignore
├── package.json
├── tsconfig.json
├── vitest.config.ts
└── README.md
```

## API Conventions

- **Base path:** `/api/v1`
- **Success response:**
  ```json
  { "success": true, "data": {}, "message": null }
  ```
- **Error response:**
  ```json
  { "success": false, "error": { "code": "ERROR_CODE", "message": "Human readable error message" } }
  ```

## Available Endpoints

### Health

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/health` | Health check — returns MongoDB and Redis status |

### Authentication

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | No | Register a new user |
| POST | `/api/v1/auth/login` | No | Login with username/email + password |
| POST | `/api/v1/auth/refresh` | No | Refresh access token (send userId + refreshToken) |
| GET | `/api/v1/auth/me` | Yes | Get current user profile |
| POST | `/api/v1/auth/logout` | Yes | Logout current session (revoke one refresh token) |
| POST | `/api/v1/auth/logout-all` | Yes | Logout from all devices |
| GET | `/api/v1/auth/sessions` | Yes | List all active sessions/devices |
| DELETE | `/api/v1/auth/sessions/:id` | Yes | Revoke a specific session |
| POST | `/api/v1/auth/change-password` | Yes | Change password (revokes all sessions) |
| POST | `/api/v1/auth/delete-account` | Yes | Delete account (requires password confirmation) |

### Authentication Flow

1. **Register/Login** → returns `{ accessToken, refreshToken }` + user profile
2. **Use access token** in `Authorization: Bearer <token>` header for protected routes
3. **When access token expires** (15 min), call `/auth/refresh` with your `userId` and `refreshToken`
4. **Refresh rotation**: each refresh rotates the token — the old one is invalidated, a new pair is issued
5. **Logout** revokes the refresh token server-side

## Socket.IO

The Socket.IO server is initialized on the same HTTP server. Currently it provides basic connection/disconnect logging. Authentication middleware and room management will be added in future phases.

### Event Conventions (future)

- Room naming: `match:<matchId>`, `user:<userId>`
- Socket.IO distributes state — it is not the source of truth
- Clients must resynchronize authoritative state on reconnect

## Architectural Decisions

1. **App/Server separation:** `app.ts` creates the Express application without starting the server or connecting to databases. This allows importing the app in tests (e.g., supertest) without side effects.

2. **Environment validation at startup:** All environment variables are validated via zod before the application starts. Missing or invalid configuration causes an immediate, clear failure rather than a cryptic runtime error later.

3. **Centralized error handling:** All errors flow through a single error handler middleware. Operational errors (AppError subclasses) are returned with their status code and machine-readable code. Unknown errors are logged server-side and return a generic 500 — stack traces are never exposed in production.

4. **Structured logging:** pino produces JSON logs in production for machine consumption and pretty-prints in development for human readability. Sensitive fields (passwords, tokens) are automatically redacted.

5. **Redis via ioredis:** Chosen over the official `redis` package for its superior reconnection handling, Lua scripting support, and cluster/sentinel support — all of which will be useful as the application grows.

6. **Refresh token rotation:** Each use of a refresh token invalidates it and issues a new one. This limits the damage window if a refresh token is stolen. Refresh tokens are stored as bcrypt hashes so even a database leak doesn't directly expose usable tokens.

7. **Opaque refresh tokens:** Refresh tokens are random hex strings (not JWTs). This enforces server-side validation — every refresh hits the database, which is necessary for revocability. Access tokens remain stateless JWTs for performance.

8. **Password change revokes all sessions:** Changing a password invalidates every active refresh token, forcing re-authentication on all devices.

