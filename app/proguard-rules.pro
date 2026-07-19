# Add project-specific ProGuard / R8 rules.

# Kotlin / coroutines
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**

# Kotlinx Serialization
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class ** { *; }

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.* <methods>;
}
-keep class * extends com.vctrch.golfgps.GolfGpsApplication { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Play Services / Maps / Billing / Firebase
-dontwarn com.google.android.gms.**
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.maps.** { *; }
-keep interface com.google.android.gms.maps.** { *; }

# Android Auto / Car App
-keep class androidx.car.app.** { *; }
-keep class com.vctrch.golfgps.feature.auto.** { *; }

# osmdroid (debug / release OSM fallback)
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
