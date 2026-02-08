package com.zagot.zagotplus.hardware.scales.protocol

import com.zagot.zagotplus.hardware.scales.WeightReading

enum class Parity { NONE, ODD, EVEN }

/**
 * Protocol parser interface for scales communication.
 * Different scales may use different serial protocols (Mettler Toledo, Dniprovеs, CAS, etc.)
 */
interface ScalesProtocol {
    val name: String
    val baudRate: Int
    val dataBits: Int
    val stopBits: Int
    val parity: Parity

    /**
     * Parse raw bytes into a WeightReading.
     * Returns null if data is incomplete or invalid.
     */
    fun parseReading(data: ByteArray): WeightReading?

    /**
     * Build tare command bytes.
     */
    fun buildTareCommand(): ByteArray

    /**
     * Build weight request command bytes.
     * Returns null if scales auto-send weight (no request needed).
     */
    fun buildRequestCommand(): ByteArray?

    /**
     * Check if buffer contains a complete message.
     * Used for framing in stream parsing.
     */
    fun isCompleteMessage(buffer: ByteArray): Boolean
}
