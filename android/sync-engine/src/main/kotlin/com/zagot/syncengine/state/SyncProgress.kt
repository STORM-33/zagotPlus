package com.zagot.syncengine.state

/**
 * Reports progress of a sync operation (catch-up or safety sync).
 * Emitted per batch to avoid per-record overhead.
 */
data class SyncProgress(
    val phase: SyncPhase,
    val currentTable: String?,
    val tablesCompleted: Int,
    val tablesTotal: Int,
    val recordsProcessed: Int,
    /** Null if unknown (streaming pull doesn't know total upfront). */
    val totalRecords: Int? = null,
)

/**
 * Phase of the sync cycle for progress reporting.
 */
enum class SyncPhase {
    PULLING,
    PUSHING,
    DRAINING_BUFFER,
    IDLE,
}
