package com.zagot.zagotplus.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.data.preferences.AuthPreferences
import com.zagot.zagotplus.data.preferences.AuthPreferencesImpl
import com.zagot.zagotplus.debug.CrashLogger
import com.zagot.zagotplus.Config
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "PinViewModel"

@HiltViewModel
class PinViewModel @Inject constructor(
    private val authPreferences: AuthPreferences,
    private val crashLogger: CrashLogger
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinUiState())
    val uiState: StateFlow<PinUiState> = _uiState.asStateFlow()

    init {
        val isSettingPin = !authPreferences.isPinSet()
        val needsAdminPin = authPreferences.isPinSet() && !authPreferences.isAdminPinSet()
        val isLockedOut = authPreferences.isLockedOut()
        val failedAttempts = authPreferences.getFailedAttempts()
        
        val initialMode = when {
            isSettingPin -> PinMode.SET_PIN
            needsAdminPin -> PinMode.VERIFY_PIN  // First verify, then set admin PIN
            else -> PinMode.VERIFY_PIN
        }
        
        _uiState.value = _uiState.value.copy(
            mode = initialMode,
            isLockedOut = isLockedOut,
            failedAttempts = failedAttempts,
            minPinLength = AuthPreferencesImpl.MIN_PIN_LENGTH,
            maxPinLength = AuthPreferencesImpl.MAX_PIN_LENGTH,
            expectedPinLength = Config.MIN_PIN_LENGTH
        )
        
        if (isLockedOut) {
            startLockoutCountdown()
        }
    }

    fun onDigitPressed(digit: Int) {
        val currentState = _uiState.value
        val maxLen = currentState.expectedPinLength
        if (currentState.currentPin.length >= maxLen || currentState.isLockedOut) return

        val newPin = currentState.currentPin + digit.toString()
        _uiState.value = currentState.copy(
            currentPin = newPin,
            errorMessage = null
        )

        // Auto-submit when PIN reaches expected length
        if (newPin.length == maxLen) {
            when (currentState.mode) {
                PinMode.SET_PIN -> handleSetPinComplete(newPin)
                PinMode.CONFIRM_PIN -> handleConfirmPinComplete(newPin)
                PinMode.VERIFY_PIN -> handleVerifyPinComplete(newPin)
                PinMode.SET_ADMIN_PIN -> handleSetAdminPinComplete(newPin)
                PinMode.CONFIRM_ADMIN_PIN -> handleConfirmAdminPinComplete(newPin)
                PinMode.VERIFY_ADMIN_PIN -> handleVerifyAdminPinComplete(newPin)
            }
        }
    }

    fun onBackspacePressed() {
        val currentPin = _uiState.value.currentPin
        if (currentPin.isEmpty() || _uiState.value.isLockedOut) return

        _uiState.value = _uiState.value.copy(
            currentPin = currentPin.dropLast(1),
            errorMessage = null
        )
    }

    /**
     * Called when user presses the confirm/submit button.
     * Required for variable-length PINs.
     */
    fun onConfirmPressed() {
        val currentState = _uiState.value
        val pin = currentState.currentPin
        val expectedLen = currentState.expectedPinLength
        
        if (pin.length < expectedLen) {
            _uiState.value = currentState.copy(
                errorMessage = "PIN має бути $expectedLen цифр"
            )
            return
        }
        
        when (currentState.mode) {
            PinMode.SET_PIN -> handleSetPinComplete(pin)
            PinMode.CONFIRM_PIN -> handleConfirmPinComplete(pin)
            PinMode.VERIFY_PIN -> handleVerifyPinComplete(pin)
            PinMode.SET_ADMIN_PIN -> handleSetAdminPinComplete(pin)
            PinMode.CONFIRM_ADMIN_PIN -> handleConfirmAdminPinComplete(pin)
            PinMode.VERIFY_ADMIN_PIN -> handleVerifyAdminPinComplete(pin)
        }
    }

    private fun handleSetPinComplete(pin: String) {
        _uiState.value = _uiState.value.copy(
            mode = PinMode.CONFIRM_PIN,
            currentPin = "",
            tempPin = pin
        )
    }

    private fun handleConfirmPinComplete(pin: String) {
        val tempPin = _uiState.value.tempPin
        if (pin == tempPin) {
            try {
                crashLogger.logDebug(TAG, "handleConfirmPinComplete: PIN confirmed, saving...")
                authPreferences.setPin(pin)
                crashLogger.logDebug(TAG, "handleConfirmPinComplete: PIN saved, checking admin PIN status")
                // After setting local PIN, require admin PIN setup
                if (!authPreferences.isAdminPinSet()) {
                    crashLogger.logDebug(TAG, "handleConfirmPinComplete: Admin PIN not set, transitioning to SET_ADMIN_PIN")
                    _uiState.value = _uiState.value.copy(
                        mode = PinMode.SET_ADMIN_PIN,
                        currentPin = "",
                        tempPin = null,
                        expectedPinLength = Config.ADMIN_PIN_LENGTH,
                        errorMessage = null
                    )
                } else {
                    crashLogger.logDebug(TAG, "handleConfirmPinComplete: Admin PIN already set, authentication complete")
                    _uiState.value = _uiState.value.copy(isAuthenticated = true)
                }
            } catch (e: Exception) {
                crashLogger.logError(TAG, "handleConfirmPinComplete: CRASH - Failed to save PIN", e)
                _uiState.value = _uiState.value.copy(
                    currentPin = "",
                    errorMessage = "Помилка збереження PIN: ${e.message}",
                    mode = PinMode.SET_PIN,
                    tempPin = null
                )
            }
        } else {
            _uiState.value = _uiState.value.copy(
                currentPin = "",
                errorMessage = "PIN не співпадають. Спробуйте ще раз.",
                mode = PinMode.SET_PIN,
                tempPin = null
            )
        }
    }

    private fun handleVerifyPinComplete(pin: String) {
        if (authPreferences.verifyPin(pin)) {
            authPreferences.clearLockout()
            // Check if admin PIN needs to be set (migration for existing users)
            if (!authPreferences.isAdminPinSet()) {
                _uiState.value = _uiState.value.copy(
                    mode = PinMode.SET_ADMIN_PIN,
                    currentPin = "",
                    failedAttempts = 0,
                    expectedPinLength = Config.ADMIN_PIN_LENGTH,
                    errorMessage = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isAuthenticated = true,
                    failedAttempts = 0
                )
            }
        } else {
            authPreferences.recordFailedAttempt()
            val failedAttempts = authPreferences.getFailedAttempts()
            val isLockedOut = authPreferences.isLockedOut()
            val lockoutSeconds = authPreferences.getLockoutRemainingSeconds()
            
            if (isLockedOut) {
                val lockoutMessage = if (lockoutSeconds >= 60) {
                    "Забагато невдалих спроб. Зачекайте ${lockoutSeconds / 60} хв."
                } else {
                    "Забагато невдалих спроб. Зачекайте ${lockoutSeconds} сек."
                }
                _uiState.value = _uiState.value.copy(
                    currentPin = "",
                    errorMessage = lockoutMessage,
                    failedAttempts = failedAttempts,
                    isLockedOut = true
                )
                startLockoutCountdown()
            } else {
                val attemptsRemaining = 5 - failedAttempts
                _uiState.value = _uiState.value.copy(
                    currentPin = "",
                    errorMessage = "Неправильний PIN. Спроб залишилось: $attemptsRemaining",
                    failedAttempts = failedAttempts
                )
            }
        }
    }

    private fun handleSetAdminPinComplete(pin: String) {
        _uiState.value = _uiState.value.copy(
            mode = PinMode.CONFIRM_ADMIN_PIN,
            currentPin = "",
            tempPin = pin
        )
    }

    private fun handleConfirmAdminPinComplete(pin: String) {
        val tempPin = _uiState.value.tempPin
        if (pin == tempPin) {
            try {
                crashLogger.logDebug(TAG, "handleConfirmAdminPinComplete: Admin PIN confirmed, saving...")
                authPreferences.setAdminPin(pin)
                crashLogger.logDebug(TAG, "handleConfirmAdminPinComplete: Admin PIN saved, authentication complete")
                _uiState.value = _uiState.value.copy(isAuthenticated = true)
            } catch (e: Exception) {
                crashLogger.logError(TAG, "handleConfirmAdminPinComplete: CRASH - Failed to save admin PIN", e)
                _uiState.value = _uiState.value.copy(
                    currentPin = "",
                    errorMessage = "Помилка збереження паролю: ${e.message}",
                    mode = PinMode.SET_ADMIN_PIN,
                    tempPin = null
                )
            }
        } else {
            _uiState.value = _uiState.value.copy(
                currentPin = "",
                errorMessage = "Паролі не співпадають. Спробуйте ще раз.",
                mode = PinMode.SET_ADMIN_PIN,
                tempPin = null
            )
        }
    }

    private fun handleVerifyAdminPinComplete(pin: String) {
        if (authPreferences.verifyAdminPin(pin)) {
            _uiState.value = _uiState.value.copy(
                adminPinVerified = true,
                currentPin = "",
                errorMessage = null
            )
        } else {
            _uiState.value = _uiState.value.copy(
                currentPin = "",
                errorMessage = "Неправильний пароль адміністратора"
            )
        }
    }

    private fun startLockoutCountdown() {
        viewModelScope.launch {
            while (authPreferences.isLockedOut()) {
                delay(1000)
            }
            authPreferences.clearLockout()
            _uiState.value = _uiState.value.copy(
                isLockedOut = false,
                failedAttempts = 0,
                errorMessage = null
            )
        }
    }
    
    /**
     * Called when biometric authentication succeeds.
     */
    fun onBiometricSuccess() {
        authPreferences.clearLockout()
        _uiState.value = _uiState.value.copy(
            isAuthenticated = true,
            failedAttempts = 0
        )
    }
    
    /**
     * Mark that biometric prompt was shown (to avoid showing twice).
     */
    fun onBiometricPromptShown() {
        _uiState.value = _uiState.value.copy(biometricPromptShown = true)
    }
}

data class PinUiState(
    val mode: PinMode = PinMode.VERIFY_PIN,
    val currentPin: String = "",
    val tempPin: String? = null,
    val errorMessage: String? = null,
    val failedAttempts: Int = 0,
    val isLockedOut: Boolean = false,
    val isAuthenticated: Boolean = false,
    val minPinLength: Int = 4,
    val maxPinLength: Int = 4,
    val biometricPromptShown: Boolean = false,
    val adminPinVerified: Boolean = false,  // For protected action verification
    val expectedPinLength: Int = 4  // Dynamic based on current mode
) {
    val canSubmit: Boolean
        get() = currentPin.length >= minPinLength && !isLockedOut
}

enum class PinMode {
    SET_PIN,           // First-time PIN setup
    CONFIRM_PIN,       // Confirm new PIN
    VERIFY_PIN,        // Normal login
    SET_ADMIN_PIN,     // Admin PIN setup (after local PIN)
    CONFIRM_ADMIN_PIN, // Confirm admin PIN
    VERIFY_ADMIN_PIN   // Verify admin PIN (for protected actions)
}
