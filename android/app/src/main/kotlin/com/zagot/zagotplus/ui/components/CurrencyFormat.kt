package com.zagot.zagotplus.ui.components

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

/**
 * Centralized currency formatting for the app.
 * All monetary values are rounded to whole hryvnias (no fractional values).
 * 
 * STANDARD: Always use ₴ prefix format: "₴100" (not "100 ₴" or "100 грн")
 */
object CurrencyFormat {
    
    /** Currency symbol - always used as prefix */
    const val CURRENCY_SYMBOL = "₴"
    
    /** Format for currency amounts (whole hryvnias) */
    val currencyFormat: DecimalFormat = DecimalFormat("#,##0")
    
    /** Format for weight in kg (one decimal place) */
    val weightFormat: DecimalFormat = DecimalFormat("#,##0.0")
    
    /** Format for price per kg (two decimal places) */
    val pricePerKgFormat: DecimalFormat = DecimalFormat("#,##0.00")
    
    /**
     * Rounds a BigDecimal to whole hryvnias.
     */
    fun BigDecimal.roundToWhole(): BigDecimal = this.setScale(0, RoundingMode.HALF_UP)
    
    /**
     * Formats a BigDecimal as currency with ₴ prefix (whole hryvnias).
     * Example: ₴1,234
     */
    fun formatCurrency(amount: BigDecimal): String = 
        "$CURRENCY_SYMBOL${currencyFormat.format(amount.roundToWhole())}"
    
    /**
     * Formats a number as currency with ₴ prefix.
     * Example: ₴1,234
     */
    fun formatCurrency(amount: Number): String = 
        "$CURRENCY_SYMBOL${currencyFormat.format(amount)}"
    
    /**
     * Formats a BigDecimal as currency with sign prefix.
     * Positive values get "+₴", negative values get "-₴".
     * Example: +₴100 or -₴50
     */
    fun formatCurrencyWithSign(amount: BigDecimal): String {
        val rounded = amount.roundToWhole()
        val prefix = if (rounded > BigDecimal.ZERO) "+" else ""
        return "$prefix$CURRENCY_SYMBOL${currencyFormat.format(rounded.abs())}"
    }
    
    /**
     * Formats a price per kg with ₴ prefix.
     * Example: ₴12.50/кг
     */
    fun formatPricePerKg(price: BigDecimal): String = 
        "$CURRENCY_SYMBOL${pricePerKgFormat.format(price)}/кг"
    
    /**
     * Formats weight in kg.
     * Example: 123.5 кг
     */
    fun formatWeight(weight: BigDecimal): String = 
        "${weightFormat.format(weight)} кг"
    
    /**
     * Raw format without symbol (for input fields).
     */
    fun formatRaw(amount: BigDecimal): String = 
        currencyFormat.format(amount.roundToWhole())
}

/**
 * Extension function to round BigDecimal to whole hryvnias.
 */
fun BigDecimal.roundToWholeHryvnia(): BigDecimal = this.setScale(0, RoundingMode.HALF_UP)

/**
 * Rounds balance for UI display: values in range (-0.99, 0.99) are displayed as 0.
 * This affects UI display only — internal calculations remain unchanged.
 */
fun BigDecimal.roundBalanceForDisplay(): BigDecimal {
    val threshold = BigDecimal("0.99")
    return if (this > threshold.negate() && this < threshold) {
        BigDecimal.ZERO
    } else {
        this
    }
}

/**
 * Formats balance with UI rounding: if balance is in range (-0.99, 0.99), displays as 0.
 */
fun CurrencyFormat.formatBalanceRounded(amount: BigDecimal): String {
    return formatCurrency(amount.roundBalanceForDisplay())
}
