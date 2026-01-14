package com.zagot.zagotplus.data.preferences

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for DevicePreferences.
 */
class DevicePreferencesTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var devicePreferences: DevicePreferences

    private val testLocationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.apply() } returns Unit
        every { sharedPreferences.getString("selected_location", null) } returns null

        // Use the test factory to bypass encrypted prefs
        devicePreferences = DevicePreferences.createForTest(context, sharedPreferences)
    }

    // Device ID Tests

    @Test
    fun `getDeviceId returns existing ID when stored`() {
        val existingId = "existing-device-id-123"
        every { sharedPreferences.getString("device_id", null) } returns existingId

        val result = devicePreferences.getDeviceId()

        assertEquals(existingId, result)
    }

    @Test
    fun `getDeviceId generates and stores new ID when not stored`() {
        every { sharedPreferences.getString("device_id", null) } returns null
        val capturedId = slot<String>()
        every { editor.putString("device_id", capture(capturedId)) } returns editor

        val result = devicePreferences.getDeviceId()

        verify { editor.putString("device_id", any()) }
        verify { editor.apply() }
        assertEquals(result, capturedId.captured)
        // New format: {androidId}_{random8chars} - should be at least 10 chars with underscore
        assertTrue(result.contains("_"))
        assertTrue(result.length >= 10)
    }

    @Test
    fun `getDeviceId returns same value on subsequent calls`() {
        val generatedId = UUID.randomUUID().toString()
        every { sharedPreferences.getString("device_id", null) } returns null andThen generatedId
        every { editor.putString("device_id", any()) } returns editor

        val firstCall = devicePreferences.getDeviceId()
        every { sharedPreferences.getString("device_id", null) } returns firstCall
        val secondCall = devicePreferences.getDeviceId()

        assertEquals(firstCall, secondCall)
    }

    // Selected Location Tests

    @Test
    fun `getSelectedLocationId returns null when not set`() {
        every { sharedPreferences.getString("selected_location", null) } returns null

        devicePreferences = DevicePreferences.createForTest(context, sharedPreferences)
        val result = devicePreferences.getSelectedLocationId()

        assertNull(result)
    }

    @Test
    fun `getSelectedLocationId returns UUID when stored`() {
        every { sharedPreferences.getString("selected_location", null) } returns testLocationId.toString()

        devicePreferences = DevicePreferences.createForTest(context, sharedPreferences)
        val result = devicePreferences.getSelectedLocationId()

        assertEquals(testLocationId, result)
    }

    @Test
    fun `setSelectedLocationId stores location correctly`() {
        val capturedLocation = slot<String>()
        every { editor.putString("selected_location", capture(capturedLocation)) } returns editor

        devicePreferences.setSelectedLocationId(testLocationId)

        verify { editor.putString("selected_location", testLocationId.toString()) }
        verify { editor.apply() }
    }

    @Test
    fun `selectedLocationIdFlow emits initial value`() = runTest {
        every { sharedPreferences.getString("selected_location", null) } returns testLocationId.toString()

        devicePreferences = DevicePreferences.createForTest(context, sharedPreferences)
        val result = devicePreferences.selectedLocationIdFlow.first()

        assertEquals(testLocationId, result)
    }

    @Test
    fun `selectedLocationIdFlow emits null when not set`() = runTest {
        every { sharedPreferences.getString("selected_location", null) } returns null

        devicePreferences = DevicePreferences.createForTest(context, sharedPreferences)
        val result = devicePreferences.selectedLocationIdFlow.first()

        assertNull(result)
    }

    @Test
    fun `setSelectedLocationId updates flow`() = runTest {
        every { sharedPreferences.getString("selected_location", null) } returns null

        devicePreferences = DevicePreferences.createForTest(context, sharedPreferences)
        devicePreferences.setSelectedLocationId(testLocationId)
        val result = devicePreferences.selectedLocationIdFlow.first()

        assertEquals(testLocationId, result)
    }

    private fun assertDoesNotThrow(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            fail("Expected no exception but got: ${e.message}")
        }
    }
}
