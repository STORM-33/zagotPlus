package com.zagot.zagotplus.ui.screens.sale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.domain.model.InventoryItem
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

data class SaleUiState(
    val products: List<Product> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val selectedProduct: Product? = null,
    val availableWeight: BigDecimal = BigDecimal.ZERO,
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

    val showInventoryWarning: Boolean
        get() {
            val w = weight.toBigDecimalOrNull() ?: return false
            return w > availableWeight
        }

    val canSave: Boolean
        get() = selectedProduct != null &&
                currentLocation != null &&
                (weight.toBigDecimalOrNull() ?: BigDecimal.ZERO) > BigDecimal.ZERO &&
                (pricePerKg.toBigDecimalOrNull() ?: BigDecimal.ZERO) > BigDecimal.ZERO
}

@HiltViewModel
class SaleViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val transactionRepository: TransactionRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SaleUiState())
    val uiState: StateFlow<SaleUiState> = _uiState.asStateFlow()

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

                // Load inventory for current location
                val inventory = if (currentLocation != null) {
                    transactionRepository.getInventoryByLocation(currentLocation.id).first()
                } else {
                    emptyList()
                }

                _uiState.update {
                    it.copy(
                        products = products,
                        inventory = inventory,
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
        val inventory = _uiState.value.inventory
        val availableWeight = inventory
            .find { it.productId == product.id }
            ?.totalWeightKg ?: BigDecimal.ZERO

        _uiState.update {
            it.copy(
                selectedProduct = product,
                availableWeight = availableWeight,
                pricePerKg = product.defaultSellPrice?.toPlainString() ?: ""
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

    fun saveSale() {
        val state = _uiState.value
        val product = state.selectedProduct ?: return
        val location = state.currentLocation ?: return
        val weight = state.weight.toBigDecimalOrNull() ?: return
        val price = state.pricePerKg.toBigDecimalOrNull() ?: return

        if (weight <= BigDecimal.ZERO || price <= BigDecimal.ZERO) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                transactionRepository.createSale(
                    locationId = location.id,
                    productId = product.id,
                    weightKg = weight,
                    pricePerKg = price,
                    notes = state.notes.ifBlank { null }
                )
                
                // Reload inventory after sale
                val newInventory = transactionRepository.getInventoryByLocation(location.id).first()
                
                _uiState.update {
                    it.copy(
                        selectedProduct = null,
                        availableWeight = BigDecimal.ZERO,
                        weight = "",
                        pricePerKg = "",
                        notes = "",
                        inventory = newInventory,
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
