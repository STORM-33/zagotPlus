package com.zagot.zagotplus.ui.screens.products

import com.zagot.zagotplus.data.remote.SupabaseStorageHelper
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ProductsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var productRepository: ProductRepository
    private lateinit var supabaseStorageHelper: SupabaseStorageHelper
    private lateinit var viewModel: ProductsViewModel

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
        productRepository = mockk()
        supabaseStorageHelper = mockk()
        every { productRepository.getAllProducts() } returns flowOf(listOf(testProduct, inactiveProduct))
        every { supabaseStorageHelper.needsUpload(any()) } returns false
        every { supabaseStorageHelper.needsUpload(null) } returns false
    }

    private fun createViewModel(): ProductsViewModel {
        return ProductsViewModel(productRepository, supabaseStorageHelper)
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
            productRepository.createProduct(any(), any(), any(), any())
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
                defaultSellPrice = BigDecimal("50.00"),
                imageUri = null
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
            productRepository.createProduct(any(), any(), any(), any())
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

    // ==================== Image Upload Tests ====================

    @Test
    fun `saveProduct uploads local image before creating product`() = runTest {
        val localUri = "content://media/images/123"
        val uploadedUrl = "https://abc.supabase.co/storage/v1/object/public/product-images/uuid.jpg"
        
        every { supabaseStorageHelper.needsUpload(localUri) } returns true
        coEvery { supabaseStorageHelper.uploadImage(localUri) } returns uploadedUrl
        coEvery {
            productRepository.createProduct(any(), any(), any(), any())
        } returns testProduct.copy(imageUri = uploadedUrl)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showAddDialog()
        viewModel.setDialogName("Product with Image")
        viewModel.setDialogBuyPrice("45.00")
        viewModel.setDialogImageUri(localUri)
        viewModel.saveProduct()
        advanceUntilIdle()

        coVerify { supabaseStorageHelper.uploadImage(localUri) }
        coVerify {
            productRepository.createProduct(
                name = "Product with Image",
                defaultBuyPrice = BigDecimal("45.00"),
                defaultSellPrice = null,
                imageUri = uploadedUrl
            )
        }
    }

    @Test
    fun `saveProduct does not upload if URI is already Supabase URL`() = runTest {
        val supabaseUrl = "https://abc.supabase.co/storage/v1/object/public/product-images/existing.jpg"
        
        every { supabaseStorageHelper.needsUpload(supabaseUrl) } returns false
        coEvery {
            productRepository.createProduct(any(), any(), any(), any())
        } returns testProduct.copy(imageUri = supabaseUrl)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showAddDialog()
        viewModel.setDialogName("Product with existing image")
        viewModel.setDialogImageUri(supabaseUrl)
        viewModel.saveProduct()
        advanceUntilIdle()

        coVerify(exactly = 0) { supabaseStorageHelper.uploadImage(any()) }
        coVerify {
            productRepository.createProduct(
                name = "Product with existing image",
                defaultBuyPrice = null,
                defaultSellPrice = null,
                imageUri = supabaseUrl
            )
        }
    }

    @Test
    fun `saveProduct handles upload failure gracefully`() = runTest {
        val localUri = "content://media/images/456"
        
        every { supabaseStorageHelper.needsUpload(localUri) } returns true
        coEvery { supabaseStorageHelper.uploadImage(localUri) } returns null
        coEvery {
            productRepository.createProduct(any(), any(), any(), any())
        } returns testProduct

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showAddDialog()
        viewModel.setDialogName("Product with failed upload")
        viewModel.setDialogImageUri(localUri)
        viewModel.saveProduct()
        advanceUntilIdle()

        // Should still create product but with null imageUri
        coVerify {
            productRepository.createProduct(
                name = "Product with failed upload",
                defaultBuyPrice = null,
                defaultSellPrice = null,
                imageUri = null
            )
        }
    }

    @Test
    fun `saveProduct uploads image when updating existing product`() = runTest {
        val localUri = "content://media/images/789"
        val uploadedUrl = "https://abc.supabase.co/storage/v1/object/public/product-images/new.jpg"
        
        every { supabaseStorageHelper.needsUpload(localUri) } returns true
        coEvery { supabaseStorageHelper.uploadImage(localUri) } returns uploadedUrl
        coEvery { productRepository.updateProduct(any()) } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showEditDialog(testProduct)
        viewModel.setDialogImageUri(localUri)
        viewModel.saveProduct()
        advanceUntilIdle()

        coVerify { supabaseStorageHelper.uploadImage(localUri) }
        coVerify {
            productRepository.updateProduct(match {
                it.id == testProduct.id && it.imageUri == uploadedUrl
            })
        }
    }

    @Test
    fun `setDialogImageUri updates state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showAddDialog()
        assertNull(viewModel.uiState.value.dialogImageUri)

        viewModel.setDialogImageUri("content://test/image")
        assertEquals("content://test/image", viewModel.uiState.value.dialogImageUri)
    }

    @Test
    fun `showEditDialog loads existing image URI`() = runTest {
        val productWithImage = testProduct.copy(
            imageUri = "https://abc.supabase.co/storage/v1/object/public/product-images/existing.jpg"
        )
        every { productRepository.getAllProducts() } returns flowOf(listOf(productWithImage))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showEditDialog(productWithImage)

        assertEquals(productWithImage.imageUri, viewModel.uiState.value.dialogImageUri)
    }

    @Test
    fun `deleteProduct calls repository and shows success`() = runTest {
        coEvery { productRepository.deleteProduct(any()) } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteProduct(testProduct.id)
        advanceUntilIdle()

        coVerify { productRepository.deleteProduct(testProduct.id) }
        assertTrue(viewModel.uiState.value.showSuccess)
        assertEquals("Товар видалено", viewModel.uiState.value.successMessage)
    }

    @Test
    fun `deleteProduct handles error`() = runTest {
        coEvery { productRepository.deleteProduct(any()) } throws RuntimeException("Delete failed")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteProduct(testProduct.id)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `dismissSuccess clears success state`() = runTest {
        coEvery { productRepository.deleteProduct(any()) } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteProduct(testProduct.id)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showSuccess)

        viewModel.dismissSuccess()
        assertFalse(viewModel.uiState.value.showSuccess)
    }
}
