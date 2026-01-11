package com.zagot.zagotplus.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages authentication preferences using SharedPreferences.
 * Stores salted PIN hash for access control with lockout protection.
 */
@Singleton
class AuthPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Check if PIN has been set.
     */
    fun isPinSet(): Boolean {
        return prefs.contains(KEY_PIN_HASH)
    }

    /**
     * Set new PIN (stores salted hash, not plain text).
     * Clears any existing lockout.
     */
    fun setPin(pin: String) {
        val salt = generateSalt()
        val hash = hashPinWithSalt(pin, salt)
        prefs.edit()
            .putString(KEY_PIN_SALT, salt)
            .putString(KEY_PIN_HASH, hash)
            .apply()
        clearLockout()
    }

    /**
     * Verify PIN against stored hash.
     */
    fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val storedSalt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val inputHash = hashPinWithSalt(pin, storedSalt)
        return storedHash == inputHash
    }

    /**
     * Clear PIN (for testing or reset).
     */
    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .apply()
    }

    // === Lockout Management ===

    /**
     * Record a failed PIN attempt. Sets lockout after MAX_ATTEMPTS.
     */
    fun recordFailedAttempt() {
        val currentAttempts = getFailedAttempts()
        val newAttempts = currentAttempts + 1
        
        val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, newAttempts)
        
        if (newAttempts >= MAX_ATTEMPTS) {
            val lockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS
            editor.putLong(KEY_LOCKOUT_UNTIL, lockoutUntil)
        }
        
        editor.apply()
    }

    /**
     * Check if currently locked out.
     */
    fun isLockedOut(): Boolean {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0)
        return lockoutUntil > System.currentTimeMillis()
    }

    /**
     * Get remaining lockout time in seconds.
     */
    fun getLockoutRemainingSeconds(): Int {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0)
        val remaining = lockoutUntil - System.currentTimeMillis()
        return if (remaining > 0) (remaining / 1000).toInt() else 0
    }

    /**
     * Get current failed attempt count.
     */
    fun getFailedAttempts(): Int {
        return prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
    }

    /**
     * Clear lockout state (after successful login or manual reset).
     */
    fun clearLockout() {
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
