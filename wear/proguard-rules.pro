# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Wearable API classes
-keep class com.google.android.gms.wearable.** { *; }

# Keep serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @Serializable annotated classes
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** serializer(...);
}

# ==================== PyTorch Mobile Keep Rules ====================
# Keep all PyTorch classes
-keep class org.pytorch.** { *; }
-keep class com.facebook.jni.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep model loading related classes
-keep class org.pytorch.LiteModuleLoader { *; }
-keep class org.pytorch.Module { *; }
-keep class org.pytorch.IValue { *; }
-keep class org.pytorch.Tensor { *; }

# Prevent stripping of native libraries
-keepclasseswithmembers class * {
    @org.pytorch.* <methods>;
}

# Keep backend model classes (sleep stage prediction)
-keep class com.example.sleeptandard_mvp_demo.backend.model.** { *; }
-keep class com.example.sleeptandard_mvp_demo.backend.processing.** { *; }

