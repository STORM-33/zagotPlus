package com.zagot.zagotplus.domain.validation

import org.junit.Assert.*
import org.junit.Test

class InputValidationTest {

    // === sanitizeNotes tests ===

    @Test
    fun `sanitizeNotes returns null for null input`() {
        assertNull(InputValidation.sanitizeNotes(null))
    }

    @Test
    fun `sanitizeNotes returns null for blank input`() {
        assertNull(InputValidation.sanitizeNotes(""))
        assertNull(InputValidation.sanitizeNotes("   "))
        assertNull(InputValidation.sanitizeNotes("\t\n"))
    }

    @Test
    fun `sanitizeNotes trims whitespace`() {
        assertEquals("hello", InputValidation.sanitizeNotes("  hello  "))
        assertEquals("hello world", InputValidation.sanitizeNotes("\thello world\n"))
    }

    @Test
    fun `sanitizeNotes limits length to MAX_NOTES_LENGTH`() {
        val longInput = "a".repeat(InputValidation.MAX_NOTES_LENGTH + 100)
        val result = InputValidation.sanitizeNotes(longInput)
        
        assertNotNull(result)
        assertEquals(InputValidation.MAX_NOTES_LENGTH, result!!.length)
    }

    @Test
    fun `sanitizeNotes preserves valid input`() {
        val validNote = "Примітка українською мовою 123"
        assertEquals(validNote, InputValidation.sanitizeNotes(validNote))
    }

    // === sanitizeProductName tests ===

    @Test
    fun `sanitizeProductName trims whitespace`() {
        assertEquals("Горіх", InputValidation.sanitizeProductName("  Горіх  "))
    }

    @Test
    fun `sanitizeProductName removes control characters`() {
        assertEquals("TestName", InputValidation.sanitizeProductName("Test\u0000Name"))
        assertEquals("TestName", InputValidation.sanitizeProductName("Test\u001FName"))
    }

    @Test
    fun `sanitizeProductName limits length to MAX_PRODUCT_NAME_LENGTH`() {
        val longInput = "a".repeat(InputValidation.MAX_PRODUCT_NAME_LENGTH + 50)
        val result = InputValidation.sanitizeProductName(longInput)
        
        assertEquals(InputValidation.MAX_PRODUCT_NAME_LENGTH, result.length)
    }

    @Test
    fun `sanitizeProductName preserves valid Ukrainian names`() {
        val validName = "Горіх білий"
        assertEquals(validName, InputValidation.sanitizeProductName(validName))
    }

    // === sanitizeCategoryName tests ===

    @Test
    fun `sanitizeCategoryName trims whitespace`() {
        assertEquals("Паливо", InputValidation.sanitizeCategoryName("  Паливо  "))
    }

    @Test
    fun `sanitizeCategoryName removes control characters`() {
        assertEquals("Category", InputValidation.sanitizeCategoryName("Cat\u0007egory"))
    }

    @Test
    fun `sanitizeCategoryName limits length to MAX_CATEGORY_NAME_LENGTH`() {
        val longInput = "b".repeat(InputValidation.MAX_CATEGORY_NAME_LENGTH + 20)
        val result = InputValidation.sanitizeCategoryName(longInput)
        
        assertEquals(InputValidation.MAX_CATEGORY_NAME_LENGTH, result.length)
    }

    // === containsSuspiciousPatterns tests ===

    @Test
    fun `containsSuspiciousPatterns detects script tags`() {
        assertTrue(InputValidation.containsSuspiciousPatterns("<script>alert('xss')</script>"))
        assertTrue(InputValidation.containsSuspiciousPatterns("<SCRIPT>"))
    }

    @Test
    fun `containsSuspiciousPatterns detects javascript protocol`() {
        assertTrue(InputValidation.containsSuspiciousPatterns("javascript:void(0)"))
        assertTrue(InputValidation.containsSuspiciousPatterns("JAVASCRIPT:alert(1)"))
    }

    @Test
    fun `containsSuspiciousPatterns detects event handlers`() {
        assertTrue(InputValidation.containsSuspiciousPatterns("onerror=alert(1)"))
        assertTrue(InputValidation.containsSuspiciousPatterns("onclick=doSomething()"))
        assertTrue(InputValidation.containsSuspiciousPatterns("onload=init()"))
    }

    @Test
    fun `containsSuspiciousPatterns returns false for normal text`() {
        assertFalse(InputValidation.containsSuspiciousPatterns("Звичайний текст"))
        assertFalse(InputValidation.containsSuspiciousPatterns("Normal product name"))
        assertFalse(InputValidation.containsSuspiciousPatterns("Price: 45.00 UAH"))
    }

    @Test
    fun `containsSuspiciousPatterns returns false for empty string`() {
        assertFalse(InputValidation.containsSuspiciousPatterns(""))
    }

    // === Edge cases ===

    @Test
    fun `sanitizeNotes handles edge case of exactly MAX_NOTES_LENGTH`() {
        val exactLength = "a".repeat(InputValidation.MAX_NOTES_LENGTH)
        assertEquals(exactLength, InputValidation.sanitizeNotes(exactLength))
    }

    @Test
    fun `sanitizeProductName handles empty string`() {
        assertEquals("", InputValidation.sanitizeProductName(""))
    }

    @Test
    fun `sanitizeCategoryName handles only control characters`() {
        assertEquals("", InputValidation.sanitizeCategoryName("\u0000\u0001\u0002"))
    }
}
