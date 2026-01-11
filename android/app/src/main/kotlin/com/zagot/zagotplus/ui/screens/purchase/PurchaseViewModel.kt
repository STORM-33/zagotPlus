package com.zagot.zagotplus.ui.screens.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class PurchaseUiState(
    val products: List<Product> = emptyList(),
    val selectedProduct: Product? = null,
    val currentLocation: Location? = null,
    val weight: String = "",
    val pricePerKg: String = "",
    val notes: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val showSuccess: Boolean = false
) {
    val total: BigDecimal?
        get() {
            val w = weight.toBigDecimalOrNull() ?: return null
            val p = pricePerKg.toBigDecimalOrNull() ?: return null
            return if (w > BigDecimal.ZERO && p > BigDecimal.ZERO) w * p else null
        }

    val canSave: Boolean
        get() = selectedProduct != null &&
                currentLocation != null &&
                (weight.toBigDecimalOrNull() ?: BigDecimal.ZERO) > BigDecimal.ZERO &&
                (pricePerKg.toBigDecimalOrNull() ?: BigDecimal.ZERO) > BigDecimal.ZERO
}

@HiltViewModel
class PurchaseViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val transactionRepository: TransactionRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseUiState())
    val uiState: StateFlow<PurchaseUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Load products
                val products = productRepository.getActiveProducts().first()
                // Load first location as current (for now)
                val locations = locationRepository.getAllLocations().first()
                val currentLocation = locations.firstOrNull()

                _uiState.update {
                    it.copy(
                        products = products,
                        currentLocation = currentLocation,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Помилка завантаження даних",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun selectProduct(product: Product) {
        _uiState.update {
            it.copy(
                selectedProduct = product,
                pricePerKg = product.defaultBuyPrice?.toPlainString() ?: ""
            )
        }
    }

    fun setWeight(value: String) {
        _uiState.update { it.copy(weight = value) }
    }

    fun setPricePerKg(value: String) {
        _uiState.update { it.copy(pricePerKg = value) }
    }

    fun setNotes(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissSuccess() {
        _uiState.update { it.copy(showSuccess = false) }
    }

    fun savePurchase() {
        val state = _uiState.value
        val product = state.selectedProduct ?: return
        val location = state.currentLocation ?: return
        val weight = state.weight.toBigDecimalOrNull() ?: return
        val price = state.pricePerKg.toBigDecimalOrNull() ?: return

        if (weight <= BigDecimal.ZERO || price <= BigDecimal.ZERO) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                transactionRepository.createPurchase(
                    locationId = location.id,
                    productId = product.id,
                    weightKg = weight,
                    pricePerKg = price,
                    notes = state.notes.ifBlank { null }
                )
                _uiState.update {
                    it.copy(
                        selectedProduct = null,
                        weight = "",
                        pricePerKg = "",
                        notes = "",
                        isLoading = false,
                        showSuccess = true
                    )
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
}
