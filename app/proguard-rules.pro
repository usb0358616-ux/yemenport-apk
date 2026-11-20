# ProGuard / R8 keep rules for نظام المنافذ والجوازات
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep <fields>;
    @androidx.annotation.Keep <methods>;
}
-keep @androidx.annotation.Keep class * { *; }

# Keep Room entities, TypeConverters, Firestore models, and kotlinx.serialization serializers
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepclassmembers class com.example.yemenport.data.local.** { *; }
-keepclassmembers class com.example.yemenport.data.remote.** { *; }
-keepclassmembers class com.example.yemenport.domain.ai.** { *; }
-keep,includedescriptorclasses class com.example.yemenport.**$$serializer { *; }
-keepclassmembers class kotlinx.serialization.json.** { *; }

# Keep WorkManager, Biometric, and ML Kit classes
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class androidx.biometric.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn org.apache.commons.logging.**

