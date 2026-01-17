package com.zagot.zagotplus.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.data.preferences.AuthPreferences
import com.zagot.zagotplus.data.preferences.AuthPreferencesImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PinViewModel @Inject constructor(
    private val authPreferences: AuthPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinUiState())
    val uiState: StateFlow<PinUiState> = _uiState.asStateFlow()

    init {
        val isSettingPin = !authPreferences.isPinSet()
        val isLockedOut = authPreferences.isLockedOut()
        val failedAttempts = authPreferences.getFailedAttempts()
        
        _uiState.value = _uiState.value.copy(
            mode = if (isSettingPin) PinMode.SET_PIN else PinMode.VERIFY_PIN,
            isLockedOut = isLockedOut,
            failedAttempts = failedAttempts,
            minPinLength = AuthPreferencesImpl.MIN_PIN_LENGTH,
            maxPinLength = AuthPreferencesImpl.MAX_PIN_LENGTH
        )
        
        if (isLockedOut) {
            startLockoutCountdown()
        }
    }

    fun onDigitPressed(digit: Int) {
        val currentState = _uiState.value
        if (currentState.currentPin.length >= currentState.maxPinLength || currentState.isLockedOut) return

        val newPin = currentState.currentPin + digit.toString()
        _uiState.value = currentState.copy(
            currentPin = newPin,
            errorMessage = null
        )

        // Auto-submit when PIN reaches max length
        if (newPin.length == currentState.maxPinLength) {
            when (currentState.mode) {
                PinMode.SET_PIN -> handleSetPinComplete(newPin)
                PinMode.CONFIRM_PIN -> handleConfirmPinComplete(newPin)
                PinMode.VERIFY_PIN -> handleVerifyPinComplete(newPin)
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
        
        if (pin.length < currentState.minPinLength) {
            _uiState.value = currentState.copy(
                errorMessage = "PIN має бути мінімум ${currentState.minPinLength} цифр"
            )
            return
        }
        
        when (currentState.mode) {
            PinMode.SET_PIN -> handleSetPinComplete(pin)
            PinMode.CONFIRM_PIN -> handleConfirmPinComplete(pin)
            PinMode.VERIFY_PIN -> handleVerifyPinComplete(pin)
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
            authPreferences.setPin(pin)
            _uiState.value = _uiState.value.copy(isAuthenticated = true)
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
            _uiState.value = _uiState.value.copy(
                isAuthenticated = true,
                failedAttempts = 0
            )
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
    val biometricPromptShown: Boolean = false
) {
    val canSubmit: Boolean
        get() = currentPin.length >= minPinLength && !isLockedOut
}

enum class PinMode {
    SET_PIN,      // First-time PIN setup
    CONFIRM_PIN,  // Confirm new PIN
    VERIFY_PIN    // Normal login
}
