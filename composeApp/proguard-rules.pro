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

# === Credential Manager (Google Sign-In) - CRITICAL FOR RELEASE ===
# Keep all Credential Manager classes
-keep class androidx.credentials.** { *; }
-keep class androidx.credentials.playservices.** { *; }
-keepclassmembers class androidx.credentials.** { *; }

# Keep all Google Identity classes
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keepclassmembers class com.google.android.libraries.identity.googleid.** { *; }

# Keep Credential base classes and their implementations
-keep class * extends androidx.credentials.Credential { *; }
-keep class * extends androidx.credentials.CredentialOption { *; }
-keep class * implements androidx.credentials.Credential { *; }

# Keep exception classes (used via reflection)
-keep class androidx.credentials.exceptions.** { *; }
-keepclassmembers class androidx.credentials.exceptions.** { *; }

# Keep Google ID Token Credential classes
-keep class com.google.android.libraries.identity.googleid.GoogleIdTokenCredential { *; }
-keep class com.google.android.libraries.identity.googleid.GetGoogleIdOption { *; }
-keep class com.google.android.libraries.identity.googleid.GetGoogleIdOption$Builder { *; }

# Keep Parcelable implementations (Credential Manager uses Parcelable)
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep Firebase Auth classes
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keepclassmembers class com.google.firebase.auth.** { *; }

# Keep User data class
-keep class com.liftley.vodrop.auth.User { *; }
-keep class com.liftley.vodrop.auth.FirebaseAuthManager { *; }

# Suppress warnings
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**
-dontwarn com.google.firebase.auth.**
-dontwarn com.google.android.gms.auth.**

# === Firestore UserData ===
-keep class com.liftley.vodrop.data.firestore.UserData { *; }

# === Keep reflection-based classes (Credential Manager uses reflection) ===
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes Exceptions

# === Keep classes used via reflection by Credential Manager ===
-keepclassmembers class * {
    @androidx.credentials.CredentialOption <methods>;
}

# === Keep Parcelable classes (Credential Manager uses Parcelable) ===
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# === Keep Serializable classes ===
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# === Suppress other warnings ===
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**