package com.zagot.syncengine.util

import kotlinx.serialization.json.Json

/**
 * Shared Json instance for outbox payload serialization.
 *
 * Uses `encodeDefaults = true` to ensure ALL fields are present in every payload.
 * Postgrest batch upsert requires all records to have identical key sets —
 * without this, fields with defaults (e.g., `server_updated_at = null`,
 * `is_voided = false`) are conditionally omitted, causing key mismatches.
 */
val SyncJson = Json { encodeDefaults = true }
