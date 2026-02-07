# ═══ APIS & DATA (Crucial) ═══
# This protects your API response models from being renamed/deleted
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;}

# ═══ SQLDELIGHT ═══
# Use your actual package name here
-keep class com.liftley.vodrop.db.** { *; }

# ═══ KOTLIN SERIALIZATION ═══
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ═══ FIREBASE / GMS ═══
# Most Firebase libraries provide their own rules.
# Only add these if you experience crashes in RELEASE builds.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# ═══ GENERAL HOUSEKEEPING ═══
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**