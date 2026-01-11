package com.zagot.zagotplus.sync

/**
 * Result of a sync operation, containing success status and statistics.
 */
data class SyncResult(
    val success: Boolean,
    val pushedCount: Int = 0,
    val pulledCount: Int = 0,
    val error: String? = null
) {
    companion object {
        fun success(pushed: Int, pulled: Int) = SyncResult(
            success = true,
            pushedCount = pushed,
            pulledCount = pulled
        )

        fun failure(error: String) = SyncResult(
            success = false,
            error = error
        )
    }
}
