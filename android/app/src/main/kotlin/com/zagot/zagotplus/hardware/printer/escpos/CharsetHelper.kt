package com.zagot.zagotplus.hardware.printer.escpos

/**
 * Helper for encoding Ukrainian text to CP866 charset.
 * 
 * CP866 mapping for Ukrainian Cyrillic:
 * - А-Я (uppercase): 0x80-0x9F (first 16), 0xA0-0xAF (next 16)
 * - а-п (lowercase first 16): 0xA0-0xAF  
 * - р-я (lowercase last 16): 0xE0-0xEF
 * - Ukrainian-specific: Є, І, Ї, Ґ need special handling
 */
object CharsetHelper {
    
    // CP866 codepage mapping for Cyrillic
    // This is the standard MS-DOS Cyrillic codepage, commonly used in thermal printers
    
    /**
     * Convert Ukrainian/Russian text to CP866 bytes.
     * Falls back to '?' for unmappable characters.
     */
    fun encodeToCP866(text: String): ByteArray {
        return text.map { char ->
            cp866Map[char] ?: when {
                char.code < 128 -> char.code.toByte()  // ASCII passthrough
                else -> '?'.code.toByte()               // Unknown char
            }
        }.toByteArray()
    }
    
    /**
     * Check if a string can be fully encoded in CP866.
     */
    fun canEncode(text: String): Boolean {
        return text.all { char -> 
            char.code < 128 || cp866Map.containsKey(char)
        }
    }
    
    // CP866 mapping for Cyrillic characters
    private val cp866Map: Map<Char, Byte> = buildMap {
        // Uppercase А-Я (0x80-0x9F)
        put('А', 0x80.toByte())
        put('Б', 0x81.toByte())
        put('В', 0x82.toByte())
        put('Г', 0x83.toByte())
        put('Д', 0x84.toByte())
        put('Е', 0x85.toByte())
        put('Ж', 0x86.toByte())
        put('З', 0x87.toByte())
        put('И', 0x88.toByte())
        put('Й', 0x89.toByte())
        put('К', 0x8A.toByte())
        put('Л', 0x8B.toByte())
        put('М', 0x8C.toByte())
        put('Н', 0x8D.toByte())
        put('О', 0x8E.toByte())
        put('П', 0x8F.toByte())
        put('Р', 0x90.toByte())
        put('С', 0x91.toByte())
        put('Т', 0x92.toByte())
        put('У', 0x93.toByte())
        put('Ф', 0x94.toByte())
        put('Х', 0x95.toByte())
        put('Ц', 0x96.toByte())
        put('Ч', 0x97.toByte())
        put('Ш', 0x98.toByte())
        put('Щ', 0x99.toByte())
        put('Ъ', 0x9A.toByte())
        put('Ы', 0x9B.toByte())
        put('Ь', 0x9C.toByte())
        put('Э', 0x9D.toByte())
        put('Ю', 0x9E.toByte())
        put('Я', 0x9F.toByte())
        
        // Lowercase а-п (0xA0-0xAF)
        put('а', 0xA0.toByte())
        put('б', 0xA1.toByte())
        put('в', 0xA2.toByte())
        put('г', 0xA3.toByte())
        put('д', 0xA4.toByte())
        put('е', 0xA5.toByte())
        put('ж', 0xA6.toByte())
        put('з', 0xA7.toByte())
        put('и', 0xA8.toByte())
        put('й', 0xA9.toByte())
        put('к', 0xAA.toByte())
        put('л', 0xAB.toByte())
        put('м', 0xAC.toByte())
        put('н', 0xAD.toByte())
        put('о', 0xAE.toByte())
        put('п', 0xAF.toByte())
        
        // Lowercase р-я (0xE0-0xEF)
        put('р', 0xE0.toByte())
        put('с', 0xE1.toByte())
        put('т', 0xE2.toByte())
        put('у', 0xE3.toByte())
        put('ф', 0xE4.toByte())
        put('х', 0xE5.toByte())
        put('ц', 0xE6.toByte())
        put('ч', 0xE7.toByte())
        put('ш', 0xE8.toByte())
        put('щ', 0xE9.toByte())
        put('ъ', 0xEA.toByte())
        put('ы', 0xEB.toByte())
        put('ь', 0xEC.toByte())
        put('э', 0xED.toByte())
        put('ю', 0xEE.toByte())
        put('я', 0xEF.toByte())
        
        // Ukrainian-specific letters (using extended positions)
        // Note: CP866 has Є at 0xF2, І at 0xF6, Ї at 0xF7, ї at 0xF7
        put('Є', 0xF2.toByte())  // Ukrainian Ye
        put('є', 0xF3.toByte())  // lowercase
        put('І', 0xF6.toByte())  // Ukrainian I
        put('і', 0xF7.toByte())  // lowercase
        put('Ї', 0xF8.toByte())  // Ukrainian Yi
        put('ї', 0xF9.toByte())  // lowercase
        put('Ґ', 0xF4.toByte())  // Ukrainian Ghe with upturn
        put('ґ', 0xF5.toByte())  // lowercase
        
        // Yo (Russian)
        put('Ё', 0xF0.toByte())
        put('ё', 0xF1.toByte())
        
        // Common symbols
        put('№', 0xFC.toByte())  // Number sign
        put('₴', '?'.code.toByte())  // Hryvnia - not in CP866, use UAH text instead
    }
}
