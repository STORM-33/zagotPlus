package com.zagot.zagotplus.ui.screens.products

import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.repository.ProductRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ProductsViewModelTest {

    private lateinit var productRepository: ProductRepository
    private lateinit var viewModel: ProductsViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testProduct = Product(
        id = UUID.randomUUID(),
        name = "Горіх білий",
        defaultBuyPrice = BigDecimal("45.00"),
        defaultSellPrice = BigDecimal("50.00"),
        isActive = true,
        createdAt = Instant.now()
    )

    private val inactiveProduct = Product(
        id = UUID.randomUUID(),
        name = "Горіх чорний",
        defaultBuyPrice = BigDecimal("40.00"),
        defaultSellPrice = BigDecimal("48.00"),
        isActive = false,
        createdAt = Instant.now()
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        productRepository = mockk()
        every { productRepository.getAllProducts() } returns flowOf(listOf(testProduct, inactiveProduct))
    }

    private fun createViewModel(): ProductsViewModel {
        return ProductsViewModel(productRepository)
    }

    @Test
    fun `loads products on init`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.products.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun `sorted products shows active first`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val sorted = viewModel.uiState.value.sortedProducts
        assertTrue(sorted[0].isActive)
        assertFalse(sorted[1].isActive)
    }

    @Test
    fun `showAddDialog sets dialog state correctly`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showAddDialog()

        val state = viewModel.uiState.value
        assertTrue(state.showDialog)
        assertNull(state.editingProduct)
        assertEquals("", state.dialogName)
        assertEquals("", state.dialogBuyPrice)
        assertEquals("", state.dialogSellPrice)
    }

    @Test
    fun `showEditDialog populates dialog with product data`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showEditDialog(testProduct)

        val state = viewModel.uiState.value
        assertTrue(state.showDialog)
        assertEquals(testProduct, state.editingProduct)
        assertEquals(testProduct.name, state.dialogName)
        assertEquals("45.00", state.dialogBuyPrice)
        assertEquals("50.00", state.dialogSellPrice)
    }

    @Test
    fun `validation rejects empty name`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showAddDialog()
        viewModel.setDialogName("")
        viewModel.setDialogBuyPrice("45.00")

        val state = viewModel.uiState.value
        assertNotNull(state.dialogNameError)
        assertEquals("Назва обов'язкова", state.dialogNameError)
    }

    @Test
    fun `validation rejects negative price`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showAddDialog()
        viewModel.setDialogName("Test Product")
        viewModel.setDialogBuyPrice("-10")

        val state = viewModel.uiState.value
        assertNotNull(state.dialogBuyPriceError)
        assertEquals("Ціна має бути додатною", state.dialogBuyPriceError)
    }

    @Test
    fun `validation rejects zero price`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showAddDialog()
        viewModel.setDialogName("Test Product")
        viewModel.setDialogSellPrice("0")

        val state = viewModel.uiState.value
        assertNotNull(state.dialogSellPriceError)
        assertEquals("Ціна має бути додатною", state.dialogSellPriceError)
    }

    @Test
    fun `validation accepts empty price (optional)`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showAddDialog()
        viewModel.setDialogName("Test Product")
        viewModel.setDialogBuyPrice("")

        val state = viewModel.uiState.value
        assertNull(state.dialogBuyPriceError)
    }

    @Test
    fun `validation rejects invalid price format`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showAddDialog()
        viewModel.setDialogName("Test Product")
        viewModel.setDialogBuyPrice("abc")

        val state = viewModel.uiState.value
        assertNotNull(state.dialogBuyPriceError)
        assertEquals("Невірний формат ціни", state.dialogBuyPriceError)
    }

    @Test
    fun `canSaveDialog is true when name is valid`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showAddDialog()
        viewModel.setDialogName("Test Product")
        viewModel.setDialogBuyPrice("45.00")

        assertTrue(viewModel.uiState.value.canSaveDialog)
    }

    @Test
    fun `canSaveDialog is false when name is blank`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showAddDialog()
        viewModel.setDialogName("")
        viewModel.setDialogBuyPrice("45.00")

        assertFalse(viewModel.uiState.value.canSaveDialog)
    }

    @Test
    fun `saveProduct calls createProduct for new product`() = runTest {
        val newProduct = testProduct.copy(id = UUID.randomUUID(), name = "New Product")
        coEvery {
            productRepository.createProduct(any(), any(), any())
        } returns newProduct

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showAddDialog()
        viewModel.setDialogName("New Product")
        viewModel.setDialogBuyPrice("45.00")
        viewModel.setDialogSellPrice("50.00")
        viewModel.saveProduct()
        advanceUntilIdle()

        coVerify {
            productRepository.createProduct(
                name = "New Product",
                defaultBuyPrice = BigDecimal("45.00"),
                defaultSellPrice = BigDecimal("50.00")
            )
        }

        val state = viewModel.uiState.value
        assertTrue(state.showSuccess)
        assertFalse(state.showDialog)
    }

    @Test
    fun `saveProduct calls updateProduct for existing product`() = runTest {
        coEvery {
            productRepository.updateProduct(any())
        } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showEditDialog(testProduct)
        viewModel.setDialogName("Updated Name")
        viewModel.saveProduct()
        advanceUntilIdle()

        coVerify {
            productRepository.updateProduct(match {
                it.name == "Updated Name" && it.id == testProduct.id
            })
        }

        val state = viewModel.uiState.value
        assertTrue(state.showSuccess)
        assertEquals("Товар оновлено", state.successMessage)
    }

    @Test
    fun `toggleProductActive calls repository`() = runTest {
        coEvery {
            productRepository.toggleProductActive(any())
        } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleProductActive(testProduct.id)
        advanceUntilIdle()

        coVerify {
            productRepository.toggleProductActive(testProduct.id)
        }
    }

    @Test
    fun `dismissDialog hides dialog`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showAddDialog()
        assertTrue(viewModel.uiState.value.showDialog)

        viewModel.dismissDialog()
        assertFalse(viewModel.uiState.value.showDialog)
    }

    @Test
    fun `dismissError clears error`() = runTest {
        coEvery {
            productRepository.createProduct(any(), any(), any())
        } throws RuntimeException("Database error")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showAddDialog()
        viewModel.setDialogName("Test")
        viewModel.saveProduct()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)

        viewModel.dismissError()
        assertNull(viewModel.uiState.value.error)
    }
}
