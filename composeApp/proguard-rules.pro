# === ESSENTIAL: JNI Native Methods ===
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.liftley.vodrop.data.audio.stt.WhisperJni { *; }

# === ESSENTIAL: Kotlinx Serialization (for Gemini API) ===
-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# === ESSENTIAL: Keep sealed class subclasses (for when expressions) ===
-keep class com.liftley.vodrop.stt.ModelState$* { *; }
-keep class com.liftley.vodrop.stt.TranscriptionResult$* { *; }
-keep class com.liftley.vodrop.audio.RecordingStatus$* { *; }

# === ESSENTIAL: SQLDelight generated code ===
-keep class com.liftley.vodrop.db.** { *; }

# ... existing rules ...

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# RevenueCat
-keep class com.revenuecat.** { *; }

# === Credential Manager (Google Sign-In) ===
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**

# === Suppress warnings only ===
-dontwarn com.revenuecat.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**