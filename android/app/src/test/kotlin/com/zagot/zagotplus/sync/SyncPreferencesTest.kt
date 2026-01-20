package com.zagot.zagotplus.sync

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for SyncPreferences.
 */
class SyncPreferencesTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var syncPreferences: SyncPreferences

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        every { context.getSharedPreferences("zagot_sync_prefs", Context.MODE_PRIVATE) } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.apply() } returns Unit

        syncPreferences = SyncPreferences(context)
    }

    @Test
    fun `getLastSyncTimestamp returns EPOCH when never synced`() {
        every { sharedPreferences.getLong("last_sync_timestamp", 0L) } returns 0L

        val result = syncPreferences.getLastSyncTimestamp()

        assertEquals(Instant.EPOCH, result)
    }

    @Test
    fun `getLastSyncTimestamp returns correct timestamp with 1ms buffer when synced`() {
        val testMillis = 1705320600000L // 2024-01-15T10:30:00Z
        every { sharedPreferences.getLong("last_sync_timestamp", 0L) } returns testMillis

        val result = syncPreferences.getLastSyncTimestamp()

        // Should return stored timestamp as-is (buffer removed per M1 audit fix)
        assertEquals(Instant.ofEpochMilli(testMillis), result)
    }

    @Test
    fun `setLastSyncTimestamp stores timestamp correctly`() {
        val testInstant = Instant.parse("2024-01-15T10:30:00Z")
        val capturedMillis = slot<Long>()
        every { editor.putLong("last_sync_timestamp", capture(capturedMillis)) } returns editor

        syncPreferences.setLastSyncTimestamp(testInstant)

        verify { editor.putLong("last_sync_timestamp", any()) }
        verify { editor.apply() }
        assertEquals(testInstant.toEpochMilli(), capturedMillis.captured)
    }

    @Test
    fun `clearLastSyncTimestamp removes timestamp`() {
        syncPreferences.clearLastSyncTimestamp()

        verify { editor.remove("last_sync_timestamp") }
        verify { editor.apply() }
    }

    @Test
    fun `getLastSyncTimestamp handles edge case timestamps with buffer`() {
        // Very old timestamp
        val oldMillis = 1000L
        every { sharedPreferences.getLong("last_sync_timestamp", 0L) } returns oldMillis

        val result = syncPreferences.getLastSyncTimestamp()

        // Should return stored timestamp as-is (buffer removed per M1 audit fix)
        assertEquals(Instant.ofEpochMilli(oldMillis), result)
    }

    @Test
    fun `setLastSyncTimestamp handles current time`() {
        val now = Instant.now()
        
        syncPreferences.setLastSyncTimestamp(now)

        verify { editor.putLong("last_sync_timestamp", now.toEpochMilli()) }
    }
}
