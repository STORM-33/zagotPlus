package com.zagot.zagotplus.hardware.scales.protocol

import com.zagot.zagotplus.hardware.scales.WeightReading
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Protocol parser for Dniprovesy (Дніпроваги) VPD series indicators.
 *
 * Format: "=<reversed digits with decimal><sign>"
 * - '=' is the header byte
 * - 6 data characters: weight digits including decimal point, sent LSB-first (reversed)
 * - 1 sign character: '0' = positive, '-' = negative
 *
 * Example: display shows -500.00 kg → serial output is "=00.005-"
 *   - Sign: '-' (negative)
 *   - Data: "00.005" → reversed → "500.00"
 *   - Result: -500.00 kg
 *
 * Example: display shows 0.450 kg → serial output is "=54.0000"
 *   - Sign: '0' (positive)
 *   - Data: "54.000" → reversed → "000.45"
 *   - Result: 0.45 kg
 *
 * Messages are 8 bytes, sent continuously with no line terminator.
 * Framing is done by detecting '=' as message start marker.
 */
class DniprovesyProtocol : ScalesProtocol {
    override val name = "Dniprovesy (Дніпроваги)"
    override val baudRate = 9600
    override val dataBits = 8
    override val stopBits = 1
    override val parity = Parity.NONE

    override fun parseReading(data: ByteArray): WeightReading? {
        val text = data.toString(Charsets.US_ASCII).trim()
        if (text.isEmpty()) return null

        // Strip '=' prefix if present
        val payload = if (text.startsWith('=')) text.substring(1) else text
        if (payload.length < 2) return null

        // Last character is sign: '0' = positive, '-' = negative
        val signChar = payload.last()
        if (signChar != '0' && signChar != '-') return null

        val isNegative = signChar == '-'
        val reversedData = payload.substring(0, payload.length - 1)

        // Reversed data must contain digits and at most one decimal point
        if (!reversedData.all { it.isDigit() || it == '.' }) return null
        if (reversedData.count { it == '.' } > 1) return null

        // Reverse to get actual weight string
        val weightStr = reversedData.reversed()

        val value = weightStr.toBigDecimalOrNull() ?: return null
        val weightKg = if (isNegative) value.negate() else value

        return WeightReading(
            weightKg = weightKg.setScale(3, RoundingMode.HALF_UP),
            isStable = true, // Protocol has no stability indicator
            timestamp = Instant.now(),
            raw = text
        )
    }

    override fun buildTareCommand(): ByteArray = "T\r\n".toByteArray(Charsets.US_ASCII)

    override fun buildRequestCommand(): ByteArray? = null // Continuous output, no request needed

    override fun isCompleteMessage(buffer: ByteArray): Boolean {
        val text = buffer.toString(Charsets.US_ASCII)
        // Message is complete when we have '=' + at least 2 chars (data + sign)
        // and ends with sign character ('0' or '-')
        if (!text.startsWith('=') || text.length < 3) return false
        val lastChar = text.last()
        return lastChar == '0' || lastChar == '-'
    }
}
