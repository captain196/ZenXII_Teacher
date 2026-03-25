# Retrofit
-keepattributes Signature
-keepattributes Annotation
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }

# Gson
-keep class com.schoolsync.teacher.data.model.** { *; }
-keepattributes *Annotation*

# Firebase
-keep class com.google.firebase.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }

# Firestore document models (needed for deserialization)
-keep class com.schoolsync.teacher.data.model.firestore.** { *; }

# Strip debug/verbose logs in release builds (security: prevents PII leakage)
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# Better obfuscation
-repackageclasses ''
