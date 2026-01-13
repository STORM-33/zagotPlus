package com.zagot.zagotplus.sync

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for SyncStatus data class and factory methods.
 */
class SyncStatusTest {

    private val testInstant = Instant.parse("2024-01-15T10:30:00Z")

    @Test
    fun `idle factory creates IDLE state`() {
        val status = SyncStatus.idle()

        assertEquals(SyncStatus.State.IDLE, status.state)
        assertNull(status.lastSyncTime)
        assertNull(status.errorMessage)
    }

    @Test
    fun `idle factory with lastSync preserves timestamp`() {
        val status = SyncStatus.idle(testInstant)

        assertEquals(SyncStatus.State.IDLE, status.state)
        assertEquals(testInstant, status.lastSyncTime)
        assertNull(status.errorMessage)
    }

    @Test
    fun `syncing factory creates SYNCING state`() {
        val status = SyncStatus.syncing()

        assertEquals(SyncStatus.State.SYNCING, status.state)
        assertNull(status.lastSyncTime)
        assertNull(status.errorMessage)
    }

    @Test
    fun `error factory creates ERROR state with message`() {
        val status = SyncStatus.error("Network connection failed")

        assertEquals(SyncStatus.State.ERROR, status.state)
        assertNull(status.lastSyncTime)
        assertEquals("Network connection failed", status.errorMessage)
    }

    @Test
    fun `State enum contains all expected values`() {
        val states = SyncStatus.State.values()

        assertEquals(3, states.size)
        assertTrue(states.contains(SyncStatus.State.IDLE))
        assertTrue(states.contains(SyncStatus.State.SYNCING))
        assertTrue(states.contains(SyncStatus.State.ERROR))
    }

    @Test
    fun `when expression covers all states`() {
        val states = listOf(
            SyncStatus.idle(),
            SyncStatus.syncing(),
            SyncStatus.error("test")
        )

        states.forEach { status ->
            val description = when (status.state) {
                SyncStatus.State.IDLE -> "idle"
                SyncStatus.State.SYNCING -> "syncing"
                SyncStatus.State.ERROR -> "error"
            }
            assertNotNull(description)
        }
    }

    @Test
    fun `data class equality works correctly`() {
        val status1 = SyncStatus.idle(testInstant)
        val status2 = SyncStatus.idle(testInstant)
        val status3 = SyncStatus.idle()

        assertEquals(status1, status2)
        assertNotEquals(status1, status3)
    }

    @Test
    fun `copy preserves unchanged fields`() {
        val original = SyncStatus.idle(testInstant)
        val copied = original.copy(state = SyncStatus.State.ERROR)

        assertEquals(SyncStatus.State.ERROR, copied.state)
        assertEquals(testInstant, copied.lastSyncTime)
        assertNull(copied.errorMessage)
    }

    @Test
    fun `error status has null lastSyncTime`() {
        val status = SyncStatus.error("Timeout")

        assertNull(status.lastSyncTime)
    }

    @Test
    fun `error message can contain unicode`() {
        val status = SyncStatus.error("Помилка з'єднання з сервером")

        assertEquals("Помилка з'єднання з сервером", status.errorMessage)
    }
}
