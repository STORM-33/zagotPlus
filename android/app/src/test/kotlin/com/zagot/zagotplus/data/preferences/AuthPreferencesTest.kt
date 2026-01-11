package com.zagot.zagotplus.data.preferences

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthPreferencesTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var authPreferences: AuthPreferences

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

        authPreferences = AuthPreferences(context)
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
        
        verify { editor.putString("pin_hash", match { it != pin && it.length == 64 }) }
        verify { editor.apply() }
    }

    @Test
    fun `verifyPin returns true for correct PIN`() {
        val pin = "1234"
        
        val storedHash = mutableListOf<String>()
        val storedSalt = mutableListOf<String>()
        every { editor.putString("pin_hash", capture(storedHash)) } returns editor
        every { editor.putString("pin_salt", capture(storedSalt)) } returns editor
        authPreferences.setPin(pin)
        
        every { sharedPreferences.getString("pin_hash", null) } returns storedHash.first()
        every { sharedPreferences.getString("pin_salt", null) } returns storedSalt.first()
        
        assertTrue(authPreferences.verifyPin(pin))
    }

    @Test
    fun `verifyPin returns false for incorrect PIN`() {
        val correctPin = "1234"
        
        val storedHash = mutableListOf<String>()
        val storedSalt = mutableListOf<String>()
        every { editor.putString("pin_hash", capture(storedHash)) } returns editor
        every { editor.putString("pin_salt", capture(storedSalt)) } returns editor
        authPreferences.setPin(correctPin)
        
        every { sharedPreferences.getString("pin_hash", null) } returns storedHash.first()
        every { sharedPreferences.getString("pin_salt", null) } returns storedSalt.first()
        
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
        
        verify { editor.putString("pin_salt", match { it.length == 32 }) }
    }

    @Test
    fun `same PIN produces different hashes due to salt`() {
        val pin = "1234"
        val capturedHashes = mutableListOf<String>()
        every { editor.putString("pin_hash", capture(capturedHashes)) } returns editor
        
        authPreferences.setPin(pin)
        authPreferences.setPin(pin)
        
        // Two different hashes for same PIN (different salts)
        assertTrue(capturedHashes.size >= 2)
        assertNotEquals(capturedHashes[0], capturedHashes[1])
    }

    @Test
    fun `clearPin removes both hash and salt`() {
        authPreferences.clearPin()
        
        verify { editor.remove("pin_hash") }
        verify { editor.remove("pin_salt") }
    }

    // === Lockout Persistence Tests ===

    @Test
    fun `recordFailedAttempt increments counter`() {
        every { sharedPreferences.getInt("failed_attempts", 0) } returns 0
        
        authPreferences.recordFailedAttempt()
        
        verify { editor.putInt("failed_attempts", 1) }
    }

    @Test
    fun `lockout is set after 3 failed attempts`() {
        every { sharedPreferences.getInt("failed_attempts", 0) } returns 2
        
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
}
