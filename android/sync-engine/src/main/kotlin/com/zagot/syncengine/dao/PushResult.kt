package com.zagot.syncengine.dao

/**
 * Result of a [PushCoordinator.pushPending] call.
 * Contains both success count and details of any terminal failures.
 */
data class PushResult(
    val successCount: Int,
    val failedEntries: List<PushFailure>,
)

/**
 * A single push failure (terminal error like 400/404).
 */
data class PushFailure(
    val tableName: String,
    val recordId: String,
    val reason: String,
)
