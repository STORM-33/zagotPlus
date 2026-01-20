package com.zagot.zagotplus.ui.screens.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zagot.zagotplus.data.preferences.AuthPreferences
import com.zagot.zagotplus.debug.CrashLogger
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for PinScreen.
 * Tests the PIN entry flow, validation feedback, and authentication states.
 */
class PinScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ==================== Initial State Tests ====================

    @Test
    fun pinScreen_displaysSetPinTitle_whenNoPinSet() {
        val viewModel = createTestViewModel(isPinSet = false)
        
        composeTestRule.setContent {
            PinScreen(
                onAuthenticated = {},
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithText("Встановіть PIN-код").assertIsDisplayed()
    }

    @Test
    fun pinScreen_displaysVerifyPinTitle_whenPinExists() {
        val viewModel = createTestViewModel(isPinSet = true)
        
        composeTestRule.setContent {
            PinScreen(
                onAuthenticated = {},
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithText("Введіть PIN-код").assertIsDisplayed()
    }

    @Test
    fun pinScreen_displaysAllKeypadDigits() {
        val viewModel = createTestViewModel(isPinSet = true)
        
        composeTestRule.setContent {
            PinScreen(
                onAuthenticated = {},
                viewModel = viewModel
            )
        }

        // Verify all digits 0-9 are displayed
        for (digit in 0..9) {
            composeTestRule.onNodeWithText(digit.toString()).assertIsDisplayed()
        }
    }

    @Test
    fun pinScreen_displaysBackspaceButton() {
        val viewModel = createTestViewModel(isPinSet = true)
        
        composeTestRule.setContent {
            PinScreen(
                onAuthenticated = {},
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithContentDescription("Видалити").assertIsDisplayed()
    }

    // ==================== PIN Entry Tests ====================

    @Test
    fun pinScreen_digitClick_updatesDotsDisplay() {
        val viewModel = createTestViewModel(isPinSet = true)
        
        composeTestRule.setContent {
            PinScreen(
                onAuthenticated = {},
                viewModel = viewModel
            )
        }

        // Click digit 1
        composeTestRule.onNodeWithText("1").performClick()
        
        // Verify state updated (dots visual change happens via recomposition)
        composeTestRule.waitForIdle()
        assert(viewModel.uiState.value.currentPin == "1")
    }

    @Test
    fun pinScreen_multipleDigitClicks_accumulatesPin() {
        val viewModel = createTestViewModel(isPinSet = true)
        
        composeTestRule.setContent {
            PinScreen(
                onAuthenticated = {},
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("3").performClick()
        
        composeTestRule.waitForIdle()
        assert(viewModel.uiState.value.currentPin == "123")
    }

    @Test
    fun pinScreen_backspaceClick_removesLastDigit() {
        val viewModel = createTestViewModel(isPinSet = true)
        
        composeTestRule.setContent {
            PinScreen(
                onAuthenticated = {},
                viewModel = viewModel
            )
        }

        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithContentDescription("Видалити").performClick()
        
        composeTestRule.waitForIdle()
        assert(viewModel.uiState.value.currentPin == "1")
    }

    // ==================== Error Display Tests ====================

    @Test
    fun pinScreen_displaysErrorMessage_whenPresent() {
        val viewModel = createTestViewModel(isPinSet = true)
        // Trigger error by entering wrong PIN 4 times
        repeat(4) { viewModel.onDigitPressed(9) }
        
        composeTestRule.setContent {
            PinScreen(
                onAuthenticated = {},
                viewModel = viewModel
            )
        }

        // Error message should be displayed
        composeTestRule.waitForIdle()
        viewModel.uiState.value.errorMessage?.let { errorMsg ->
            composeTestRule.onNodeWithText(errorMsg).assertIsDisplayed()
        }
    }

    // ==================== PIN Setup Flow Tests ====================

    @Test
    fun pinScreen_navigatesToConfirmPin_afterSettingPin() {
        val viewModel = createTestViewModel(isPinSet = false)
        
        composeTestRule.setContent {
            PinScreen(
                onAuthenticated = {},
                viewModel = viewModel
            )
        }

        // Enter 4 digits
        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("3").performClick()
        composeTestRule.onNodeWithText("4").performClick()
        
        composeTestRule.waitForIdle()
        
        // Should now be in confirm mode
        assert(viewModel.uiState.value.mode == PinMode.CONFIRM_PIN)
        composeTestRule.onNodeWithText("Підтвердіть PIN-код").assertIsDisplayed()
    }

    @Test
    fun pinScreen_authenticates_afterMatchingConfirmation() {
        var authenticated = false
        val viewModel = createTestViewModel(isPinSet = false)
        
        composeTestRule.setContent {
            PinScreen(
                onAuthenticated = { authenticated = true },
                viewModel = viewModel
            )
        }

        // Set PIN: 1234
        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("3").performClick()
        composeTestRule.onNodeWithText("4").performClick()
        composeTestRule.waitForIdle()

        // Confirm PIN: 1234
        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule.onNodeWithText("2").performClick()
        composeTestRule.onNodeWithText("3").performClick()
        composeTestRule.onNodeWithText("4").performClick()
        composeTestRule.waitForIdle()

        assert(viewModel.uiState.value.isAuthenticated)
    }

    // ==================== Lockout Tests ====================

    @Test
    fun pinScreen_disablesKeypad_whenLockedOut() {
        val viewModel = createTestViewModel(isPinSet = true, isLockedOut = true)
        
        composeTestRule.setContent {
            PinScreen(
                onAuthenticated = {},
                viewModel = viewModel
            )
        }

        // Keypad buttons should be disabled
        composeTestRule.onNodeWithText("1").assertIsNotEnabled()
        composeTestRule.onNodeWithText("5").assertIsNotEnabled()
        composeTestRule.onNodeWithText("9").assertIsNotEnabled()
    }

    // ==================== Helper ====================

    private fun createTestViewModel(
        isPinSet: Boolean,
        isLockedOut: Boolean = false
    ): PinViewModel {
        val mockPrefs = FakeAuthPreferences(
            pinSet = isPinSet,
            lockedOut = isLockedOut
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        return PinViewModel(mockPrefs, CrashLogger(context))
    }
    
    /**
     * Fake implementation of AuthPreferences for testing.
     */
    private class FakeAuthPreferences(
        private var pinSet: Boolean,
        private var lockedOut: Boolean
    ) : AuthPreferences {
        private var pin: String? = if (pinSet) "1234" else null
        private var failedAttempts = 0
        private var authenticated = false
        private var adminPinSet = false
        private var adminPin: String? = null
        private var restrictedMode = false
        private val _restrictedModeFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
        override val restrictedModeFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _restrictedModeFlow
        
        override fun isPinSet(): Boolean = pin != null
        override fun setPin(pin: String) { 
            this.pin = pin
            this.pinSet = true
        }
        override fun verifyPin(enteredPin: String): Boolean = enteredPin == pin
        override fun clearPin() { 
            pin = null 
            pinSet = false
        }
        override fun recordFailedAttempt() { failedAttempts++ }
        override fun isLockedOut(): Boolean = lockedOut
        override fun getLockoutRemainingSeconds(): Int = if (lockedOut) 30 else 0
        override fun getFailedAttempts(): Int = failedAttempts
        override fun clearLockout() { 
            failedAttempts = 0
            lockedOut = false
        }
        override fun setAuthenticated(authenticated: Boolean) { this.authenticated = authenticated }
        override fun isAuthenticated(): Boolean = authenticated
        override fun isSessionValid(): Boolean = authenticated
        override fun getSessionRemainingMinutes(): Int = if (authenticated) 240 else 0
        
        override fun isAdminPinSet(): Boolean = adminPinSet
        override fun setAdminPin(pin: String) {
            adminPin = pin
            adminPinSet = true
        }
        override fun verifyAdminPin(pin: String): Boolean = pin == adminPin
        
        override fun isRestrictedMode(): Boolean = restrictedMode
        override fun setRestrictedMode(restricted: Boolean) {
            restrictedMode = restricted
            _restrictedModeFlow.value = restricted
        }
    }
}
