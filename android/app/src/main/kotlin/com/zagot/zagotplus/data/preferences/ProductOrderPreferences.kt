package com.zagot.zagotplus.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages product display order preferences.
 * Order is shared between purchase and sale screens.
 * Stored locally per device.
 */
@Singleton
open class ProductOrderPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _productOrderFlow = MutableStateFlow(getProductOrder())

    /**
     * Observable flow of product order.
     * List of product IDs in display order.
     */
    val productOrderFlow: StateFlow<List<UUID>> = _productOrderFlow.asStateFlow()

    /**
     * Get the saved product order.
     * Returns empty list if no order is saved.
     */
    open fun getProductOrder(): List<UUID> {
        val orderString = prefs.getString(KEY_PRODUCT_ORDER, null)
        if (orderString.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            orderString.split(",").mapNotNull { 
                try { UUID.fromString(it.trim()) } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Save product order.
     * Products not in this list will appear after ordered products.
     */
    fun setProductOrder(order: List<UUID>) {
        val orderString = order.joinToString(",") { it.toString() }
        prefs.edit().putString(KEY_PRODUCT_ORDER, orderString).apply()
        _productOrderFlow.value = order
    }

    /**
     * Apply saved order to a list of products.
     * Products in saved order appear first (in that order),
     * remaining products appear after in their original order.
     */
    open fun <T> applyOrder(products: List<T>, getId: (T) -> UUID): List<T> {
        val savedOrder = getProductOrder()
        if (savedOrder.isEmpty()) return products

        val orderMap = savedOrder.withIndex().associate { it.value to it.index }
        val (ordered, unordered) = products.partition { orderMap.containsKey(getId(it)) }
        
        return ordered.sortedBy { orderMap[getId(it)] } + unordered
    }

    companion object {
        private const val PREFS_NAME = "zagot_product_order_prefs"
        private const val KEY_PRODUCT_ORDER = "product_order"
    }
}
