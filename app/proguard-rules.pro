# ProGuard / R8 keep rules for نظام المنافذ والجوازات
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep <fields>;
    @androidx.annotation.Keep <methods>;
}
-keep @androidx.annotation.Keep class * { *; }

# Keep Firestore models and kotlinx.serialization serializers
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepclassmembers class com.example.yemenport.data.remote.** { *; }
-keepclassmembers class com.example.yemenport.domain.ai.** { *; }
