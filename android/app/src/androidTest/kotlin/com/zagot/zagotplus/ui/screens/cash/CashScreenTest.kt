package com.zagot.zagotplus.ui.screens.cash

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zagot.zagotplus.domain.model.CashHistoryItem
import com.zagot.zagotplus.domain.model.CashHistoryItemType
import com.zagot.zagotplus.domain.model.DayCashGroup
import com.zagot.zagotplus.domain.model.ExpenseCategory
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.repository.CashRepository
import com.zagot.zagotplus.domain.repository.LocationRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * UI tests for CashScreen.
 * Tests tab switching, balance display, and action button visibility.
 */
class CashScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var cashRepository: CashRepository
    private lateinit var locationRepository: LocationRepository

    private val testLocationId1 = UUID.randomUUID()
    private val testLocationId2 = UUID.randomUUID()
    private val testCategoryId = UUID.randomUUID()

    private val testLocations = listOf(
        Location(
            id = testLocationId1,
            name = "Склад 1",
            type = com.zagot.zagotplus.domain.model.LocationType.KIOSK,
            createdAt = Instant.now()
        ),
        Location(
            id = testLocationId2,
            name = "Склад 2",
            type = com.zagot.zagotplus.domain.model.LocationType.MOBILE,
            createdAt = Instant.now()
        )
    )

    private val testCategories = listOf(
        ExpenseCategory(
            id = testCategoryId,
            localId = "cat-1",
            name = "Пальне",
            isActive = true,
            createdAt = Instant.now(),
            syncedAt = null
        )
    )

    private val testHistoryItems = listOf(
        CashHistoryItem(
            id = UUID.randomUUID().toString(),
            type = CashHistoryItemType.DEPOSIT,
            amount = BigDecimal("1000.00"),
            notes = "Поповнення",
            categoryName = null,
            itemCount = null,
            weightKg = null,
            createdAt = Instant.now(),
            batchCount = null
        )
    )

    @Before
    fun setup() {
        cashRepository = mockk()
        locationRepository = mockk()
        setupDefaultMocks()
    }

    private fun setupDefaultMocks() {
        // Global totals
        every { cashRepository.getTotalBalance() } returns flowOf(BigDecimal("5000.00"))
        every { cashRepository.getDailyChangeGlobal(any()) } returns flowOf(BigDecimal("500.00"))
        every { cashRepository.getDailyDepositsGlobal(any()) } returns flowOf(BigDecimal("200.00"))
        
        // Location-specific
        every { cashRepository.getBalance(testLocationId1) } returns flowOf(BigDecimal("3000.00"))
        every { cashRepository.getBalance(testLocationId2) } returns flowOf(BigDecimal("2000.00"))
        every { cashRepository.getDailyChange(any(), any()) } returns flowOf(BigDecimal("100.00"))
        every { cashRepository.getDailyDeposits(any(), any()) } returns flowOf(BigDecimal("50.00"))
        
        // Categories and history
        every { cashRepository.getActiveCategories() } returns flowOf(testCategories)
        coEvery { cashRepository.getTotalHistoryCount() } returns 1
        coEvery { cashRepository.getCashHistoryPaged(any(), any()) } returns testHistoryItems
        coEvery { cashRepository.getTotalHistoryCountByLocation(any()) } returns 1
        coEvery { cashRepository.getCashHistoryByLocationPaged(any(), any(), any()) } returns testHistoryItems
        
        // Locations
        every { locationRepository.getAllLocations() } returns flowOf(testLocations)
    }

    private fun createViewModel(): CashViewModel {
        return CashViewModel(cashRepository, locationRepository, Dispatchers.Main)
    }

    // ==================== Tab Display Tests ====================

    @Test
    fun cashScreen_displaysLocationTabs() {
        val viewModel = createViewModel()
        
        composeTestRule.setContent {
            CashScreen(
                onNavigateBack = {},
                viewModel = viewModel
            )
        }

        composeTestRule.waitForIdle()
        
        // Verify location tabs are displayed
        composeTestRule.onNodeWithText("Склад 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Склад 2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Всього").assertIsDisplayed()
    }

    @Test
    fun cashScreen_displaysBalance() {
        val viewModel = createViewModel()
        
        composeTestRule.setContent {
            CashScreen(
                onNavigateBack = {},
                viewModel = viewModel
            )
        }

        composeTestRule.waitForIdle()
        
        // Balance should be displayed (5000.00 for totals view)
        composeTestRule.onNode(hasText("5000.00", substring = true)).assertIsDisplayed()
    }

    // ==================== Tab Switching Tests ====================

    @Test
    fun cashScreen_switchingToLocationTab_updatesBalance() {
        val viewModel = createViewModel()
        
        composeTestRule.setContent {
            CashScreen(
                onNavigateBack = {},
                viewModel = viewModel
            )
        }

        composeTestRule.waitForIdle()
        
        // Click on first location tab
        composeTestRule.onNodeWithText("Склад 1").performClick()
        composeTestRule.waitForIdle()
        
        // Balance should update to location-specific value (3000.00)
        composeTestRule.onNode(hasText("3000.00", substring = true)).assertIsDisplayed()
    }

    @Test
    fun cashScreen_switchingToTotalTab_showsTotalBalance() {
        val viewModel = createViewModel()
        
        composeTestRule.setContent {
            CashScreen(
                onNavigateBack = {},
                viewModel = viewModel
            )
        }

        composeTestRule.waitForIdle()
        
        // First switch to a location
        composeTestRule.onNodeWithText("Склад 1").performClick()
        composeTestRule.waitForIdle()
        
        // Then switch to Total
        composeTestRule.onNodeWithText("Всього").performClick()
        composeTestRule.waitForIdle()
        
        // Should show total balance
        composeTestRule.onNode(hasText("5000.00", substring = true)).assertIsDisplayed()
    }

    // ==================== Action Buttons Tests ====================

    @Test
    fun cashScreen_locationView_showsActionButtons() {
        val viewModel = createViewModel()
        
        composeTestRule.setContent {
            CashScreen(
                onNavigateBack = {},
                viewModel = viewModel
            )
        }

        composeTestRule.waitForIdle()
        
        // Switch to location view
        composeTestRule.onNodeWithText("Склад 1").performClick()
        composeTestRule.waitForIdle()
        
        // Action buttons should be visible
        composeTestRule.onNodeWithText("Поповнити").assertIsDisplayed()
        composeTestRule.onNodeWithText("Зняти").assertIsDisplayed()
        composeTestRule.onNodeWithText("Витрата").assertIsDisplayed()
    }

    @Test
    fun cashScreen_totalView_hidesActionButtons() {
        val viewModel = createViewModel()
        
        composeTestRule.setContent {
            CashScreen(
                onNavigateBack = {},
                viewModel = viewModel
            )
        }

        composeTestRule.waitForIdle()
        
        // Switch to total view
        composeTestRule.onNodeWithText("Всього").performClick()
        composeTestRule.waitForIdle()
        
        // Action buttons should NOT be visible in totals view
        composeTestRule.onNodeWithText("Поповнити").assertDoesNotExist()
        composeTestRule.onNodeWithText("Зняти").assertDoesNotExist()
    }

    @Test
    fun cashScreen_multipleLocations_showsTransferButton() {
        val viewModel = createViewModel()
        
        composeTestRule.setContent {
            CashScreen(
                onNavigateBack = {},
                viewModel = viewModel
            )
        }

        composeTestRule.waitForIdle()
        
        // Switch to location view
        composeTestRule.onNodeWithText("Склад 1").performClick()
        composeTestRule.waitForIdle()
        
        // Transfer button should be visible when there are multiple locations
        composeTestRule.onNodeWithText("Переказ").assertIsDisplayed()
    }

    // ==================== Dialog Tests ====================

    @Test
    fun cashScreen_depositButton_opensDepositDialog() {
        val viewModel = createViewModel()
        
        composeTestRule.setContent {
            CashScreen(
                onNavigateBack = {},
                viewModel = viewModel
            )
        }

        composeTestRule.waitForIdle()
        
        // Switch to location view
        composeTestRule.onNodeWithText("Склад 1").performClick()
        composeTestRule.waitForIdle()
        
        // Click deposit button
        composeTestRule.onNodeWithText("Поповнити").performClick()
        composeTestRule.waitForIdle()
        
        // Dialog should appear
        composeTestRule.onNodeWithText("Поповнення каси").assertIsDisplayed()
    }

    @Test
    fun cashScreen_withdrawButton_opensWithdrawDialog() {
        val viewModel = createViewModel()
        
        composeTestRule.setContent {
            CashScreen(
                onNavigateBack = {},
                viewModel = viewModel
            )
        }

        composeTestRule.waitForIdle()
        
        // Switch to location view
        composeTestRule.onNodeWithText("Склад 1").performClick()
        composeTestRule.waitForIdle()
        
        // Click withdraw button
        composeTestRule.onNodeWithText("Зняти").performClick()
        composeTestRule.waitForIdle()
        
        // Dialog should appear
        composeTestRule.onNodeWithText("Зняття готівки").assertIsDisplayed()
    }

    @Test
    fun cashScreen_paymentButton_opensPaymentDialog() {
        val viewModel = createViewModel()
        
        composeTestRule.setContent {
            CashScreen(
                onNavigateBack = {},
                viewModel = viewModel
            )
        }

        composeTestRule.waitForIdle()
        
        // Switch to location view
        composeTestRule.onNodeWithText("Склад 1").performClick()
        composeTestRule.waitForIdle()
        
        // Click payment button
        composeTestRule.onNodeWithText("Витрата").performClick()
        composeTestRule.waitForIdle()
        
        // Dialog should appear
        composeTestRule.onNodeWithText("Оплата витрати").assertIsDisplayed()
    }

    // ==================== History Display Tests ====================

    @Test
    fun cashScreen_displaysHistoryTitle() {
        val viewModel = createViewModel()
        
        composeTestRule.setContent {
            CashScreen(
                onNavigateBack = {},
                viewModel = viewModel
            )
        }

        composeTestRule.waitForIdle()
        
        // History section title should be displayed
        composeTestRule.onNodeWithText("Історія операцій").assertIsDisplayed()
    }

    // ==================== Daily Change Display Tests ====================

    @Test
    fun cashScreen_displaysPositiveDailyChange() {
        every { cashRepository.getDailyChangeGlobal(any()) } returns flowOf(BigDecimal("500.00"))
        val viewModel = createViewModel()
        
        composeTestRule.setContent {
            CashScreen(
                onNavigateBack = {},
                viewModel = viewModel
            )
        }

        composeTestRule.waitForIdle()
        
        // Daily change with + prefix should be displayed
        composeTestRule.onNode(hasText("+500.00", substring = true)).assertIsDisplayed()
    }
}
