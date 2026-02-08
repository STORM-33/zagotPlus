package com.zagot.zagotplus.hardware.scales.protocol

import com.zagot.zagotplus.hardware.scales.WeightReading
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Parser for Mettler Toledo MT-SICS protocol.
 * 
 * Common format: "S S    12.34 kg\r\n" (stable) or "S D    12.34 kg\r\n" (dynamic)
 * 
 * Status codes:
 * - S = stable reading
 * - D = dynamic (weight still changing)
 * - + = overload
 * - - = underload
 * 
 * Commands:
 * - S\r\n = request stable weight
 * - SI\r\n = request immediate weight
 * - T\r\n = tare
 * - Z\r\n = zero
 */
class MettlerToledoProtocol : ScalesProtocol {
    override val name = "Mettler Toledo (MT-SICS)"
    override val baudRate = 4800
    override val dataBits = 8
    override val stopBits = 1
    override val parity = Parity.EVEN

    // Pattern: S [S|D] [+|-]?[space]*[digits].[digits] [kg|g]
    private val weightPattern = Regex(
        """S\s+([SD])\s+([+-]?\s*\d+\.?\d*)\s*(kg|g)""",
        RegexOption.IGNORE_CASE
    )

    override fun parseReading(data: ByteArray): WeightReading? {
        val text = data.toString(Charsets.US_ASCII).trim()

        // Check for error responses
        if (text.startsWith("ES") || text.startsWith("EL")) {
            return null  // Syntax error or logical error
        }

        val match = weightPattern.find(text) ?: return null

        val (status, valueStr, unit) = match.destructured
        val cleanValue = valueStr.replace("\\s".toRegex(), "")

        val value = cleanValue.toBigDecimalOrNull() ?: return null

        val weightKg = when (unit.lowercase()) {
            "kg" -> value
            "g" -> value.divide(BigDecimal(1000), 3, RoundingMode.HALF_UP)
            else -> return null
        }

        return WeightReading(
            weightKg = weightKg.setScale(3, RoundingMode.HALF_UP),
            isStable = status.uppercase() == "S",
            timestamp = Instant.now(),
            raw = text
        )
    }

    override fun buildTareCommand(): ByteArray = "T\r\n".toByteArray(Charsets.US_ASCII)

    override fun buildRequestCommand(): ByteArray = "S\r\n".toByteArray(Charsets.US_ASCII)

    override fun isCompleteMessage(buffer: ByteArray): Boolean {
        val text = buffer.toString(Charsets.US_ASCII)
        return text.endsWith("\r\n") || text.endsWith("\n")
    }
}
