package com.zagot.zagotplus.hardware.scales.protocol

import com.zagot.zagotplus.hardware.scales.WeightReading
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Parser for Dniprovеs custom BCD protocol (ВТД-РС).
 * 
 * Commands (prefix with 0x00 0x00):
 * - 0x01 = Tare
 * - 0x03 = Request weight
 * 
 * Response: 6 bytes BCD weight (W1W2W3W4W5W6), LSB first
 * Example: [0x05, 0x04, 0x03, 0x02, 0x01, 0x00] = 12345.0g = 12.345 kg
 * 
 * Note: Actual protocol may vary. This implementation is based on research.
 * Verify with actual hardware or official documentation from Dniprovеs.
 */
class DniprovesProtocol : ScalesProtocol {
    override val name = "Dniprovеs (BCD)"
    override val baudRate = 4800
    override val dataBits = 8
    override val stopBits = 1
    override val parity = Parity.EVEN

    companion object {
        private const val RESPONSE_LENGTH = 6
        private val TARE_COMMAND = byteArrayOf(0x00, 0x00, 0x01)
        private val REQUEST_COMMAND = byteArrayOf(0x00, 0x00, 0x03)
    }

    override fun parseReading(data: ByteArray): WeightReading? {
        if (data.size < RESPONSE_LENGTH) return null

        // Parse BCD bytes (LSB first)
        // Each byte is one BCD digit (0-9)
        var weightGrams = BigDecimal.ZERO
        var multiplier = BigDecimal.ONE

        for (i in 0 until RESPONSE_LENGTH) {
            val digit = data[i].toInt() and 0x0F  // Take lower nibble
            if (digit > 9) return null  // Invalid BCD

            weightGrams = weightGrams.add(BigDecimal(digit).multiply(multiplier))
            multiplier = multiplier.multiply(BigDecimal.TEN)
        }

        // Convert grams to kilograms (assuming weight is in 0.1g units)
        val weightKg = weightGrams.divide(BigDecimal(10000), 3, RoundingMode.HALF_UP)

        return WeightReading(
            weightKg = weightKg,
            isStable = true,  // Dniprovеs protocol doesn't indicate stability
            timestamp = Instant.now(),
            raw = data.joinToString(" ") { String.format("%02X", it) }
        )
    }

    override fun buildTareCommand(): ByteArray = TARE_COMMAND

    override fun buildRequestCommand(): ByteArray = REQUEST_COMMAND

    override fun isCompleteMessage(buffer: ByteArray): Boolean {
        return buffer.size >= RESPONSE_LENGTH
    }
}
