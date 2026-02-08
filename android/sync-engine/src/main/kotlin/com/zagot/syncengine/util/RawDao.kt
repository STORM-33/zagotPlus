package com.zagot.syncengine.util

import javax.inject.Qualifier

/**
 * Qualifier for raw (unwrapped) Room DAOs.
 * Used by the sync engine's applyToRoom callbacks which must NOT create outbox entries.
 * All other injection sites get the SyncAware-wrapped version (unqualified).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RawDao
