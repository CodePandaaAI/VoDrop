# ═══════════════════════════════════════════════════════════
# VoDrop ProGuard Rules (Simplified - Post SHA-1 Fix)
# ═══════════════════════════════════════════════════════════

# ═══ CORE ANDROID ═══
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep native methods (standard Android)
-keepclasseswithmembernames class * {
    native <methods>;
}

# ═══ KOTLINX SERIALIZATION (For Groq/Gemini API) ═══
-keep class kotlinx.serialization.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ═══ SQLDELIGHT ═══
-keep class com.liftley.vodrop.db.** { *; }

# ═══ FIREBASE (Core + Auth + Firestore) ═══
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**

# ═══ SUPPRESS COMMON WARNINGS ═══
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**