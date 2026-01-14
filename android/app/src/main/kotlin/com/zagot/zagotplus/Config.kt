package com.zagot.zagotplus

/**
 * Centralized configuration constants for the application.
 * Eliminates magic numbers scattered across the codebase.
 */
object Config {
    /** Number of items per page in paginated lists */
    const val PAGE_SIZE = 50

    /** Interval between automatic syncs in minutes */
    const val SYNC_INTERVAL_MIN = 15

    /** Minimum interval between manual syncs in milliseconds (30 seconds) */
    const val MIN_SYNC_INTERVAL_MS = 30_000L

    /** PIN session timeout in hours */
    const val PIN_TIMEOUT_HOURS = 4

    /** PIN session timeout in milliseconds (4 hours) */
    const val SESSION_TIMEOUT_MS = PIN_TIMEOUT_HOURS * 60 * 60 * 1000L

    /** Maximum failed PIN attempts before lockout */
    const val MAX_PIN_ATTEMPTS = 3

    /** Lockout duration after max failed attempts in seconds */
    const val LOCKOUT_DURATION_SECONDS = 30

    /** Maximum image size for upload in bytes (500KB after compression) */
    const val MAX_IMAGE_SIZE_KB = 500

    /** Maximum image width for compression */
    const val MAX_IMAGE_WIDTH = 1080

    /** Maximum raw image size for upload in bytes (5MB) */
    const val MAX_RAW_IMAGE_SIZE_BYTES = 5 * 1024 * 1024
}
