package com.zagot.zagotplus

/**
 * Centralized configuration constants for the application.
 * Eliminates magic numbers scattered across the codebase.
 */
object Config {
    // === Pagination ===
    /** Number of items per page in paginated lists */
    const val PAGE_SIZE = 50

    // === Sync ===
    /** Interval between automatic syncs in minutes */
    const val SYNC_INTERVAL_MIN = 15

    /** Minimum interval between manual syncs in milliseconds (30 seconds) */
    const val MIN_SYNC_INTERVAL_MS = 30_000L
    
    /** Maximum retry attempts for sync worker */
    const val SYNC_WORKER_RETRY_ATTEMPTS = 3

    // === PIN Security ===
    /** Minimum PIN length (4 digits) */
    const val MIN_PIN_LENGTH = 4
    
    /** Maximum PIN length */
    const val MAX_PIN_LENGTH = 4
    
    /** PIN session timeout in hours */
    const val PIN_TIMEOUT_HOURS = 4

    /** PIN session timeout in milliseconds (4 hours) */
    const val SESSION_TIMEOUT_MS = PIN_TIMEOUT_HOURS * 60 * 60 * 1000L

    /** Maximum failed PIN attempts before initial lockout */
    const val MAX_PIN_ATTEMPTS = 5

    /** Short lockout duration after 4-5 failed attempts in seconds */
    const val LOCKOUT_DURATION_SHORT_SECONDS = 30
    
    /** Long lockout duration after 6+ failed attempts in seconds */
    const val LOCKOUT_DURATION_LONG_SECONDS = 300 // 5 minutes
    
    /** PBKDF2 iterations for PIN hashing */
    const val PBKDF2_ITERATIONS = 10_000

    // === Image Upload ===
    /** Maximum image size for upload in KB (500KB after compression) */
    const val MAX_IMAGE_SIZE_KB = 500

    /** Maximum image width for compression */
    const val MAX_IMAGE_WIDTH = 1080

    /** Maximum raw image size for upload in bytes (5MB) */
    const val MAX_RAW_IMAGE_SIZE_BYTES = 5 * 1024 * 1024
    
    // === Disk Cache ===
    /** Disk cache size as percentage of storage */
    const val DISK_CACHE_PERCENT = 0.02 // 2%
    
    /** Memory cache size as percentage of available memory */
    const val MEMORY_CACHE_PERCENT = 0.25 // 25%
}
