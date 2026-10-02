# CLAUDE.md — MusicSportsApp

Persistent project instructions. Read this at the start of every session — you should not need to be re-told any of this.

For "what phase are we on / what should I build right now," see `ROADMAP.md`, not this file. This file is stack, rules, and conventions that stay true for the life of the project.

## Project

Android-first music streaming and live sports scores application with a Node.js backend. The architecture must support future iOS development (via Kotlin Multiplatform extraction) without requiring a rewrite.

**Development strategy:** build incrementally according to `ROADMAP.md`. Never implement a phase beyond the one marked `CURRENT` there, even if it looks easy or related.

## Role

You are acting as a senior full-stack architect and engineer. That means:
- Design production-quality architecture, not prototypes.
- Write maintainable, scalable code — but don't over-engineer for problems you don't have yet.
- Keep backend and Android responsibilities clearly separated.
- Explain non-obvious architectural decisions as you make them.
- Avoid unnecessary dependencies.
- Never write placeholder implementations that will need a major rewrite later. If something can't be done properly yet, say so instead of faking it.

## Tech stack

### Android
| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Design system | Material 3 |
| Architecture | Clean Architecture, feature-based organization |
| Async | Kotlin Coroutines + Flow |
| Networking | Retrofit + OkHttp |
| Local database | Room |
| Audio | Media3 / ExoPlayer |
| Realtime | Socket.IO client (`socket.io-client-java`) |
| Notifications | Firebase Cloud Messaging |
| Background work | WorkManager |
| Widgets | Jetpack Glance |

### Backend
| Concern | Choice |
|---|---|
| Runtime | Node.js ≥ 20 |
| Language | TypeScript (strict mode) |
| Framework | Express |
| Package manager | npm |
| Database | MongoDB |
| ODM | Mongoose |
| Cache / realtime state | Redis |
| Realtime | Socket.IO |
| Auth | JWT access tokens + refresh tokens |
| Validation | zod |
| Logging | pino (structured JSON logs) |
| Testing | vitest |

## Architecture principles

- The backend is a new, clean project. It must not depend on the old Daily Hub backend.
- The old backend may only be consulted as reference for useful existing ideas (e.g. the cricket provider, JioSaavn integration). Do not copy its architecture wholesale, and do not modify it.
- External APIs (music, sports) must be hidden behind provider/service abstractions. Android and the rest of the backend should never see a third-party API's raw response shape — only app-owned normalized models.
- REST is for normal request/response. Socket.IO is for realtime events and state updates. FCM is for push notifications when the client isn't actively connected.
- Redis holds temporary, cached, high-speed, and realtime state. MongoDB holds persistent application data. Neither is a substitute for the other.
- Music audio is never transmitted through Socket.IO. Media3/ExoPlayer handles actual playback on Android; Socket.IO only ever carries metadata/state/events.
- Live sports data is collected by backend workers, not by every Android client polling the sports provider directly.
- Socket.IO distributes state/events — it is not the source of truth. Redis/MongoDB/provider state is authoritative. After a reconnect, the client must resynchronize authoritative state rather than assume it received every missed event.
- Keep business logic independent of Android-specific APIs wherever practical, so a future Kotlin Multiplatform extraction stays realistic.
- Don't over-engineer the initial implementation. Don't introduce microservices unless there's a demonstrated need.
- Use API versioning from the beginning (`/api/v1/...`).

## Backend project structure

```
backend/
├── src/
│   ├── config/       # Environment, MongoDB, Redis configuration
│   ├── controllers/  # HTTP request handling only — no business logic
│   ├── routes/       # API route definitions
│   ├── models/        # Mongoose models
│   ├── services/      # Application/business logic
│   ├── providers/      # External music and sports providers
│   ├── websocket/       # Socket.IO server, auth, rooms, realtime handlers
│   ├── jobs/            # Background workers and scheduled tasks
│   ├── middleware/      # Auth, validation, rate limiting, error handling
│   ├── utils/           # Logger, errors, response helpers, shared utilities
│   ├── app.ts
│   └── server.ts
├── tests/
├── .env
├── .env.example
├── package.json
├── tsconfig.json
└── README.md
```

## API conventions

Base path: `/api/v1`

Success response:
```json
{ "success": true, "data": {}, "message": null }
```

Error response:
```json
{ "success": false, "error": { "code": "ERROR_CODE", "message": "Human readable error message" } }
```

## Realtime architecture (Socket.IO)

Future sports data flow: Sports API → Sports Worker → normalize external data → store current state in Redis → emit updates via Socket.IO → send to match-specific rooms → Android clients update local state.

Room naming: `match:<matchId>`, `user:<userId>`.

Reminder: Socket.IO distributes, it does not decide. Redis/DB/provider state is authoritative — always resync on reconnect rather than trusting the client received every event.

## Coding rules

- TypeScript strict mode.
- Prefer `async`/`await`.
- Avoid `any` unless genuinely unavoidable.
- Meaningful names; no abbreviations that aren't obvious.
- Controllers stay thin — business logic lives in services, external integrations live in providers, infra initialization stays separate from business logic.
- No giant files, no logic duplication, no dumping everything into `index.ts`.
- Centralized error types; consistent API responses everywhere.
- Document non-obvious architectural decisions inline or in the README.
- Do not prematurely implement future features — see `ROADMAP.md` for what's in scope right now.

## Security requirements

- Never hardcode secrets. Never commit `.env`.
- `.env.example` lists required variable names only, no real secrets.
- Validate environment variables at startup; fail fast with a clear error if one is missing.
- Secure HTTP headers (Helmet), explicit CORS configuration, reasonable JSON body size limits.
- Never expose stack traces in production responses; use safe, generic client-facing error messages.
- Never log passwords, JWTs, refresh tokens, or other secrets.
- Architecture should be rate-limiting-ready even before rate limiting is implemented.

## Environment variables

Only add variables actually required by the current implementation — don't invent config for features that don't exist yet.

Currently expected: `NODE_ENV`, `PORT`, `MONGODB_URI`, `REDIS_URL`, `CORS_ORIGINS`, `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET`.

## Old backend reference policy

If an old backend exists in the workspace: inspect it only to understand reusable concepts (cricket integration, JioSaavn integration, Socket.IO patterns). Do not modify it, do not copy its architecture wholesale, and do not pull in its dependencies unless individually justified in the new project.

## Workflow rules

- Work incrementally. After each meaningful implementation step, inspect the result and fix errors before continuing.
- Never silently skip a failing test.
- Never move on to a later roadmap phase just because it looks easy or related to what you're doing — check `ROADMAP.md`'s current status first.
- Don't overwrite or delete existing user files without explicit confirmation.
- Git: do not auto-commit. Stage and describe changes; wait for explicit go-ahead before committing.

## Quality gate — check before declaring any phase finished

- TypeScript compiles without errors.
- The backend starts cleanly with valid environment variables.
- MongoDB and Redis connection handling both work (including graceful failure if temporarily unavailable, where applicable).
- Socket.IO server initializes correctly.
- Health endpoint, 404 handling, and centralized error handling all work.
- Graceful shutdown works.
- No secrets are hardcoded; `.env` is git-ignored; `.env.example` exists.
- README has setup instructions.
- Tests pass.