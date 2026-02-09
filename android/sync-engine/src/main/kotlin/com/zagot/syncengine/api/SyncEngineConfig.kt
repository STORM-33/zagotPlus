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

    /** Max records per pull HTTP request. Drives cursor-based pagination. */
    val pullPageSize: Int = 1000,

    /** Overlap window subtracted from last_synced_at when pulling (ms). */
    val pullOverlapWindowMs: Long = 5_000L,

    /** Max realtime events to buffer during catch-up before overflow is flagged. */
    val realtimeBufferMaxEvents: Int = 1000,

    /** Max duration for catch-up before forcing LIVE (ms). 0 = no limit. */
    val maxCatchUpDurationMs: Long = 30 * 60 * 1000L,

    /** Max retries for catch-up when realtime buffer overflows repeatedly. */
    val maxCatchUpRetries: Int = 3,

    /** Backoff base for catch-up overflow retry (ms). */
    val catchUpOverflowBackoffBaseMs: Long = 1_000L,

    /** Backoff max for catch-up overflow retry (ms). */
    val catchUpOverflowBackoffMaxMs: Long = 30_000L,

    /** How long to keep synced outbox entries before pruning (ms). */
    val outboxPruneRetentionMs: Long = 30 * 60 * 1_000L,

    /** Delay before FK constraint retry on realtime events (ms). */
    val fkRetryDelayMs: Long = 500L,

    /** Max attempts for FK constraint retry on realtime events. */
    val fkRetryMaxAttempts: Int = 3,

    /**
     * Called when a push fails terminally (400/404).
     * Apps can use this to show a toast, log to analytics, etc.
     * Called on the sync engine's IO dispatcher — don't block.
     */
    val onPushFailed: ((tableName: String, recordId: String, reason: String) -> Unit)? = null,

    /**
     * Called when a full sync cycle completes (catch-up or safety sync).
     * Includes timing for observability.
     */
    val onSyncComplete: ((durationMs: Long, recordsPulled: Int, recordsPushed: Int) -> Unit)? = null,
)
