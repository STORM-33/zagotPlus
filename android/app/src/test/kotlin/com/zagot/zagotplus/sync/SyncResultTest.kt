package com.zagot.zagotplus.sync

import org.junit.Assert.*
import org.junit.Test

class SyncResultTest {

    @Test
    fun `Success has correct pushedCount`() {
        val result = SyncResult.Success(pushed = 5, pulled = 3)

        assertEquals(5, result.pushedCount)
    }

    @Test
    fun `Success has isAtLeastPartial true`() {
        val result = SyncResult.Success(pushed = 5, pulled = 3)

        assertTrue(result.isAtLeastPartial)
    }

    @Test
    fun `Success with zero pushed still has isAtLeastPartial true`() {
        val result = SyncResult.Success(pushed = 0, pulled = 3)

        assertTrue(result.isAtLeastPartial)
    }

    @Test
    fun `Partial has correct pushedCount`() {
        val result = SyncResult.Partial(pushed = 5, pullError = "Network error")

        assertEquals(5, result.pushedCount)
    }

    @Test
    fun `Partial has isAtLeastPartial true`() {
        val result = SyncResult.Partial(pushed = 5, pullError = "Network error")

        assertTrue(result.isAtLeastPartial)
    }

    @Test
    fun `Partial preserves error message`() {
        val result = SyncResult.Partial(pushed = 5, pullError = "Connection timeout")

        assertEquals("Connection timeout", result.pullError)
    }

    @Test
    fun `Failure has pushedCount zero`() {
        val result = SyncResult.Failure(error = "Push failed", phase = SyncPhase.PUSH)

        assertEquals(0, result.pushedCount)
    }

    @Test
    fun `Failure has isAtLeastPartial false`() {
        val result = SyncResult.Failure(error = "Push failed", phase = SyncPhase.PUSH)

        assertFalse(result.isAtLeastPartial)
    }

    @Test
    fun `Failure preserves error and phase`() {
        val result = SyncResult.Failure(error = "Network unavailable", phase = SyncPhase.PUSH)

        assertEquals("Network unavailable", result.error)
        assertEquals(SyncPhase.PUSH, result.phase)
    }

    @Test
    fun `Failure defaults to UNKNOWN phase`() {
        val result = SyncResult.Failure(error = "Unknown error")

        assertEquals(SyncPhase.UNKNOWN, result.phase)
    }

    @Test
    fun `when expression covers all SyncResult types`() {
        val results = listOf(
            SyncResult.Success(1, 1),
            SyncResult.Partial(1, "error"),
            SyncResult.Failure("error")
        )

        results.forEach { result ->
            val description = when (result) {
                is SyncResult.Success -> "success"
                is SyncResult.Partial -> "partial"
                is SyncResult.Failure -> "failure"
            }
            assertNotNull(description)
        }
    }
}
