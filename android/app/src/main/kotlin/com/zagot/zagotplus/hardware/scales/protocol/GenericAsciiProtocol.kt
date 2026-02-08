package com.zagot.zagotplus.hardware.scales.protocol

import com.zagot.zagotplus.hardware.scales.WeightReading
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Generic ASCII protocol parser.
 * 
 * Tries to extract weight from common ASCII formats:
 * - "12.34 kg"
 * - "12.34"
 * - "   12.34 KG   "
 * - "ST,GS, 12.345, kg" (comma-separated)
 * 
 * Use this as a fallback when specific protocol is unknown.
 */
class GenericAsciiProtocol : ScalesProtocol {
    override val name = "Generic ASCII"
    override val baudRate = 9600
    override val dataBits = 8
    override val stopBits = 1
    override val parity = Parity.NONE

    // Match common weight patterns
    private val patterns = listOf(
        // Standard: "12.34 kg" or "12.34kg"
        Regex("""(-?\d+\.?\d*)\s*(kg|кг)""", RegexOption.IGNORE_CASE),
        // Grams: "12345 g" or "12345g"
        Regex("""(-?\d+\.?\d*)\s*(g|г)""", RegexOption.IGNORE_CASE),
        // No unit (assume kg)
        Regex("""(-?\d+\.\d{1,3})(?:\s|$)""")
    )

    // Stability indicators in various protocols
    private val stableIndicators = listOf("ST", "S", "STABLE", "NET")
    private val unstableIndicators = listOf("US", "D", "DYNAMIC", "MOTION")

    override fun parseReading(data: ByteArray): WeightReading? {
        val text = data.toString(Charsets.US_ASCII).trim()
        if (text.isEmpty()) return null

        // Check stability from status fields
        val isStable = stableIndicators.any { text.uppercase().contains(it) } ||
                !unstableIndicators.any { text.uppercase().contains(it) }

        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue

            val valueStr = match.groupValues[1]
            val unit = match.groupValues.getOrElse(2) { "kg" }

            val value = valueStr.toBigDecimalOrNull() ?: continue

            val weightKg = when (unit.lowercase()) {
                "kg", "кг" -> value
                "g", "г" -> value.divide(BigDecimal(1000), 3, RoundingMode.HALF_UP)
                else -> value  // Assume kg
            }

            return WeightReading(
                weightKg = weightKg.setScale(3, RoundingMode.HALF_UP),
                isStable = isStable,
                timestamp = Instant.now(),
                raw = text
            )
        }

        return null
    }

    override fun buildTareCommand(): ByteArray = "T\r\n".toByteArray(Charsets.US_ASCII)

    override fun buildRequestCommand(): ByteArray? = null  // Assume continuous output

    override fun isCompleteMessage(buffer: ByteArray): Boolean {
        val text = buffer.toString(Charsets.US_ASCII)
        return text.endsWith("\r\n") || text.endsWith("\n") || text.endsWith("\r")
    }
}
