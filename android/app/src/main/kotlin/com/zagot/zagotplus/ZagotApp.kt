package com.zagot.zagotplus

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration as WorkConfiguration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.zagot.zagotplus.sync.SyncManager
import dagger.hilt.android.HiltAndroidApp
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class ZagotApp : Application(), WorkConfiguration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncManager: SyncManager

    override fun onCreate() {
        super.onCreate()
        // Force Ukrainian locale
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("uk"))
        // Initialize periodic background sync
        syncManager.initializePeriodicSync()
    }

    override fun attachBaseContext(base: Context) {
        // Force Ukrainian locale on context
        val ukrainianLocale = Locale("uk")
        Locale.setDefault(ukrainianLocale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(ukrainianLocale)
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override val workManagerConfiguration: WorkConfiguration
        get() = WorkConfiguration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    
    /**
     * Configure Coil ImageLoader with disk caching for product images.
     * Images are cached to disk to avoid re-downloading on app restart.
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // 25% of available memory
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("product_images"))
                    .maxSizePercent(0.02) // 2% of storage
                    .build()
            }
            .respectCacheHeaders(false) // Ignore server cache headers for offline support
            .crossfade(true)
            .build()
    }
}
