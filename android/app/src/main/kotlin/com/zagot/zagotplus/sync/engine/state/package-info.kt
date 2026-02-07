/**
 * Sync engine state management.
 *
 * This package handles the state machine and event system for the sync engine:
 * - [SyncState] - Enum representing OFFLINE, CATCHING_UP, and LIVE states
 * - [SyncEvent] - Sealed interface for all events that drive state transitions
 * - [SyncStateMachine] - Core state machine managing transitions between states
 *
 * State transitions:
 * - OFFLINE → CATCHING_UP (connectivity restored)
 * - CATCHING_UP → LIVE (catch-up completed)
 * - Any state → OFFLINE (connectivity lost)
 */
package com.zagot.zagotplus.sync.engine.state
