package com.zagot.zagotplus.data.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import com.zagot.zagotplus.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
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
        private const val MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024 // 5MB
    }

    /**
     * Upload an image from a content:// or file:// URI to Supabase Storage.
     * Returns a Result with the public URL of the uploaded image or error details.
     */
    suspend fun uploadImageSafe(localUri: String): StorageResult<String> {
        return try {
            val uri = Uri.parse(localUri)
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return StorageResult.Error("Failed to open input stream for URI: $localUri")
            
            val bytes = inputStream.use { it.readBytes() }
            
            if (bytes.size > MAX_IMAGE_SIZE_BYTES) {
                return StorageResult.Error("Image too large: ${bytes.size} bytes (max ${MAX_IMAGE_SIZE_BYTES})")
            }

            val fileName = "${UUID.randomUUID()}.jpg"
            val bucket = supabaseClient.storage[BUCKET_NAME]
            
            bucket.upload(fileName, bytes)

            val publicUrl = "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/$BUCKET_NAME/$fileName"
            Log.d(TAG, "Successfully uploaded image: $fileName")
            StorageResult.Success(publicUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload image from $localUri", e)
            StorageResult.Error("Upload failed: ${e.message}", e)
        }
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
