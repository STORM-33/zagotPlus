package com.zagot.zagotplus.data.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class to persist images from content:// URIs to app internal storage.
 * This ensures images remain accessible after app restart and can be synced.
 */
@Singleton
class ImageStorageHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val IMAGES_DIR = "product_images"
    }

    /**
     * Copy an image from a content:// URI to internal storage.
     * Returns the file:// URI of the persisted image, or null on failure.
     */
    fun persistImage(contentUri: String): String? {
        return try {
            val uri = Uri.parse(contentUri)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            
            val imagesDir = File(context.filesDir, IMAGES_DIR)
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            
            val fileName = "${UUID.randomUUID()}.jpg"
            val outputFile = File(imagesDir, fileName)
            
            FileOutputStream(outputFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            
            Uri.fromFile(outputFile).toString()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Delete an image from internal storage.
     */
    fun deleteImage(fileUri: String): Boolean {
        return try {
            val uri = Uri.parse(fileUri)
            val file = uri.path?.let { File(it) }
            file?.delete() ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if the URI is a temporary content:// URI that needs persistence.
     */
    fun isTemporaryUri(uri: String?): Boolean {
        return uri?.startsWith("content://") == true
    }
}
