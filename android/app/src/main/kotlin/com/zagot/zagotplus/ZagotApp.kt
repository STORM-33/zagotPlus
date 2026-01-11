package com.zagot.zagotplus

import android.app.Application
import com.zagot.zagotplus.sync.SyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ZagotApp : Application() {
    
    @Inject
    lateinit var syncManager: SyncManager
    
    override fun onCreate() {
        super.onCreate()
        // Initialize periodic background sync
        syncManager.initializePeriodicSync()
    }
}
