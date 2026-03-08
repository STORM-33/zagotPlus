package com.zagot.zagotplus.hardware.printer.receipt

import com.zagot.zagotplus.hardware.printer.escpos.EscPosEncoder
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Builder for purchase receipts.
 * 
 * Usage:
 * ```kotlin
 * val receipt = PurchaseReceiptBuilder()
 *     .receiptNumber("A1B2C3D4")
 *     .locationName("Київ, ринок №1")
 *     .date(LocalDateTime.now())
 *     .addItem("Яблука", BigDecimal("10.5"), BigDecimal("15.00"))
 *     .addItem("Груші", BigDecimal("5.2"), BigDecimal("20.00"))
 *     .notes("Оплата готівкою")
 *     .build()
 * ```
 */
class PurchaseReceiptBuilder(
    private val businessName: String = "ЗАГОТ+",
    private val businessAddress: String? = null
) {
    private val items = mutableListOf<ReceiptItem>()
    private var receiptNumber: String = ""
    private var locationName: String = ""
    private var date: LocalDateTime = LocalDateTime.now()
    private var notes: String? = null

    /**
     * Single receipt line item.
     */
    data class ReceiptItem(
        val name: String,
        val weightKg: BigDecimal,
        val pricePerKg: BigDecimal
    ) {
        val total: BigDecimal
            get() = weightKg.multiply(pricePerKg)
                .setScale(2, RoundingMode.HALF_UP)
    }

    fun receiptNumber(number: String) = apply { this.receiptNumber = number }
    fun locationName(name: String) = apply { this.locationName = name }
    fun date(date: LocalDateTime) = apply { this.date = date }
    fun notes(notes: String?) = apply { this.notes = notes }

    fun addItem(name: String, weightKg: BigDecimal, pricePerKg: BigDecimal) = apply {
        items.add(ReceiptItem(name, weightKg, pricePerKg))
    }

    /**
     * Build the ESC/POS byte array for printing.
     */
    fun build(): ByteArray {
        val encoder = EscPosEncoder().initUkrainian()
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

        // Header
        encoder
            .alignCenter()
            .boldOn()
            .doubleSize()
            .text(businessName)
            .newLine()
            .normalSize()
            .boldOff()

        businessAddress?.let {
            encoder.text(it).newLine()
        }

        if (locationName.isNotBlank()) {
            encoder.text(locationName).newLine()
        }

        encoder.separator('=')

        // Receipt info
        encoder.alignLeft()
        if (receiptNumber.isNotBlank()) {
            encoder.row("Чек №:", receiptNumber)
        }
        encoder.row("Дата:", date.format(dateFormatter))
        encoder.separator()

        // Items
        items.forEach { item ->
            // Product name (bold)
            encoder.boldOn().text(item.name).boldOff().newLine()

            // Weight × Price = Total (indented)
            val weightStr = "${item.weightKg.stripTrailingZeros().toPlainString()} кг"
            val priceStr = "× ${item.pricePerKg.toPlainString()}"
            val totalStr = "= ${item.total.toPlainString()}"

            encoder.text("  $weightStr $priceStr $totalStr").newLine()
        }

        encoder.separator()

        // Totals
        val totalWeight = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.weightKg) }
        val totalAmount = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.total) }

        encoder.row("Вага:", "${totalWeight.stripTrailingZeros().toPlainString()} кг")
        encoder.boldOn()
        encoder.row("РАЗОМ:", "${totalAmount.toPlainString()} грн")
        encoder.boldOff()

        // Notes
        notes?.takeIf { it.isNotBlank() }?.let {
            encoder.separator()
            encoder.text("Примітка: $it").newLine()
        }

        // Footer
        encoder
            .separator('=')
            .alignCenter()
            .text("Дякуємо за співпрацю!")
            .feedLines(6)
            .cut()

        return encoder.toByteArray()
    }

    /**
     * Get a text preview of the receipt (for debugging).
     */
    fun preview(): String {
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        val sb = StringBuilder()

        sb.appendLine("=" .repeat(32))
        sb.appendLine(businessName.padStart(16 + businessName.length / 2))
        businessAddress?.let { sb.appendLine(it) }
        if (locationName.isNotBlank()) sb.appendLine(locationName)
        sb.appendLine("=".repeat(32))

        if (receiptNumber.isNotBlank()) {
            sb.appendLine("Чек №: $receiptNumber")
        }
        sb.appendLine("Дата: ${date.format(dateFormatter)}")
        sb.appendLine("-".repeat(32))

        items.forEach { item ->
            sb.appendLine(item.name)
            sb.appendLine("  ${item.weightKg.stripTrailingZeros().toPlainString()} кг × ${item.pricePerKg.toPlainString()} = ${item.total.toPlainString()}")
        }

        sb.appendLine("-".repeat(32))
        val totalWeight = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.weightKg) }
        val totalAmount = items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.total) }
        sb.appendLine("Вага: ${totalWeight.stripTrailingZeros().toPlainString()} кг")
        sb.appendLine("РАЗОМ: ${totalAmount.toPlainString()} грн")

        notes?.takeIf { it.isNotBlank() }?.let {
            sb.appendLine("-".repeat(32))
            sb.appendLine("Примітка: $it")
        }

        sb.appendLine("=".repeat(32))
        sb.appendLine("Дякуємо за співпрацю!")

        return sb.toString()
    }
}
