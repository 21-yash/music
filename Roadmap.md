# ROADMAP.md — MusicSportsApp

This file tracks *where we are*, separate from `CLAUDE.md` (which holds conventions that never change). Update the `Status` line for a phase when it's finished, and move `CURRENT` forward yourself before the next session.

**Rule for every session: only work on phase(s) marked `CURRENT`. Do not implement anything under a `PENDING` phase, even if it looks easy, small, or related to what you're already doing. If finishing the current phase naturally reveals a need from a later phase, name it and stop — don't build it.**

When a `CURRENT` phase is complete, give a concise summary of: what was created, the final project structure, commands to install/run/test it, environment variables required, tests performed, and any decisions that need approval before moving on. After that, mark the current phase as done and mark the next phase as current in this file itself.

---

## Phase 0 — Architecture & contracts
**Status:** DONE

- Define project conventions
- Define API versioning
- Define API response format
- Define error format
- Define environment configuration strategy
- Define MongoDB conventions
- Define Redis conventions
- Define logging strategy
- Define security baseline
- Define Socket.IO event conventions
- Document architectural decisions

## Phase 1 — Backend foundation
**Status:** DONE

- Initialize Node.js project, configure TypeScript
- Configure Express, environment variables, MongoDB connection, Redis connection, Socket.IO
- Configure CORS, Helmet/security middleware, JSON body parsing
- Configure request validation foundation (zod), structured logging (pino)
- Centralized error handling, 404 handling, health endpoint
- Graceful shutdown; clean application/server separation
- `.env.example`, README with setup instructions
- Basic backend tests (vitest)

**Do not build in this phase:** Android code, authentication, music features, sports features.

## Phase 2 — Authentication
**Status:** DONE

- User model, password hashing
- Register, login, logout
- Access tokens, refresh tokens
- Current user, session/device management
- Password change, account deletion

## Phase 3 — Android foundation
**Status:** DONE

- Create Android project: Kotlin, Jetpack Compose, Material 3
- Navigation, dependency injection, networking, Room
- Repository layer, ViewModels, Flow, error handling, theme

## Phase 4 — App shell
**Status:** DONE

- Home, Music, Sports, Search, Library, Profile
- Navigation architecture

## Phase 5 — Music system
**Status:** DONE

- Music provider abstraction; JioSaavn provider 
- Search, songs, artists, albums, playlists
- Track metadata, artwork
- Media3/ExoPlayer integration


## Phase 6 — User library
**Status:** CURRENT

- Favorites, liked songs, recently played, user playlists, following

## Phase 7 — Sports architecture
**Status:** PENDING

- Sports provider abstraction; cricket provider
- Normalized sports models: teams, competitions, matches, events, statistics

## Phase 8 — Live scores
**Status:** PENDING

- Sports worker, Redis live state
- Socket.IO match rooms, realtime match updates
- Reconnect/resync flow, live match details

## Phase 9 — Sports Android UI
**Status:** PENDING

- Live / upcoming / completed matches
- Match details, scoreboards, commentary, statistics, standings

## Phase 10 — Followed teams & notifications
**Status:** PENDING

- Follow teams, notification preferences
- Match start / score / event / result notifications
- FCM integration

## Phase 11 — Widgets
**Status:** PENDING

- Jetpack Glance: music widget, live sports widget

## Phase 12 — Background processing
**Status:** PENDING

- WorkManager: scheduled sync, cleanup jobs, notification-related background work

## Phase 13 — Offline support
**Status:** PENDING

- Room caching, offline-first strategy where appropriate
- Network recovery, realtime resynchronization

## Phase 14 — Performance & security
**Status:** PENDING

- Database indexes, Redis optimization, API rate limiting
- Authentication hardening, input validation, caching
- Memory/performance profiling, network optimization

## Phase 15 — Production infrastructure
**Status:** PENDING

- Production environment config, logging, monitoring, health checks
- Backups, deployment, CI/CD

## Phase 16 — Testing
**Status:** PENDING

- Unit, integration, API, repository, Socket.IO tests
- Android UI tests, end-to-end tests

## Phase 17 — Release
**Status:** PENDING

- Production build, signing, release configuration
- Play Store preparation, crash reporting, final security review

## Phase 18 — Future iOS
**Status:** PENDING

- Evaluate Kotlin Multiplatform extraction
- Share domain models, share networking/data logic
- Build iOS UI separately, reuse backend APIs