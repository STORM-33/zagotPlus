package com.zagot.zagotplus.data.remote

import com.zagot.zagotplus.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json
import javax.inject.Singleton

/**
 * Hilt module providing Supabase client as singleton.
 * 
 * NOTE: GoTrue auth is not currently installed. When the RLS migration is applied
 * that requires authenticated access, we'll need to add proper auth here.
 * For now, we're using the anon key directly.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            defaultSerializer = KotlinXSerializer(Json {
                ignoreUnknownKeys = true
                explicitNulls = true
                encodeDefaults = true
            })
            install(Postgrest)
            install(Storage)
            install(Realtime)
            // TODO: Add GoTrue when auth is properly configured
            // install(GoTrue) { ... }
        }
    }
}
