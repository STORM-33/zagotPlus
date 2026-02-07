/**
 * Realtime event handling.
 *
 * This package manages the realtime subscription and event buffering:
 * - [RealtimeManager] - Coordinates realtime subscription lifecycle
 * - [RealtimeBuffer] - In-memory buffer for events during CATCHING_UP state
 *
 * Events received while CATCHING_UP are buffered and drained after pull completes.
 * Events received while LIVE are applied immediately.
 */
package com.zagot.zagotplus.sync.engine.realtime
