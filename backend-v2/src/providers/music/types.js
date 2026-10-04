"use strict";
/**
 * Normalized music types.
 *
 * These are the app-owned models that every music provider must map to.
 * Neither the Android client nor the rest of the backend should ever see
 * a third-party API's raw response shape — only these types.
 *
 * Design note: all IDs are strings (even if the upstream provider uses
 * numbers) so the interface stays provider-agnostic. The `providerId`
 * field records which provider the data came from, enabling future
 * multi-provider scenarios.
 */
Object.defineProperty(exports, "__esModule", { value: true });
