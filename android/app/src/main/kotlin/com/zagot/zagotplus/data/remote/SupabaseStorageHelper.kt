package com.zagot.zagotplus.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.zagot.zagotplus.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result wrapper for storage operations.
 */
sealed class StorageResult<T> {
    data class Success<T>(val data: T) : StorageResult<T>()
    data class Error<T>(val message: String, val exception: Exception? = null) : StorageResult<T>()
}

/**
 * Helper class to upload and manage images in Supabase Storage.
 */
@Singleton
class SupabaseStorageHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabaseClient: SupabaseClient
) {
    companion object {
        private const val TAG = "SupabaseStorageHelper"
        private const val BUCKET_NAME = "product-images"
        private const val MAX_RAW_IMAGE_SIZE_BYTES = 5 * 1024 * 1024 // 5MB before compression
        private const val MAX_COMPRESSED_SIZE_KB = 500 // 500KB after compression
        private const val MAX_IMAGE_WIDTH = 1080
    }

    /**
     * Upload an image from a content:// or file:// URI to Supabase Storage.
     * Automatically compresses large images before upload to prevent slow network issues.
     * Returns a Result with the public URL of the uploaded image or error details.
     */
    suspend fun uploadImageSafe(localUri: String): StorageResult<String> {
        return try {
            val uri = Uri.parse(localUri)
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return StorageResult.Error("Failed to open input stream for URI: $localUri")
            
            val rawBytes = inputStream.use { it.readBytes() }
            
            if (rawBytes.size > MAX_RAW_IMAGE_SIZE_BYTES) {
                return StorageResult.Error("Image too large: ${rawBytes.size} bytes (max ${MAX_RAW_IMAGE_SIZE_BYTES})")
            }

            // Compress the image before upload
            val compressedBytes = compressImage(rawBytes, MAX_IMAGE_WIDTH, MAX_COMPRESSED_SIZE_KB)
            Log.d(TAG, "Compressed image from ${rawBytes.size} to ${compressedBytes.size} bytes")

            val fileName = "${UUID.randomUUID()}.jpg"
            val bucket = supabaseClient.storage[BUCKET_NAME]
            
            bucket.upload(fileName, compressedBytes)

            val publicUrl = "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/$BUCKET_NAME/$fileName"
            Log.d(TAG, "Successfully uploaded image: $fileName")
            StorageResult.Success(publicUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload image from $localUri", e)
            StorageResult.Error("Upload failed: ${e.message}", e)
        }
    }

    /**
     * Compresses an image to fit within size and dimension constraints.
     * Uses progressive quality reduction to meet size target.
     */
    private fun compressImage(bytes: ByteArray, maxWidth: Int, maxSizeKB: Int): ByteArray {
        // Decode with inJustDecodeBounds to get dimensions first
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        
        // Calculate sample size for downscaling
        val originalWidth = options.outWidth
        val originalHeight = options.outHeight
        var sampleSize = 1
        while (originalWidth / sampleSize > maxWidth * 2 || originalHeight / sampleSize > maxWidth * 2) {
            sampleSize *= 2
        }
        
        // Decode with sample size
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: return bytes // Return original if decode fails
        
        // Scale down if still too large
        val scaledBitmap = if (bitmap.width > maxWidth) {
            val ratio = maxWidth.toFloat() / bitmap.width
            val newHeight = (bitmap.height * ratio).toInt()
            Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true).also {
                if (it != bitmap) bitmap.recycle()
            }
        } else {
            bitmap
        }
        
        // Progressive compression to meet size target
        var quality = 90
        var output: ByteArray
        do {
            val stream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            output = stream.toByteArray()
            quality -= 10
        } while (output.size > maxSizeKB * 1024 && quality > 20)
        
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }
        
        return output
    }

    /**
     * Upload an image from a content:// or file:// URI to Supabase Storage.
     * Returns the public URL of the uploaded image, or null on failure.
     * 
     * @deprecated Use uploadImageSafe for better error handling
     */
    @Deprecated("Use uploadImageSafe for better error handling", ReplaceWith("uploadImageSafe(localUri)"))
    suspend fun uploadImage(localUri: String): String? {
        return when (val result = uploadImageSafe(localUri)) {
            is StorageResult.Success -> result.data
            is StorageResult.Error -> null
        }
    }

    /**
     * Delete an image from Supabase Storage by its public URL.
     */
    suspend fun deleteImage(publicUrl: String): Boolean {
        return try {
            val fileName = extractFileName(publicUrl) ?: return false
            val bucket = supabaseClient.storage[BUCKET_NAME]
            bucket.delete(fileName)
            Log.d(TAG, "Successfully deleted image: $fileName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete image: $publicUrl", e)
            false
        }
    }

    /**
     * Check if the URI is a Supabase Storage URL.
     */
    fun isSupabaseUrl(uri: String?): Boolean {
        return uri?.contains("/storage/v1/object/public/$BUCKET_NAME/") == true
    }

    /**
     * Check if the URI is a local file or content URI that needs uploading.
     */
    fun needsUpload(uri: String?): Boolean {
        if (uri == null) return false
        return uri.startsWith("content://") || uri.startsWith("file://")
    }

    private fun extractFileName(publicUrl: String): String? {
        val prefix = "/storage/v1/object/public/$BUCKET_NAME/"
        val index = publicUrl.indexOf(prefix)
        return if (index >= 0) {
            publicUrl.substring(index + prefix.length)
        } else {
            null
        }
    }
}
