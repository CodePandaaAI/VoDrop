# === ESSENTIAL: JNI Native Methods (for future if needed) ===
-keepclasseswithmembernames class * {
    native <methods>;
}

# === ESSENTIAL: Kotlinx Serialization (for Gemini/Groq API) ===
-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# === App's serializable data classes ===
-keep class com.liftley.vodrop.data.stt.GroqWhisperService$* { *; }
-keep class com.liftley.vodrop.data.llm.GeminiCleanupService$* { *; }

# === Keep sealed class subclasses (for when expressions) ===
-keep class com.liftley.vodrop.data.stt.TranscriptionState$* { *; }
-keep class com.liftley.vodrop.data.stt.TranscriptionResult$* { *; }
-keep class com.liftley.vodrop.data.audio.RecordingStatus$* { *; }

# === SQLDelight generated code ===
-keep class com.liftley.vodrop.db.** { *; }

# === Firebase ===
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# === RevenueCat ===
-keep class com.revenuecat.** { *; }
-dontwarn com.revenuecat.**

# === Credential Manager (Google Sign-In) ===
-keep class androidx.credentials.** { *; }
-keep class androidx.credentials.playservices.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keepclassmembers class androidx.credentials.** { *; }
-keep class * extends androidx.credentials.Credential { *; }
-keep class * extends androidx.credentials.CredentialOption { *; }
-keep class androidx.credentials.exceptions.** { *; }
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**

# === Firestore UserData ===
-keep class com.liftley.vodrop.data.firestore.UserData { *; }

# === Suppress other warnings ===
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**