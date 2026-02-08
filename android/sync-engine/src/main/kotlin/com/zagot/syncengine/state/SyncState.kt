package com.zagot.syncengine.state

/**
 * Sync engine states per the realtime-sync-spec.
 *
 * OFFLINE → CATCHING_UP → LIVE is the primary forward path.
 * Any state can transition to OFFLINE on connectivity loss.
 */
enum class SyncState {
    /** No network. Room is source of truth. Outbox captures all local writes. */
    OFFLINE,

    /** Connectivity restored. Running pull-before-push catch-up cycle. */
    CATCHING_UP,

    /** Realtime connected. Events applied immediately, pushes sent immediately. */
    LIVE
}
