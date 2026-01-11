package com.zagot.zagotplus.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionType
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
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class HistoryUiState(
    val transactions: List<HistoryDisplayItem> = emptyList(),
    val totalCount: Int = 0,
    val currentPage: Int = 0,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = false,
    val error: String? = null
)

data class HistoryDisplayItem(
    val id: UUID,
    val type: TransactionType,
    val productName: String,
    val locationName: String,
    val weightKg: BigDecimal,
    val totalAmount: BigDecimal?,
    val createdAt: Instant,
    val isSynced: Boolean
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val productRepository: ProductRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var products: Map<UUID, Product> = emptyMap()
    private var locations: Map<UUID, Location> = emptyMap()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Load reference data
                products = productRepository.getActiveProducts().first().associateBy { it.id }
                locations = locationRepository.getAllLocations().first().associateBy { it.id }

                // Load first page
                val totalCount = transactionRepository.getTotalTransactionCount().first()
                val transactions = transactionRepository.getPaginatedTransactions(
                    limit = PAGE_SIZE,
                    offset = 0
                ).first()

                _uiState.update {
                    it.copy(
                        transactions = transactions.map { tx -> tx.toDisplayItem() },
                        totalCount = totalCount,
                        currentPage = 0,
                        hasMorePages = transactions.size >= PAGE_SIZE,
                        isLoading = false
                    )
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

    fun loadMoreTransactions() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMorePages) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val nextPage = state.currentPage + 1
                val transactions = transactionRepository.getPaginatedTransactions(
                    limit = PAGE_SIZE,
                    offset = nextPage * PAGE_SIZE
                ).first()

                _uiState.update {
                    it.copy(
                        transactions = it.transactions + transactions.map { tx -> tx.toDisplayItem() },
                        currentPage = nextPage,
                        hasMorePages = transactions.size >= PAGE_SIZE,
                        isLoadingMore = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Помилка завантаження",
                        isLoadingMore = false
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val totalCount = transactionRepository.getTotalTransactionCount().first()
                val transactions = transactionRepository.getPaginatedTransactions(
                    limit = PAGE_SIZE,
                    offset = 0
                ).first()

                _uiState.update {
                    it.copy(
                        transactions = transactions.map { tx -> tx.toDisplayItem() },
                        totalCount = totalCount,
                        currentPage = 0,
                        hasMorePages = transactions.size >= PAGE_SIZE,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Помилка оновлення",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun Transaction.toDisplayItem() = HistoryDisplayItem(
        id = id,
        type = type,
        productName = productId?.let { products[it]?.name } ?: "Невідомий товар",
        locationName = locationId?.let { locations[it]?.name } ?: "Невідома локація",
        weightKg = weightKg,
        totalAmount = totalAmount,
        createdAt = createdAt,
        isSynced = syncedAt != null
    )

    companion object {
        private const val PAGE_SIZE = 50
    }
}
