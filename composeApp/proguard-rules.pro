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

# Keep Parcelable (used by Android & Credential Manager)
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# ═══ KOTLINX SERIALIZATION (For Groq/Gemini API) ═══
-keep class kotlinx.serialization.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep your API data classes
-keep class com.liftley.vodrop.data.stt.GroqWhisperService$* { *; }
-keep class com.liftley.vodrop.data.llm.GeminiCleanupService$* { *; }

# ═══ SEALED CLASSES (For when expressions) ═══
-keep class com.liftley.vodrop.data.stt.TranscriptionState$* { *; }
-keep class com.liftley.vodrop.data.audio.RecordingStatus$* { *; }

# ═══ SQLDELIGHT ═══
-keep class com.liftley.vodrop.db.** { *; }

# ═══ FIREBASE (Core + Auth + Firestore) ═══
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Keep your UserData model
-keep class com.liftley.vodrop.data.firestore.UserData { *; }
-keep class com.liftley.vodrop.auth.User { *; }

# ═══ CREDENTIAL MANAGER (Google Sign-In) ═══
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**

# ═══ REVENUECAT ═══
-keep class com.revenuecat.purchases.** { *; }
-dontwarn com.revenuecat.**

# ═══ SUPPRESS COMMON WARNINGS ═══
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**