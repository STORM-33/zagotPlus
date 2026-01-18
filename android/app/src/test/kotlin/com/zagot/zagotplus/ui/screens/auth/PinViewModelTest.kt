package com.zagot.zagotplus.ui.screens.auth

import com.zagot.zagotplus.data.preferences.AuthPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PinViewModelTest {

    private lateinit var authPreferences: AuthPreferences
    private lateinit var viewModel: PinViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        authPreferences = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is SET_PIN when no PIN set`() {
        every { authPreferences.isPinSet() } returns false
        every { authPreferences.isLockedOut() } returns false
        every { authPreferences.getFailedAttempts() } returns 0
        
        viewModel = PinViewModel(authPreferences)
        
        assertEquals(PinMode.SET_PIN, viewModel.uiState.value.mode)
    }

    @Test
    fun `initial state is VERIFY_PIN when PIN exists`() {
        every { authPreferences.isPinSet() } returns true
        every { authPreferences.isLockedOut() } returns false
        every { authPreferences.getFailedAttempts() } returns 0
        
        viewModel = PinViewModel(authPreferences)
        
        assertEquals(PinMode.VERIFY_PIN, viewModel.uiState.value.mode)
    }

    @Test
    fun `entering 4 digits and confirming in SET_PIN switches to CONFIRM_PIN`() {
        every { authPreferences.isPinSet() } returns false
        every { authPreferences.isLockedOut() } returns false
        every { authPreferences.getFailedAttempts() } returns 0
        viewModel = PinViewModel(authPreferences)
        
        viewModel.onDigitPressed(1)
        viewModel.onDigitPressed(2)
        viewModel.onDigitPressed(3)
        viewModel.onDigitPressed(4)
        viewModel.onConfirmPressed()
        
        assertEquals(PinMode.CONFIRM_PIN, viewModel.uiState.value.mode)
        assertEquals("", viewModel.uiState.value.currentPin)
        assertEquals("1234", viewModel.uiState.value.tempPin)
    }

    @Test
    fun `matching confirmation PIN authenticates user`() {
        every { authPreferences.isPinSet() } returns false
        every { authPreferences.isAdminPinSet() } returns true
        every { authPreferences.isLockedOut() } returns false
        every { authPreferences.getFailedAttempts() } returns 0
        viewModel = PinViewModel(authPreferences)
        
        // Set PIN
        viewModel.onDigitPressed(1)
        viewModel.onDigitPressed(2)
        viewModel.onDigitPressed(3)
        viewModel.onDigitPressed(4)
        viewModel.onConfirmPressed()
        
        // Confirm PIN
        viewModel.onDigitPressed(1)
        viewModel.onDigitPressed(2)
        viewModel.onDigitPressed(3)
        viewModel.onDigitPressed(4)
        viewModel.onConfirmPressed()
        
        verify { authPreferences.setPin("1234") }
        assertTrue(viewModel.uiState.value.isAuthenticated)
    }

    @Test
    fun `non-matching confirmation PIN resets to SET_PIN`() {
        every { authPreferences.isPinSet() } returns false
        every { authPreferences.isLockedOut() } returns false
        every { authPreferences.getFailedAttempts() } returns 0
        viewModel = PinViewModel(authPreferences)
        
        // Set PIN
        viewModel.onDigitPressed(1)
        viewModel.onDigitPressed(2)
        viewModel.onDigitPressed(3)
        viewModel.onDigitPressed(4)
        viewModel.onConfirmPressed()
        
        // Wrong confirmation
        viewModel.onDigitPressed(9)
        viewModel.onDigitPressed(9)
        viewModel.onDigitPressed(9)
        viewModel.onDigitPressed(9)
        viewModel.onConfirmPressed()
        
        assertEquals(PinMode.SET_PIN, viewModel.uiState.value.mode)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertEquals("", viewModel.uiState.value.currentPin)
    }

    @Test
    fun `correct PIN verification authenticates user`() {
        every { authPreferences.isPinSet() } returns true
        every { authPreferences.isAdminPinSet() } returns true
        every { authPreferences.isLockedOut() } returns false
        every { authPreferences.getFailedAttempts() } returns 0
        every { authPreferences.verifyPin("1234") } returns true
        viewModel = PinViewModel(authPreferences)
        
        viewModel.onDigitPressed(1)
        viewModel.onDigitPressed(2)
        viewModel.onDigitPressed(3)
        viewModel.onDigitPressed(4)
        
        verify { authPreferences.clearLockout() }
        assertTrue(viewModel.uiState.value.isAuthenticated)
    }

    @Test
    fun `incorrect PIN verification shows error`() {
        every { authPreferences.isPinSet() } returns true
        every { authPreferences.isLockedOut() } returns false
        every { authPreferences.getFailedAttempts() } returnsMany listOf(0, 1)
        every { authPreferences.verifyPin(any()) } returns false
        viewModel = PinViewModel(authPreferences)
        
        viewModel.onDigitPressed(9)
        viewModel.onDigitPressed(9)
        viewModel.onDigitPressed(9)
        viewModel.onDigitPressed(9)
        
        verify { authPreferences.recordFailedAttempt() }
        assertFalse(viewModel.uiState.value.isAuthenticated)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertEquals(1, viewModel.uiState.value.failedAttempts)
    }

    @Test
    fun `3 failed attempts triggers lockout`() {
        every { authPreferences.isPinSet() } returns true
        every { authPreferences.isLockedOut() } returnsMany listOf(false, false, false, true)
        every { authPreferences.getFailedAttempts() } returnsMany listOf(0, 1, 2, 3)
        every { authPreferences.verifyPin(any()) } returns false
        viewModel = PinViewModel(authPreferences)
        
        // Attempt 1
        repeat(4) { viewModel.onDigitPressed(9) }
        // Attempt 2
        repeat(4) { viewModel.onDigitPressed(9) }
        // Attempt 3
        repeat(4) { viewModel.onDigitPressed(9) }
        
        assertTrue(viewModel.uiState.value.isLockedOut)
        assertEquals(3, viewModel.uiState.value.failedAttempts)
    }

    @Test
    fun `lockout clears after 30 seconds`() = runTest {
        every { authPreferences.isPinSet() } returns true
        every { authPreferences.isLockedOut() } returnsMany listOf(false, false, false, true, true, false)
        every { authPreferences.getFailedAttempts() } returnsMany listOf(0, 1, 2, 3)
        every { authPreferences.verifyPin(any()) } returns false
        viewModel = PinViewModel(authPreferences)
        
        // Trigger lockout
        repeat(3) {
            repeat(4) { viewModel.onDigitPressed(9) }
        }
        
        assertTrue(viewModel.uiState.value.isLockedOut)
        
        advanceTimeBy(30_000)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isLockedOut)
        assertEquals(0, viewModel.uiState.value.failedAttempts)
    }

    @Test
    fun `backspace removes last digit`() {
        every { authPreferences.isPinSet() } returns true
        every { authPreferences.isLockedOut() } returns false
        every { authPreferences.getFailedAttempts() } returns 0
        viewModel = PinViewModel(authPreferences)
        
        viewModel.onDigitPressed(1)
        viewModel.onDigitPressed(2)
        viewModel.onBackspacePressed()
        
        assertEquals("1", viewModel.uiState.value.currentPin)
    }
}
