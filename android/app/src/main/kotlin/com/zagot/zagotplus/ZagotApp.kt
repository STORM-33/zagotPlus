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
import com.zagot.zagotplus.debug.CrashLogger
import com.zagot.zagotplus.debug.MainThreadDebugger
import com.zagot.zagotplus.debug.PerformanceTracer
import com.zagot.zagotplus.data.preferences.PreferencesWarmer
import com.zagot.zagotplus.sync.engine.api.SyncEngine
import com.zagot.zagotplus.sync.engine.api.ZagotSyncRegistrar
import com.zagot.zagotplus.sync.engine.db.SyncMigrationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dagger.hilt.android.HiltAndroidApp
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class ZagotApp : Application(), WorkConfiguration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncEngine: SyncEngine

    @Inject
    lateinit var syncRegistrar: ZagotSyncRegistrar

    @Inject
    lateinit var syncMigrationHelper: SyncMigrationHelper

    @Inject
    lateinit var mainThreadDebugger: MainThreadDebugger
    
    @Inject
    lateinit var performanceTracer: PerformanceTracer
    
    @Inject
    lateinit var crashLogger: CrashLogger
    
    @Inject
    lateinit var preferencesWarmer: PreferencesWarmer

    override fun onCreate() {
        super.onCreate()
        
        // Install crash handler FIRST to catch any initialization crashes
        crashLogger.install()
        
        // Log device info for crash debugging
        crashLogger.logDeviceInfo()
        
        // Pre-warm preferences on background thread FIRST to avoid main thread disk I/O
        preferencesWarmer.warmUp()
        
        // Force Ukrainian locale
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("uk"))
        // Register all tables then start the realtime sync engine.
        // Migration must complete before engine starts to avoid pushing
        // before all unsynced records are in the outbox.
        syncRegistrar.registerAll(syncEngine)
        CoroutineScope(Dispatchers.IO).launch {
            syncMigrationHelper.migrateIfNeeded()
            syncEngine.start()
        }
        
        // Initialize performance debugging in debug builds
//        if (BuildConfig.DEBUG) {
//            mainThreadDebugger.initialize()
//            performanceTracer.startPeriodicReport(30_000L) // Report every 30 seconds
//        }
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
