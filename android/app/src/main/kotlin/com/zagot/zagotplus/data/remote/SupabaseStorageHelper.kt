package com.zagot.zagotplus.data.remote

import android.content.Context
import android.net.Uri
import com.zagot.zagotplus.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class to upload and manage images in Supabase Storage.
 */
@Singleton
class SupabaseStorageHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabaseClient: SupabaseClient
) {
    companion object {
        private const val BUCKET_NAME = "product-images"
    }

    /**
     * Upload an image from a content:// or file:// URI to Supabase Storage.
     * Returns the public URL of the uploaded image, or null on failure.
     */
    suspend fun uploadImage(localUri: String): String? {
        return try {
            val uri = Uri.parse(localUri)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = inputStream.readBytes()
            inputStream.close()

            val fileName = "${UUID.randomUUID()}.jpg"
            val bucket = supabaseClient.storage[BUCKET_NAME]
            
            bucket.upload(fileName, bytes)

            // Construct public URL
            "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/$BUCKET_NAME/$fileName"
        } catch (e: Exception) {
            null
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
            true
        } catch (e: Exception) {
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
