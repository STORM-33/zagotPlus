package com.zagot.zagotplus.ui.screens.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

data class ProductsUiState(
    val products: List<Product> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showSuccess: Boolean = false,
    val successMessage: String = "",
    // Dialog state
    val showDialog: Boolean = false,
    val editingProduct: Product? = null,
    val dialogName: String = "",
    val dialogBuyPrice: String = "",
    val dialogSellPrice: String = "",
    val dialogNameError: String? = null,
    val dialogBuyPriceError: String? = null,
    val dialogSellPriceError: String? = null
) {
    val isEditing: Boolean get() = editingProduct != null
    
    val dialogTitle: String get() = if (isEditing) "Редагувати товар" else "Новий товар"
    
    val canSaveDialog: Boolean get() = dialogName.isNotBlank() && 
        dialogNameError == null && 
        dialogBuyPriceError == null && 
        dialogSellPriceError == null

    // Active products first, then inactive (sorted by name within each group)
    val sortedProducts: List<Product> get() = 
        products.sortedWith(compareByDescending<Product> { it.isActive }.thenBy { it.name })
}

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductsUiState())
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

    init {
        loadProducts()
    }

    private fun loadProducts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                productRepository.getAllProducts().collect { products ->
                    _uiState.update { 
                        it.copy(
                            products = products,
                            isLoading = false
                        ) 
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Помилка завантаження",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun showAddDialog() {
        _uiState.update {
            it.copy(
                showDialog = true,
                editingProduct = null,
                dialogName = "",
                dialogBuyPrice = "",
                dialogSellPrice = "",
                dialogNameError = null,
                dialogBuyPriceError = null,
                dialogSellPriceError = null
            )
        }
    }

    fun showEditDialog(product: Product) {
        _uiState.update {
            it.copy(
                showDialog = true,
                editingProduct = product,
                dialogName = product.name,
                dialogBuyPrice = product.defaultBuyPrice?.toPlainString() ?: "",
                dialogSellPrice = product.defaultSellPrice?.toPlainString() ?: "",
                dialogNameError = null,
                dialogBuyPriceError = null,
                dialogSellPriceError = null
            )
        }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(showDialog = false) }
    }

    fun setDialogName(name: String) {
        _uiState.update { 
            it.copy(
                dialogName = name,
                dialogNameError = validateName(name)
            ) 
        }
    }

    fun setDialogBuyPrice(price: String) {
        _uiState.update { 
            it.copy(
                dialogBuyPrice = price,
                dialogBuyPriceError = validatePrice(price)
            ) 
        }
    }

    fun setDialogSellPrice(price: String) {
        _uiState.update { 
            it.copy(
                dialogSellPrice = price,
                dialogSellPriceError = validatePrice(price)
            ) 
        }
    }

    private fun validateName(name: String): String? {
        return if (name.isBlank()) "Назва обов'язкова" else null
    }

    private fun validatePrice(price: String): String? {
        if (price.isBlank()) return null // Optional field
        return try {
            val value = BigDecimal(price)
            if (value <= BigDecimal.ZERO) "Ціна має бути додатною" else null
        } catch (e: NumberFormatException) {
            "Невірний формат ціни"
        }
    }

    fun saveProduct() {
        val state = _uiState.value
        
        // Final validation
        val nameError = validateName(state.dialogName)
        val buyPriceError = validatePrice(state.dialogBuyPrice)
        val sellPriceError = validatePrice(state.dialogSellPrice)
        
        if (nameError != null || buyPriceError != null || sellPriceError != null) {
            _uiState.update {
                it.copy(
                    dialogNameError = nameError,
                    dialogBuyPriceError = buyPriceError,
                    dialogSellPriceError = sellPriceError
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val buyPrice = state.dialogBuyPrice.takeIf { it.isNotBlank() }?.let { BigDecimal(it) }
                val sellPrice = state.dialogSellPrice.takeIf { it.isNotBlank() }?.let { BigDecimal(it) }

                if (state.isEditing) {
                    val updated = state.editingProduct!!.copy(
                        name = state.dialogName.trim(),
                        defaultBuyPrice = buyPrice,
                        defaultSellPrice = sellPrice
                    )
                    productRepository.updateProduct(updated)
                    _uiState.update {
                        it.copy(
                            showDialog = false,
                            isLoading = false,
                            showSuccess = true,
                            successMessage = "Товар оновлено"
                        )
                    }
                } else {
                    productRepository.createProduct(
                        name = state.dialogName.trim(),
                        defaultBuyPrice = buyPrice,
                        defaultSellPrice = sellPrice
                    )
                    _uiState.update {
                        it.copy(
                            showDialog = false,
                            isLoading = false,
                            showSuccess = true,
                            successMessage = "Товар додано"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Помилка збереження",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun toggleProductActive(productId: UUID) {
        viewModelScope.launch {
            try {
                productRepository.toggleProductActive(productId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Помилка зміни статусу")
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissSuccess() {
        _uiState.update { it.copy(showSuccess = false) }
    }
}
