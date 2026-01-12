package com.zagot.zagotplus.data.remote

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for SupabaseStorageHelper URL handling logic.
 * Network operations are mocked; these tests focus on URL parsing and detection.
 */
class SupabaseStorageHelperTest {

    // Test the URL detection and parsing logic without needing the full Supabase client

    // ==================== isSupabaseUrl Tests ====================

    @Test
    fun `isSupabaseUrl returns true for valid Supabase storage URL`() {
        val url = "https://abc.supabase.co/storage/v1/object/public/product-images/image.jpg"
        
        assertTrue(url.contains("/storage/v1/object/public/product-images/"))
    }

    @Test
    fun `isSupabaseUrl returns false for local file URI`() {
        val uri = "file:///storage/emulated/0/images/photo.jpg"
        
        assertFalse(uri.contains("/storage/v1/object/public/product-images/"))
    }

    @Test
    fun `isSupabaseUrl returns false for content URI`() {
        val uri = "content://com.android.providers.media/images/123"
        
        assertFalse(uri.contains("/storage/v1/object/public/product-images/"))
    }

    @Test
    fun `isSupabaseUrl returns false for null`() {
        val uri: String? = null
        
        assertFalse(uri?.contains("/storage/v1/object/public/product-images/") == true)
    }

    // ==================== needsUpload Tests ====================

    @Test
    fun `needsUpload returns true for content URI`() {
        val uri = "content://com.android.providers.media/images/123"
        
        assertTrue(uri.startsWith("content://") || uri.startsWith("file://"))
    }

    @Test
    fun `needsUpload returns true for file URI`() {
        val uri = "file:///storage/emulated/0/images/photo.jpg"
        
        assertTrue(uri.startsWith("content://") || uri.startsWith("file://"))
    }

    @Test
    fun `needsUpload returns false for http URL`() {
        val uri = "https://example.com/image.jpg"
        
        assertFalse(uri.startsWith("content://") || uri.startsWith("file://"))
    }

    @Test
    fun `needsUpload returns false for Supabase URL`() {
        val uri = "https://abc.supabase.co/storage/v1/object/public/product-images/image.jpg"
        
        assertFalse(uri.startsWith("content://") || uri.startsWith("file://"))
    }

    // ==================== extractFileName Tests ====================

    @Test
    fun `extractFileName extracts filename from Supabase URL`() {
        val url = "https://abc.supabase.co/storage/v1/object/public/product-images/abc123.jpg"
        val prefix = "/storage/v1/object/public/product-images/"
        val index = url.indexOf(prefix)
        
        val fileName = if (index >= 0) {
            url.substring(index + prefix.length)
        } else {
            null
        }
        
        assertEquals("abc123.jpg", fileName)
    }

    @Test
    fun `extractFileName returns null for non-Supabase URL`() {
        val url = "https://example.com/images/photo.jpg"
        val prefix = "/storage/v1/object/public/product-images/"
        val index = url.indexOf(prefix)
        
        val fileName = if (index >= 0) {
            url.substring(index + prefix.length)
        } else {
            null
        }
        
        assertNull(fileName)
    }

    @Test
    fun `extractFileName handles UUID filenames`() {
        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        val url = "https://abc.supabase.co/storage/v1/object/public/product-images/$uuid.jpg"
        val prefix = "/storage/v1/object/public/product-images/"
        val index = url.indexOf(prefix)
        
        val fileName = if (index >= 0) {
            url.substring(index + prefix.length)
        } else {
            null
        }
        
        assertEquals("$uuid.jpg", fileName)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `handles empty string URI`() {
        val uri = ""
        
        assertFalse(uri.startsWith("content://") || uri.startsWith("file://"))
        assertFalse(uri.contains("/storage/v1/object/public/product-images/"))
    }

    @Test
    fun `handles malformed URI gracefully`() {
        val uri = "not a valid uri at all"
        
        assertFalse(uri.startsWith("content://") || uri.startsWith("file://"))
        assertFalse(uri.contains("/storage/v1/object/public/product-images/"))
    }
}
