package com.zagot.zagotplus.hardware.printer.escpos

/**
 * ESC/POS command constants for thermal printers.
 */
object EscPosCommands {
    // Printer control
    val INIT = byteArrayOf(0x1B, 0x40)                    // ESC @ - Initialize printer
    val CANCEL_CHINESE_MODE = byteArrayOf(0x1C, 0x2E)     // FS . - Cancel Chinese character mode (critical for Cyrillic!)
    val SELECT_CP866 = byteArrayOf(0x1B, 0x74, 0x11)      // ESC t 17 - Select CP866 codepage

    // Text alignment
    val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)        // ESC a 0
    val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)      // ESC a 1
    val ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)       // ESC a 2

    // Text style
    val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)           // ESC E 1
    val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)          // ESC E 0
    val UNDERLINE_ON = byteArrayOf(0x1B, 0x2D, 0x01)      // ESC - 1
    val UNDERLINE_OFF = byteArrayOf(0x1B, 0x2D, 0x00)     // ESC - 0

    // Text size (GS !)
    val SIZE_NORMAL = byteArrayOf(0x1D, 0x21, 0x00)       // Normal size
    val SIZE_DOUBLE_HEIGHT = byteArrayOf(0x1D, 0x21, 0x01) // Double height
    val SIZE_DOUBLE_WIDTH = byteArrayOf(0x1D, 0x21, 0x10)  // Double width
    val SIZE_DOUBLE = byteArrayOf(0x1D, 0x21, 0x11)        // Double height and width

    // Legacy text mode (ESC !)
    val MODE_NORMAL = byteArrayOf(0x1B, 0x21, 0x00)
    val MODE_DOUBLE_HEIGHT = byteArrayOf(0x1B, 0x21, 0x10)
    val MODE_DOUBLE_WIDTH = byteArrayOf(0x1B, 0x21, 0x20)
    val MODE_DOUBLE_SIZE = byteArrayOf(0x1B, 0x21, 0x30)

    // Paper control
    val LINE_FEED = byteArrayOf(0x0A)                      // LF
    fun feedLines(n: Int) = byteArrayOf(0x1B, 0x64, n.toByte()) // ESC d n

    // Cut paper
    val CUT_FULL = byteArrayOf(0x1D, 0x56, 0x00)          // GS V 0 - Full cut
    val CUT_PARTIAL = byteArrayOf(0x1D, 0x56, 0x01)       // GS V 1 - Partial cut

    // Horizontal line/separator
    const val PAPER_WIDTH_CHARS = 32  // 58mm paper = 32 characters in normal font
}
