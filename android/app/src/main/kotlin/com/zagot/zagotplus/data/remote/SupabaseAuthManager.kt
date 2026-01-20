package com.zagot.zagotplus.data.remote

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Manages Supabase authentication.
 * 
 * NOTE: For now, authentication is bypassed since the RLS migration hasn't been applied.
 * Once the migration is applied, this will need to be updated to use proper GoTrue auth.
 * 
 * The device ID is tracked for audit purposes even without auth.
 */
@Singleton
class SupabaseAuthManager @Inject constructor() {
    companion object {
        private const val TAG = "SupabaseAuthManager"
    }

    private val _isAuthenticated = MutableStateFlow(true)

    /**
     * Whether the user is currently authenticated.
     * Always true for now (auth bypassed until RLS migration is applied).
     */
    val isAuthenticated: Flow<Boolean> = _isAuthenticated

    /**
     * Ensures the user is authenticated.
     * Call this before any sync operations.
     * 
     * Currently returns true always since auth is bypassed.
     * When RLS migration is applied, this will need proper GoTrue implementation.
     * 
     * @param deviceId The device ID to store in user metadata for audit
     * @return true if sign-in succeeded or already signed in
     */
    suspend fun ensureAuthenticated(deviceId: String): Boolean {
        // TODO: Implement proper GoTrue auth when RLS migration is applied
        // For now, we're using the anon key which still works with current RLS policies
        Log.d(TAG, "Auth bypassed (using anon key), device: $deviceId")
        return true
    }

    /**
     * Signs out the current user.
     */
    suspend fun signOut() {
        Log.d(TAG, "Sign out called (no-op, auth bypassed)")
    }

    /**
     * Gets the current session's access token, or null if not authenticated.
     */
    fun getAccessToken(): String? = null

    /**
     * Gets the current user's ID, or null if not authenticated.
     */
    fun getUserId(): String? = null
}
