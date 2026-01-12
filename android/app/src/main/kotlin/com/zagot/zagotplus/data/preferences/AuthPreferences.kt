package com.zagot.zagotplus.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for authentication preferences.
 * Extracted for testability.
 */
interface AuthPreferences {
    fun isPinSet(): Boolean
    fun setPin(pin: String)
    fun verifyPin(pin: String): Boolean
    fun clearPin()
    fun recordFailedAttempt()
    fun isLockedOut(): Boolean
    fun getLockoutRemainingSeconds(): Int
    fun getFailedAttempts(): Int
    fun clearLockout()
    fun setAuthenticated(authenticated: Boolean)
    fun isAuthenticated(): Boolean
}

/**
 * Manages authentication preferences using SharedPreferences.
 * Stores salted PIN hash for access control with lockout protection.
 *
 * Security notes:
 * - PIN is hashed with salted SHA-256 (not PBKDF2, but acceptable for 4-digit PIN
 *   with 30-second lockout after 3 attempts)
 * - Session expires on app process death (in-memory flag)
 */
@Singleton
class AuthPreferencesImpl @Inject constructor(
    @ApplicationContext context: Context
) : AuthPreferences {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    // In-memory session flag - expires on process death
    @Volatile
    private var sessionAuthenticated: Boolean = false

    override fun isPinSet(): Boolean {
        return prefs.contains(KEY_PIN_HASH)
    }

    override fun setPin(pin: String) {
        val salt = generateSalt()
        val hash = hashPinWithSalt(pin, salt)
        prefs.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hash)
            .apply()
        clearLockout()
    }

    override fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val storedSalt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val inputHash = hashPinWithSalt(pin, storedSalt)
        return storedHash == inputHash
    }

    override fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .apply()
    }

    override fun recordFailedAttempt() {
        val currentAttempts = getFailedAttempts()
        val newAttempts = currentAttempts + 1
        
        val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, newAttempts)
        
        if (newAttempts >= MAX_ATTEMPTS) {
            val lockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS
            editor.putLong(KEY_LOCKOUT_UNTIL, lockoutUntil)
        }
        
        editor.apply()
    }

    override fun isLockedOut(): Boolean {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0)
        return lockoutUntil > System.currentTimeMillis()
    }

    override fun getLockoutRemainingSeconds(): Int {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0)
        val remaining = lockoutUntil - System.currentTimeMillis()
        return if (remaining > 0) (remaining / 1000).toInt() else 0
    }

    override fun getFailedAttempts(): Int {
        return prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
    }

    override fun clearLockout() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0)
            .apply()
    }

    // === Private Helpers ===

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPinWithSalt(pin: String, salt: String): String {
        val combined = salt + pin
        val bytes = MessageDigest.getInstance("SHA-256").digest(combined.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // === Session Management ===

    override fun setAuthenticated(authenticated: Boolean) {
        sessionAuthenticated = authenticated
    }

    override fun isAuthenticated(): Boolean {
        return sessionAuthenticated
    }

    companion object {
        private const val PREFS_NAME = "zagot_auth_prefs"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"

        private const val MAX_ATTEMPTS = 3
        private const val LOCKOUT_DURATION_MS = 30_000L
    }
}
