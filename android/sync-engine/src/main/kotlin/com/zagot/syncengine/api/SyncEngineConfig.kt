package com.zagot.syncengine.api

/**
 * Configuration for the sync engine. Provide via Hilt or pass to [SyncEngine.start].
 *
 * All values have sensible defaults for typical mobile apps with Supabase backend.
 */
data class SyncEngineConfig(
    /** Interval between safety sync runs (WorkManager periodic). Min 15 min (WorkManager constraint). */
    val safetySyncIntervalMinutes: Long = 15L,

    /** Debounce delay for LIVE push after outbox change. Batches rapid successive writes. */
    val livePushDebounceMs: Long = 200L,

    /** Max records per push HTTP request. Prevents oversized payloads. */
    val pushBatchSize: Int = 200,

    /** Max retries for catch-up when realtime buffer overflows repeatedly. */
    val maxCatchUpRetries: Int = 3,

    /** How long to keep synced outbox entries before pruning (ms). */
    val outboxPruneRetentionMs: Long = 30 * 60 * 1_000L,

    /** Delay before FK constraint retry on realtime events (ms). */
    val fkRetryDelayMs: Long = 500L,
)
