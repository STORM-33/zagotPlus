package com.zagot.zagotplus.testutil

/**
 * Test timing constants with meaningful names.
 * Replaces magic numbers in advanceTimeBy() calls.
 */
object TestTimeouts {
    /** Lockout duration in milliseconds (30 seconds) */
    const val LOCKOUT_DURATION_MS = 30_000L
    
    /** Short delay for UI state updates */
    const val UI_UPDATE_DELAY_MS = 100L
    
    /** Network timeout simulation */
    const val NETWORK_TIMEOUT_MS = 10_000L
    
    /** Debounce delay for search/filter operations */
    const val DEBOUNCE_DELAY_MS = 300L
    
    /** Extra buffer time for timing-sensitive tests */
    const val TIMING_BUFFER_MS = 100L
}
