# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Hilt annotations
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.zagot.zagotplus.**$$serializer { *; }
-keepclassmembers class com.zagot.zagotplus.** {
    *** Companion;
}
-keepclasseswithmembers class com.zagot.zagotplus.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Supabase/Ktor classes
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }

# Keep enum values (for serialization)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
