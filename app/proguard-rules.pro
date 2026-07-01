# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes Annotation
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }

# Retrofit — R8 full-mode consumer rules. Retrofit 2.9 does NOT bundle these
# (added upstream in 2.11). Without them, R8 full mode (AGP 8 default) strips
# the generic signature off suspend-function return types, so Retrofit reads
# the return type as a raw Class → "java.lang.Class cannot be cast to
# java.lang.reflect.ParameterizedType" at the first call (e.g. clear_must_change).
# Keep generic signature of Call, Response (stripped for non-kept types).
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
# Suspend functions are wrapped in a Continuation whose type argument carries
# the response type — keep it so the signature survives.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
# Keep each @http-annotated service interface WITH its generic signature.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

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

# Razorpay checkout
-keepattributes *Annotation*,Signature
-keepclassmembers class * {
    @proguard.annotation.Keep *;
    @proguard.annotation.KeepClassMembers *;
}
-keep class com.razorpay.** { *; }
-keep class proguard.annotation.Keep
-keep class proguard.annotation.KeepClassMembers
-dontwarn com.razorpay.**
-dontwarn proguard.annotation.**

# Better obfuscation
-repackageclasses ''
