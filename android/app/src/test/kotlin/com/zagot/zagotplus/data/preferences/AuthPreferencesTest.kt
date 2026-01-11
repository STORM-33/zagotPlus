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
        authPreferences.setPin(pin)
        
        val storedHash = mutableListOf<String>()
        every { editor.putString("pin_hash", capture(storedHash)) } returns editor
        authPreferences.setPin(pin)
        
        every { sharedPreferences.getString("pin_hash", null) } returns storedHash.first()
        
        assertTrue(authPreferences.verifyPin(pin))
    }

    @Test
    fun `verifyPin returns false for incorrect PIN`() {
        val correctPin = "1234"
        authPreferences.setPin(correctPin)
        
        val storedHash = mutableListOf<String>()
        every { editor.putString("pin_hash", capture(storedHash)) } returns editor
        authPreferences.setPin(correctPin)
        
        every { sharedPreferences.getString("pin_hash", null) } returns storedHash.first()
        
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
}
