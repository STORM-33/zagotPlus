package com.zagot.zagotplus

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ZagotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Future: Initialize WorkManager, Supabase client
    }
}
