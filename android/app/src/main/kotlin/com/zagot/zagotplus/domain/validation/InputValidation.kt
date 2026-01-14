package com.zagot.zagotplus.domain.validation

/**
 * Input validation utilities for sanitizing user input.
 */
object InputValidation {
    
    /** Maximum length for notes field */
    const val MAX_NOTES_LENGTH = 500
    
    /** Maximum length for product names */
    const val MAX_PRODUCT_NAME_LENGTH = 100
    
    /** Maximum length for category names */
    const val MAX_CATEGORY_NAME_LENGTH = 50
    
    /**
     * Sanitize notes input: trim whitespace and limit length.
     * Returns null if the sanitized result is blank.
     */
    fun sanitizeNotes(input: String?): String? {
        if (input == null) return null
        val sanitized = input.trim().take(MAX_NOTES_LENGTH)
        return sanitized.takeIf { it.isNotBlank() }
    }
    
    /**
     * Sanitize product name: trim whitespace, limit length, remove control characters.
     */
    fun sanitizeProductName(input: String): String {
        return input
            .trim()
            .replace(Regex("[\\p{Cntrl}]"), "") // Remove control characters
            .take(MAX_PRODUCT_NAME_LENGTH)
    }
    
    /**
     * Sanitize category name: trim whitespace, limit length.
     */
    fun sanitizeCategoryName(input: String): String {
        return input
            .trim()
            .replace(Regex("[\\p{Cntrl}]"), "")
            .take(MAX_CATEGORY_NAME_LENGTH)
    }
    
    /**
     * Check if a string contains potential XSS patterns.
     * Note: This is a basic check, server-side validation should be the primary defense.
     */
    fun containsSuspiciousPatterns(input: String): Boolean {
        val patterns = listOf(
            "<script",
            "javascript:",
            "onerror=",
            "onclick=",
            "onload="
        )
        val lowered = input.lowercase()
        return patterns.any { lowered.contains(it) }
    }
}
