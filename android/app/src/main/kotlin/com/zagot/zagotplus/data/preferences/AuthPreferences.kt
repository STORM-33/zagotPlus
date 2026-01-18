package com.zagot.zagotplus.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.zagot.zagotplus.Config
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    
    // Admin password (5-digit)
    fun isAdminPinSet(): Boolean
    fun setAdminPin(pin: String)
    fun verifyAdminPin(pin: String): Boolean
    
    // Restricted mode
    fun isRestrictedMode(): Boolean
    fun setRestrictedMode(restricted: Boolean)
    
    // Reactive state for restricted mode
    val restrictedModeFlow: StateFlow<Boolean>
}

/**
 * Manages authentication preferences using SharedPreferences.
 * Stores PIN hash using PBKDF2 for access control with lockout protection.
 *
 * Security notes:
 * - PIN must be 4 digits
 * - PIN is hashed with PBKDF2-HMAC-SHA256 (10,000 iterations)
 * - 16-byte random salt per PIN
 * - Exponential lockout: 30s after 4 attempts, 5min after 6+ attempts
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
    
    // Reactive state for restricted mode
    private val _restrictedModeFlow = MutableStateFlow(prefs.getBoolean(KEY_RESTRICTED_MODE, false))
    override val restrictedModeFlow: StateFlow<Boolean> = _restrictedModeFlow.asStateFlow()

    override fun isPinSet(): Boolean {
        return prefs.contains(KEY_PIN_HASH)
    }

    override fun setPin(pin: String) {
        require(pin.length in Config.MIN_PIN_LENGTH..Config.MAX_PIN_LENGTH) {
            "PIN must be ${Config.MIN_PIN_LENGTH}-${Config.MAX_PIN_LENGTH} digits"
        }
        require(pin.all { it.isDigit() }) { "PIN must contain only digits" }
        
        val salt = generateSalt()
        val hash = hashPinWithPbkdf2(pin, salt)
        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.getEncoder().encodeToString(salt))
            .putString(KEY_PIN_HASH, hash)
            .putInt(KEY_HASH_VERSION, CURRENT_HASH_VERSION)
            .putInt(KEY_PIN_LENGTH, pin.length)
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
            .remove(KEY_PIN_LENGTH)
            .apply()
    }

    override fun recordFailedAttempt() {
        val currentAttempts = getFailedAttempts()
        val newAttempts = currentAttempts + 1
        
        val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, newAttempts)
        
        // Exponential backoff lockout
        val lockoutDuration = when (newAttempts) {
            in 1..3 -> 0L // No lockout for first 3 attempts
            in 4..5 -> LOCKOUT_DURATION_SHORT_MS // 30 seconds
            else -> LOCKOUT_DURATION_LONG_MS // 5 minutes after 6+ attempts
        }
        
        if (lockoutDuration > 0) {
            val lockoutUntil = System.currentTimeMillis() + lockoutDuration
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
    
    /**
     * Get the expected PIN length (for UI hints).
     */
    fun getExpectedPinLength(): Int {
        return prefs.getInt(KEY_PIN_LENGTH, Config.MIN_PIN_LENGTH)
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
        val spec = PBEKeySpec(pin.toCharArray(), salt, Config.PBKDF2_ITERATIONS, HASH_LENGTH_BITS)
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
        return System.currentTimeMillis() - sessionStartTime < Config.SESSION_TIMEOUT_MS
    }

    override fun getSessionRemainingMinutes(): Int {
        if (!sessionAuthenticated || sessionStartTime == 0L) return 0
        val elapsed = System.currentTimeMillis() - sessionStartTime
        val remaining = Config.SESSION_TIMEOUT_MS - elapsed
        return if (remaining > 0) (remaining / 60_000).toInt() else 0
    }

    // === Admin PIN Management ===
    
    override fun isAdminPinSet(): Boolean {
        return prefs.contains(KEY_ADMIN_PIN_HASH)
    }

    override fun setAdminPin(pin: String) {
        require(pin.length == Config.ADMIN_PIN_LENGTH) {
            "Admin PIN must be ${Config.ADMIN_PIN_LENGTH} digits"
        }
        require(pin.all { it.isDigit() }) { "Admin PIN must contain only digits" }
        
        val salt = generateSalt()
        val hash = hashPinWithPbkdf2(pin, salt)
        prefs.edit()
            .putString(KEY_ADMIN_PIN_SALT, Base64.getEncoder().encodeToString(salt))
            .putString(KEY_ADMIN_PIN_HASH, hash)
            .apply()
    }

    override fun verifyAdminPin(pin: String): Boolean {
        val storedHash = prefs.getString(KEY_ADMIN_PIN_HASH, null) ?: return false
        val storedSaltBase64 = prefs.getString(KEY_ADMIN_PIN_SALT, null) ?: return false
        
        val salt = Base64.getDecoder().decode(storedSaltBase64)
        val inputHash = hashPinWithPbkdf2(pin, salt)
        return storedHash == inputHash
    }

    // === Restricted Mode Management ===
    
    override fun isRestrictedMode(): Boolean {
        return prefs.getBoolean(KEY_RESTRICTED_MODE, false)
    }

    override fun setRestrictedMode(restricted: Boolean) {
        prefs.edit().putBoolean(KEY_RESTRICTED_MODE, restricted).apply()
        _restrictedModeFlow.value = restricted
    }

    companion object {
        private const val PREFS_NAME = "zagot_auth_prefs"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_HASH_VERSION = "hash_version"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
        private const val KEY_PIN_LENGTH = "pin_length"
        private const val KEY_ADMIN_PIN_HASH = "admin_pin_hash"
        private const val KEY_ADMIN_PIN_SALT = "admin_pin_salt"
        private const val KEY_RESTRICTED_MODE = "restricted_mode"

        // PIN length constants - reference Config for values, expose for external use
        /** Minimum PIN length - delegates to Config */
        val MIN_PIN_LENGTH get() = Config.MIN_PIN_LENGTH
        /** Maximum PIN length - delegates to Config */
        val MAX_PIN_LENGTH get() = Config.MAX_PIN_LENGTH
        
        /** Maximum attempts before initial lockout */
        private const val MAX_ATTEMPTS = 5
        /** Short lockout duration in milliseconds (30 seconds) */
        private const val LOCKOUT_DURATION_SHORT_MS = 30_000L
        /** Long lockout duration in milliseconds (5 minutes) */
        private const val LOCKOUT_DURATION_LONG_MS = 5 * 60_000L
        
        // PBKDF2 parameters
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val HASH_LENGTH_BITS = 256
        private const val SALT_LENGTH = 16
        private const val CURRENT_HASH_VERSION = 2
    }
}
