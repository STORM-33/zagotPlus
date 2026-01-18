package com.zagot.zagotplus.data.preferences

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthPreferencesTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var authPreferences: AuthPreferencesImpl

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        sharedPreferences = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.apply() } returns Unit

        authPreferences = AuthPreferencesImpl(context)
    }

    @Test
    fun `isPinSet returns false when no PIN stored`() {
        every { sharedPreferences.contains("pin_hash") } returns false
        
        assertFalse(authPreferences.isPinSet())
    }

    @Test
    fun `isPinSet returns true when PIN stored`() {
        every { sharedPreferences.contains("pin_hash") } returns true
        
        assertTrue(authPreferences.isPinSet())
    }

    @Test
    fun `setPin stores hash not plain text`() {
        val pin = "1234"
        
        authPreferences.setPin(pin)
        
        // PBKDF2 produces Base64 hash, not hex
        verify { editor.putString("pin_hash", match { it != pin && it.isNotEmpty() }) }
        verify { editor.putInt("hash_version", 2) }
        verify { editor.apply() }
    }

    @Test
    fun `verifyPin returns true for correct PIN`() {
        val pin = "1234"
        
        val hashSlot = slot<String>()
        val saltSlot = slot<String>()
        val versionSlot = slot<Int>()
        every { editor.putString("pin_hash", capture(hashSlot)) } returns editor
        every { editor.putString("pin_salt", capture(saltSlot)) } returns editor
        every { editor.putInt("hash_version", capture(versionSlot)) } returns editor
        
        authPreferences.setPin(pin)
        
        every { sharedPreferences.getString("pin_hash", null) } returns hashSlot.captured
        every { sharedPreferences.getString("pin_salt", null) } returns saltSlot.captured
        every { sharedPreferences.getInt("hash_version", 1) } returns versionSlot.captured
        
        assertTrue(authPreferences.verifyPin(pin))
    }

    @Test
    fun `verifyPin returns false for incorrect PIN`() {
        val correctPin = "1234"
        
        val hashSlot = slot<String>()
        val saltSlot = slot<String>()
        val versionSlot = slot<Int>()
        every { editor.putString("pin_hash", capture(hashSlot)) } returns editor
        every { editor.putString("pin_salt", capture(saltSlot)) } returns editor
        every { editor.putInt("hash_version", capture(versionSlot)) } returns editor
        
        authPreferences.setPin(correctPin)
        
        every { sharedPreferences.getString("pin_hash", null) } returns hashSlot.captured
        every { sharedPreferences.getString("pin_salt", null) } returns saltSlot.captured
        every { sharedPreferences.getInt("hash_version", 1) } returns versionSlot.captured
        
        assertFalse(authPreferences.verifyPin("9999"))
    }

    @Test
    fun `verifyPin returns false when no PIN set`() {
        every { sharedPreferences.getString("pin_hash", null) } returns null
        
        assertFalse(authPreferences.verifyPin("1234"))
    }

    @Test
    fun `clearPin removes hash from preferences`() {
        authPreferences.clearPin()
        
        verify { editor.remove("pin_hash") }
        verify { editor.apply() }
    }

    // === Salt Tests ===

    @Test
    fun `setPin stores salt alongside hash`() {
        val pin = "1234"
        
        authPreferences.setPin(pin)
        
        // Salt is now Base64 encoded (16 bytes = ~24 chars in Base64)
        verify { editor.putString("pin_salt", match { it.isNotEmpty() }) }
    }

    @Test
    fun `same PIN produces different hashes due to salt`() {
        val pin = "1234"
        val capturedHashes = mutableListOf<String>()
        every { editor.putString("pin_hash", capture(capturedHashes)) } returns editor
        every { editor.putInt("hash_version", any()) } returns editor
        
        authPreferences.setPin(pin)
        authPreferences.setPin(pin)
        
        // Two different hashes for same PIN (different salts)
        assertTrue(capturedHashes.size >= 2)
        assertNotEquals(capturedHashes[0], capturedHashes[1])
    }

    @Test
    fun `clearPin removes hash salt and version`() {
        authPreferences.clearPin()
        
        verify { editor.remove("pin_hash") }
        verify { editor.remove("pin_salt") }
        verify { editor.remove("hash_version") }
    }

    // === Lockout Persistence Tests ===

    @Test
    fun `recordFailedAttempt increments counter`() {
        every { sharedPreferences.getInt("failed_attempts", 0) } returns 0
        
        authPreferences.recordFailedAttempt()
        
        verify { editor.putInt("failed_attempts", 1) }
    }

    @Test
    fun `lockout is set after 4 failed attempts`() {
        // Lockout starts after 4 attempts (exponential backoff: 1-3 = no lockout, 4+ = lockout)
        every { sharedPreferences.getInt("failed_attempts", 0) } returns 3
        
        authPreferences.recordFailedAttempt()
        
        verify { editor.putLong("lockout_until", match { it > System.currentTimeMillis() }) }
    }

    @Test
    fun `isLockedOut returns true when lockout_until is in future`() {
        val futureTime = System.currentTimeMillis() + 30_000
        every { sharedPreferences.getLong("lockout_until", 0) } returns futureTime
        
        assertTrue(authPreferences.isLockedOut())
    }

    @Test
    fun `isLockedOut returns false when lockout_until is in past`() {
        val pastTime = System.currentTimeMillis() - 1000
        every { sharedPreferences.getLong("lockout_until", 0) } returns pastTime
        
        assertFalse(authPreferences.isLockedOut())
    }

    @Test
    fun `clearLockout resets failed attempts and lockout`() {
        authPreferences.clearLockout()
        
        verify { editor.putInt("failed_attempts", 0) }
        verify { editor.putLong("lockout_until", 0) }
    }

    @Test
    fun `getFailedAttempts returns stored count`() {
        every { sharedPreferences.getInt("failed_attempts", 0) } returns 2
        
        assertEquals(2, authPreferences.getFailedAttempts())
    }

    @Test
    fun `getLockoutRemainingSeconds returns correct value`() {
        val futureTime = System.currentTimeMillis() + 15_000
        every { sharedPreferences.getLong("lockout_until", 0) } returns futureTime
        
        val remaining = authPreferences.getLockoutRemainingSeconds()
        assertTrue(remaining in 14..15)
    }

    // === Admin PIN Tests ===

    @Test
    fun `isAdminPinSet returns false when no admin PIN stored`() {
        every { sharedPreferences.contains("admin_pin_hash") } returns false
        
        assertFalse(authPreferences.isAdminPinSet())
    }

    @Test
    fun `isAdminPinSet returns true when admin PIN stored`() {
        every { sharedPreferences.contains("admin_pin_hash") } returns true
        
        assertTrue(authPreferences.isAdminPinSet())
    }

    @Test
    fun `setAdminPin stores hash not plain text`() {
        val pin = "12345"
        
        authPreferences.setAdminPin(pin)
        
        verify { editor.putString("admin_pin_hash", match { it != pin && it.isNotEmpty() }) }
        verify { editor.apply() }
    }

    @Test
    fun `verifyAdminPin returns true for correct PIN`() {
        val pin = "12345"
        
        val hashSlot = slot<String>()
        val saltSlot = slot<String>()
        every { editor.putString("admin_pin_hash", capture(hashSlot)) } returns editor
        every { editor.putString("admin_pin_salt", capture(saltSlot)) } returns editor
        
        authPreferences.setAdminPin(pin)
        
        every { sharedPreferences.getString("admin_pin_hash", null) } returns hashSlot.captured
        every { sharedPreferences.getString("admin_pin_salt", null) } returns saltSlot.captured
        
        assertTrue(authPreferences.verifyAdminPin(pin))
    }

    @Test
    fun `verifyAdminPin returns false for incorrect PIN`() {
        val correctPin = "12345"
        
        val hashSlot = slot<String>()
        val saltSlot = slot<String>()
        every { editor.putString("admin_pin_hash", capture(hashSlot)) } returns editor
        every { editor.putString("admin_pin_salt", capture(saltSlot)) } returns editor
        
        authPreferences.setAdminPin(correctPin)
        
        every { sharedPreferences.getString("admin_pin_hash", null) } returns hashSlot.captured
        every { sharedPreferences.getString("admin_pin_salt", null) } returns saltSlot.captured
        
        assertFalse(authPreferences.verifyAdminPin("99999"))
    }

    // === Restricted Mode Tests ===

    @Test
    fun `isRestrictedMode returns false by default`() {
        every { sharedPreferences.getBoolean("restricted_mode", false) } returns false
        
        assertFalse(authPreferences.isRestrictedMode())
    }

    @Test
    fun `isRestrictedMode returns true when set`() {
        every { sharedPreferences.getBoolean("restricted_mode", false) } returns true
        
        assertTrue(authPreferences.isRestrictedMode())
    }

    @Test
    fun `setRestrictedMode stores the value`() {
        every { editor.putBoolean("restricted_mode", true) } returns editor
        
        authPreferences.setRestrictedMode(true)
        
        verify { editor.putBoolean("restricted_mode", true) }
        verify { editor.apply() }
    }

    // === Restricted Mode StateFlow Tests ===

    @Test
    fun `restrictedModeFlow initial value matches stored preference`() {
        every { sharedPreferences.getBoolean("restricted_mode", false) } returns true
        
        val newAuthPrefs = AuthPreferencesImpl(context)
        
        assertTrue(newAuthPrefs.restrictedModeFlow.value)
    }

    @Test
    fun `setRestrictedMode updates restrictedModeFlow value`() {
        every { sharedPreferences.getBoolean("restricted_mode", false) } returns false
        every { editor.putBoolean("restricted_mode", true) } returns editor
        
        val newAuthPrefs = AuthPreferencesImpl(context)
        assertFalse(newAuthPrefs.restrictedModeFlow.value)
        
        newAuthPrefs.setRestrictedMode(true)
        
        assertTrue(newAuthPrefs.restrictedModeFlow.value)
    }

    @Test
    fun `setRestrictedMode to false updates restrictedModeFlow value`() {
        every { sharedPreferences.getBoolean("restricted_mode", false) } returns true
        every { editor.putBoolean("restricted_mode", false) } returns editor
        
        val newAuthPrefs = AuthPreferencesImpl(context)
        assertTrue(newAuthPrefs.restrictedModeFlow.value)
        
        newAuthPrefs.setRestrictedMode(false)
        
        assertFalse(newAuthPrefs.restrictedModeFlow.value)
    }
}
