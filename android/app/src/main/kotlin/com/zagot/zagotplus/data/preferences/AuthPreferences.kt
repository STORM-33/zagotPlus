package com.zagot.zagotplus.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
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
    fun isSessionValid(): Boolean
    fun getSessionRemainingMinutes(): Int
}

/**
 * Manages authentication preferences using SharedPreferences.
 * Stores PIN hash using PBKDF2 for access control with lockout protection.
 *
 * Security notes:
 * - PIN is hashed with PBKDF2-HMAC-SHA256 (10,000 iterations)
 * - 16-byte random salt per PIN
 * - 30-second lockout after 3 failed attempts
 * - Session expires after 4 hours or on app process death
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
    
    // Session start time for timeout tracking
    @Volatile
    private var sessionStartTime: Long = 0

    override fun isPinSet(): Boolean {
        return prefs.contains(KEY_PIN_HASH)
    }

    override fun setPin(pin: String) {
        val salt = generateSalt()
        val hash = hashPinWithPbkdf2(pin, salt)
        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.getEncoder().encodeToString(salt))
            .putString(KEY_PIN_HASH, hash)
            .putInt(KEY_HASH_VERSION, CURRENT_HASH_VERSION)
            .apply()
        clearLockout()
    }

    override fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val storedSaltBase64 = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val hashVersion = prefs.getInt(KEY_HASH_VERSION, 1)
        
        return if (hashVersion >= 2) {
            // PBKDF2 hash (version 2+)
            val salt = Base64.getDecoder().decode(storedSaltBase64)
            val inputHash = hashPinWithPbkdf2(pin, salt)
            storedHash == inputHash
        } else {
            // Legacy SHA-256 hash (version 1) - migrate on successful verify
            val inputHash = hashPinWithSha256Legacy(pin, storedSaltBase64)
            if (storedHash == inputHash) {
                // Migrate to PBKDF2
                setPin(pin)
                true
            } else {
                false
            }
        }
    }

    override fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .remove(KEY_HASH_VERSION)
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

    private fun generateSalt(): ByteArray {
        val bytes = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(bytes)
        return bytes
    }

    /**
     * Hash PIN using PBKDF2-HMAC-SHA256.
     * More secure than simple SHA-256 for password/PIN storage.
     */
    private fun hashPinWithPbkdf2(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val hash = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.getEncoder().encodeToString(hash)
    }

    /**
     * Legacy SHA-256 hash for migration purposes.
     */
    private fun hashPinWithSha256Legacy(pin: String, salt: String): String {
        val combined = salt + pin
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(combined.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // === Session Management ===

    override fun setAuthenticated(authenticated: Boolean) {
        sessionAuthenticated = authenticated
        if (authenticated) {
            sessionStartTime = System.currentTimeMillis()
        } else {
            sessionStartTime = 0
        }
    }

    override fun isAuthenticated(): Boolean {
        return sessionAuthenticated && isSessionValid()
    }

    override fun isSessionValid(): Boolean {
        if (!sessionAuthenticated) return false
        return System.currentTimeMillis() - sessionStartTime < SESSION_TIMEOUT_MS
    }

    override fun getSessionRemainingMinutes(): Int {
        if (!sessionAuthenticated || sessionStartTime == 0L) return 0
        val elapsed = System.currentTimeMillis() - sessionStartTime
        val remaining = SESSION_TIMEOUT_MS - elapsed
        return if (remaining > 0) (remaining / 60_000).toInt() else 0
    }

    companion object {
        private const val PREFS_NAME = "zagot_auth_prefs"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_HASH_VERSION = "hash_version"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"

        private const val MAX_ATTEMPTS = 3
        /** Lockout duration in milliseconds (30 seconds) */
        private const val LOCKOUT_DURATION_MS = 30_000L
        
        /** Session timeout in milliseconds (4 hours) */
        private const val SESSION_TIMEOUT_MS = 4 * 60 * 60 * 1000L
        
        // PBKDF2 parameters
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 10_000
        private const val HASH_LENGTH_BITS = 256
        private const val SALT_LENGTH = 16
        private const val CURRENT_HASH_VERSION = 2
    }
}
