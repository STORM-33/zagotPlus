package com.zagot.zagotplus.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.data.preferences.AuthPreferences
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
            failedAttempts = failedAttempts
        )
        
        if (isLockedOut) {
            startLockoutCountdown()
        }
    }

    fun onDigitPressed(digit: Int) {
        val currentState = _uiState.value
        if (currentState.currentPin.length >= 4 || currentState.isLockedOut) return

        val newPin = currentState.currentPin + digit.toString()
        _uiState.value = currentState.copy(
            currentPin = newPin,
            errorMessage = null
        )

        if (newPin.length == 4) {
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
            
            if (isLockedOut) {
                _uiState.value = _uiState.value.copy(
                    currentPin = "",
                    errorMessage = "Забагато невдалих спроб. Зачекайте 30 секунд.",
                    failedAttempts = failedAttempts,
                    isLockedOut = true
                )
                startLockoutCountdown()
            } else {
                _uiState.value = _uiState.value.copy(
                    currentPin = "",
                    errorMessage = "Неправильний PIN. Спроб залишилось: ${3 - failedAttempts}",
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
}

data class PinUiState(
    val mode: PinMode = PinMode.VERIFY_PIN,
    val currentPin: String = "",
    val tempPin: String? = null,
    val errorMessage: String? = null,
    val failedAttempts: Int = 0,
    val isLockedOut: Boolean = false,
    val isAuthenticated: Boolean = false
)

enum class PinMode {
    SET_PIN,      // First-time PIN setup
    CONFIRM_PIN,  // Confirm new PIN
    VERIFY_PIN    // Normal login
}
