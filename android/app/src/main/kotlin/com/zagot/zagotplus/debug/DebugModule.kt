package com.zagot.zagotplus.debug

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DebugModule {
    
    @Provides
    @Singleton
    fun provideMainThreadDebugger(): MainThreadDebugger {
        return MainThreadDebugger()
    }
    
    @Provides
    @Singleton
    fun providePerformanceTracer(): PerformanceTracer {
        return PerformanceTracer()
    }
    
    @Provides
    @Singleton
    fun provideRecompositionTracker(): RecompositionTracker {
        return RecompositionTracker()
    }
}
