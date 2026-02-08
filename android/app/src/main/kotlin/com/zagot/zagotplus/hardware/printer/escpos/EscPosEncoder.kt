package com.zagot.zagotplus.hardware.printer.escpos

/**
 * Fluent builder for ESC/POS printer commands.
 * 
 * Usage:
 * ```kotlin
 * val data = EscPosEncoder()
 *     .initUkrainian()
 *     .alignCenter()
 *     .boldOn()
 *     .text("ЗАГОТ+")
 *     .boldOff()
 *     .newLine()
 *     .alignLeft()
 *     .text("Яблука: 10.5 кг")
 *     .feedLines(3)
 *     .cut()
 *     .toByteArray()
 * ```
 */
class EscPosEncoder {
    private val buffer = mutableListOf<Byte>()

    /** Initialize printer */
    fun init(): EscPosEncoder {
        buffer.addAll(EscPosCommands.INIT.toList())
        return this
    }

    /** Cancel Chinese character mode (CRITICAL for Cyrillic!) */
    fun cancelChineseMode(): EscPosEncoder {
        buffer.addAll(EscPosCommands.CANCEL_CHINESE_MODE.toList())
        return this
    }

    /** Select CP866 codepage for Cyrillic */
    fun selectCyrillic(): EscPosEncoder {
        buffer.addAll(EscPosCommands.SELECT_CP866.toList())
        return this
    }

    /** Standard initialization for Ukrainian receipts */
    fun initUkrainian(): EscPosEncoder {
        return init().cancelChineseMode().selectCyrillic()
    }

    // Alignment
    
    fun alignLeft(): EscPosEncoder {
        buffer.addAll(EscPosCommands.ALIGN_LEFT.toList())
        return this
    }

    fun alignCenter(): EscPosEncoder {
        buffer.addAll(EscPosCommands.ALIGN_CENTER.toList())
        return this
    }

    fun alignRight(): EscPosEncoder {
        buffer.addAll(EscPosCommands.ALIGN_RIGHT.toList())
        return this
    }

    // Text style

    fun boldOn(): EscPosEncoder {
        buffer.addAll(EscPosCommands.BOLD_ON.toList())
        return this
    }

    fun boldOff(): EscPosEncoder {
        buffer.addAll(EscPosCommands.BOLD_OFF.toList())
        return this
    }

    fun underlineOn(): EscPosEncoder {
        buffer.addAll(EscPosCommands.UNDERLINE_ON.toList())
        return this
    }

    fun underlineOff(): EscPosEncoder {
        buffer.addAll(EscPosCommands.UNDERLINE_OFF.toList())
        return this
    }

    // Text size

    fun normalSize(): EscPosEncoder {
        buffer.addAll(EscPosCommands.SIZE_NORMAL.toList())
        return this
    }

    fun doubleHeight(): EscPosEncoder {
        buffer.addAll(EscPosCommands.SIZE_DOUBLE_HEIGHT.toList())
        return this
    }

    fun doubleWidth(): EscPosEncoder {
        buffer.addAll(EscPosCommands.SIZE_DOUBLE_WIDTH.toList())
        return this
    }

    fun doubleSize(): EscPosEncoder {
        buffer.addAll(EscPosCommands.SIZE_DOUBLE.toList())
        return this
    }

    // Text output

    /** Add text (auto-encodes to CP866 for Cyrillic) */
    fun text(content: String): EscPosEncoder {
        buffer.addAll(CharsetHelper.encodeToCP866(content).toList())
        return this
    }

    /** Add raw ASCII text without CP866 encoding */
    fun rawText(content: String): EscPosEncoder {
        buffer.addAll(content.toByteArray(Charsets.US_ASCII).toList())
        return this
    }

    /** Add new line */
    fun newLine(): EscPosEncoder {
        buffer.addAll(EscPosCommands.LINE_FEED.toList())
        return this
    }

    /** Add text followed by new line */
    fun line(content: String): EscPosEncoder {
        return text(content).newLine()
    }

    /** Add empty line */
    fun emptyLine(): EscPosEncoder {
        return newLine()
    }

    // Paper control

    /** Feed N lines */
    fun feedLines(n: Int): EscPosEncoder {
        buffer.addAll(EscPosCommands.feedLines(n).toList())
        return this
    }

    /** Full paper cut */
    fun cut(): EscPosEncoder {
        buffer.addAll(EscPosCommands.CUT_FULL.toList())
        return this
    }

    /** Partial paper cut */
    fun partialCut(): EscPosEncoder {
        buffer.addAll(EscPosCommands.CUT_PARTIAL.toList())
        return this
    }

    // Formatting helpers

    /** Add horizontal separator line */
    fun separator(char: Char = '-'): EscPosEncoder {
        val line = char.toString().repeat(EscPosCommands.PAPER_WIDTH_CHARS)
        return line(line)
    }

    /** Add left-right aligned row (e.g., "Item         10.00") */
    fun row(left: String, right: String): EscPosEncoder {
        val spacing = EscPosCommands.PAPER_WIDTH_CHARS - left.length - right.length
        val paddedLine = if (spacing > 0) {
            left + " ".repeat(spacing) + right
        } else {
            // Truncate left side if too long
            left.take(EscPosCommands.PAPER_WIDTH_CHARS - right.length - 1) + " " + right
        }
        return line(paddedLine)
    }

    /** Center text with padding */
    fun centeredLine(content: String): EscPosEncoder {
        val padding = (EscPosCommands.PAPER_WIDTH_CHARS - content.length) / 2
        val padded = if (padding > 0) {
            " ".repeat(padding) + content
        } else {
            content.take(EscPosCommands.PAPER_WIDTH_CHARS)
        }
        return line(padded)
    }

    /** Add raw bytes directly */
    fun raw(bytes: ByteArray): EscPosEncoder {
        buffer.addAll(bytes.toList())
        return this
    }

    /** Get the final byte array */
    fun toByteArray(): ByteArray {
        return buffer.toByteArray()
    }

    /** Clear the buffer */
    fun clear(): EscPosEncoder {
        buffer.clear()
        return this
    }
}
