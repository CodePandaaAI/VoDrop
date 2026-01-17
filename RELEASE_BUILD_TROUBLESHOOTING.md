# Release Build Troubleshooting Guide

## Google Sign-In Not Working in Release Builds

If Google Sign-In works in debug but fails in release builds, check the following:

### 1. **SHA-1 Fingerprint for Release Keystore**

Release builds use a different signing key than debug builds. You **must** add the release keystore's SHA-1 fingerprint to Firebase Console.

#### Get Release SHA-1:
```bash
# On Windows (PowerShell)
keytool -list -v -keystore "path\to\your\release\keystore.jks" -alias your-key-alias

# On Mac/Linux
keytool -list -v -keystore path/to/your/release/keystore.jks -alias your-key-alias
```

Look for the `SHA1:` value and add it to:
- Firebase Console → Project Settings → Your Android App → SHA certificate fingerprints

#### Get Debug SHA-1 (for reference):
```bash
# Windows
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android

# Mac/Linux
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

### 2. **ProGuard Rules**

The `proguard-rules.pro` file has been updated with comprehensive rules for:
- Credential Manager classes
- Google Identity classes
- Firebase Auth classes
- Exception classes
- Parcelable implementations

**Important:** If you add new authentication-related code, make sure to add corresponding ProGuard keep rules.

### 3. **OAuth Client Configuration**

Verify in [Google Cloud Console](https://console.cloud.google.com/):
1. Go to APIs & Services → Credentials
2. Find your OAuth 2.0 Client ID (Web application)
3. Ensure the authorized redirect URIs include your app's package name
4. The Web Client ID in `AuthConfig.kt` must match exactly

### 4. **Check Logcat for Detailed Errors**

In release builds, check Logcat for detailed error messages:
```bash
adb logcat | grep -i "FirebaseAuthManager\|Credential\|Google"
```

The enhanced error logging will show:
- Exception class names
- Exception types
- Full stack traces

### 5. **Common Issues**

#### Issue: "Could not get Google credentials"
- **Cause:** ProGuard obfuscation or missing SHA-1 fingerprint
- **Fix:** Add release SHA-1 to Firebase Console and verify ProGuard rules

#### Issue: "GetCredentialException"
- **Cause:** OAuth client misconfiguration or network issues
- **Fix:** Verify Web Client ID and check network connectivity

#### Issue: "No credentials available"
- **Cause:** User hasn't authorized the app or Google Play Services issue
- **Fix:** Clear app data, reinstall, or check Google Play Services

### 6. **Testing Release Builds**

To test a release build locally:
```bash
# Build release APK
./gradlew assembleRelease

# Install on device
adb install -r composeApp/build/outputs/apk/release/composeApp-release.apk

# Monitor logs
adb logcat | grep -i "FirebaseAuthManager"
```

### 7. **Verify ProGuard is Working**

After building a release APK, check that classes are obfuscated:
```bash
# Extract and check classes.dex
unzip -q composeApp-release.apk -d temp/
dexdump temp/classes.dex | grep -i "FirebaseAuthManager"
```

If you see obfuscated names (like `a.b.c`), ProGuard is working. If you see full class names, ProGuard might not be enabled.

---

## Quick Checklist

- [ ] Release SHA-1 fingerprint added to Firebase Console
- [ ] ProGuard rules file is up to date
- [ ] Web Client ID matches in Google Cloud Console
- [ ] `google-services.json` is up to date
- [ ] Testing with release build (not debug)
- [ ] Checked Logcat for detailed error messages
