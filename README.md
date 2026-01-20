# VoDrop - Voice Transcription App

> **Transform voice into text with AI-powered polish. Built with Kotlin Multiplatform.**

---

## Executive Summary

**VoDrop** is a voice-to-text transcription application built using **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**. It enables users to record voice memos and instantly convert them to text using cloud-based speech recognition (Groq Whisper API), with optional AI-powered text cleanup (Google Gemini).

### Business Model
- **Free Tier**: 3 free transcriptions (requires Google Sign-In)
- **Pro Tier**: $2.99/month for unlimited transcriptions + AI Polish feature
- **Target Users**: Professionals, students, content creators who need quick voice-to-text

### Current Version: v1 (Closed Launch)
- ✅ Android: Fully functional
- ⏳ Desktop (JVM): Functional with limited features (no auth)
- 📋 iOS: Placeholder (not implemented)

---

## Features

| Feature | Free | Pro |
|---------|------|-----|
| Voice Recording | ✅ | ✅ |
| Cloud Transcription (Whisper) | ✅ (3 trials) | ✅ Unlimited |
| Transcription History | ✅ | ✅ |
| Copy/Edit/Delete | ✅ | ✅ |
| AI Polish (Gemini cleanup) | ❌ | ✅ |
| Cross-device Sync | ❌ v2 | ❌ v2 |

### AI Polish (Pro Feature)
Uses Google Gemini to:
- Remove filler words ("um", "uh", "like")
- Fix grammar and punctuation
- Improve sentence structure
- Preserve original meaning

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| **UI** | Compose Multiplatform + Material 3 |
| **Architecture** | MVVM (ViewModel + StateFlow) |
| **DI** | Koin |
| **Database** | SQLDelight (local transcription history) |
| **Cloud STT** | Groq Whisper API |
| **AI Cleanup** | Google Gemini 2.0 Flash |
| **Auth** | Firebase Authentication (Google Sign-In) |
| **Subscriptions** | RevenueCat |
| **User Data** | Firebase Firestore |
| **Networking** | Ktor Client |

---

## Project Structure

```
VoDrop/
├── composeApp/
│   └── src/
│       ├── commonMain/          # Shared code (all platforms)
│       │   └── kotlin/com/liftley/vodrop/
│       │       ├── App.kt                 # Entry point
│       │       ├── auth/                  # Auth models, PlatformAuth expect
│       │       ├── data/                  # Data layer
│       │       │   ├── audio/             # AudioRecorder interface
│       │       │   ├── stt/               # SpeechToTextEngine interface
│       │       │   └── llm/               # TextCleanupService interface
│       │       ├── domain/                # Business logic
│       │       │   ├── model/             # Transcription model
│       │       │   ├── repository/        # TranscriptionRepository
│       │       │   └── usecase/           # Use cases
│       │       ├── di/                    # Koin modules
│       │       └── ui/                    # Compose UI
│       │           ├── main/              # MainScreen, ViewModel, State
│       │           ├── components/        # Reusable components
│       │           └── theme/             # Material 3 theme
│       │
│       ├── androidMain/         # Android-specific
│       │   └── kotlin/com/liftley/vodrop/
│       │       ├── MainActivity.kt        # Entry point
│       │       ├── auth/                  # Firebase, RevenueCat, AccessManager
│       │       ├── data/                  # Platform implementations
│       │       │   ├── audio/             # Android AudioRecord
│       │       │   ├── stt/               # Groq API client
│       │       │   ├── firestore/         # Firestore operations
│       │       │   └── llm/               # Gemini API client
│       │       └── di/                    # Android Koin module
│       │
│       └── jvmMain/             # Desktop-specific
│           └── kotlin/com/liftley/vodrop/
│               ├── main.kt                # Desktop entry
│               ├── auth/                  # Stub (no auth on desktop)
│               └── data/                  # Desktop implementations
│
└── iosApp/                      # iOS entry point (placeholder)
```

---

## Architecture

### MVVM Pattern
```
┌─────────────────────────────────────────────────────────────┐
│                      UI Layer                                │
│  MainScreen.kt (Stateless Composable)                       │
│  - Collects state from ViewModel                            │
│  - Triggers ViewModel actions                               │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│                   ViewModel Layer                            │
│  MainViewModel.kt                                           │
│  - Holds MainUiState (single source of truth)               │
│  - Handles business logic                                    │
│  - Coordinates use cases                                     │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│                   Domain Layer                               │
│  Use Cases: TranscribeAudioUseCase, ManageHistoryUseCase    │
│  Repository: TranscriptionRepository                         │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│                    Data Layer                                │
│  Platform-specific implementations (expect/actual)          │
│  - AudioRecorder (Android: AudioRecord, JVM: TargetDataLine)│
│  - SpeechToTextEngine (Groq API)                            │
│  - TextCleanupService (Gemini API)                          │
│  - TranscriptionRepositoryImpl (SQLDelight)                 │
└─────────────────────────────────────────────────────────────┘
```

### expect/actual Pattern (KMP)
```kotlin
// commonMain - Define contract
expect class PlatformAuth {
    suspend fun signIn(): Result<User>
    suspend fun signOut()
}

// androidMain - Android implementation
actual class PlatformAuth(...) {
    actual suspend fun signIn() = firebaseAuth.signInWithGoogle(activity)
}

// jvmMain - Desktop stub
actual class PlatformAuth {
    actual suspend fun signIn() = Result.success(User("desktop", null, "Desktop User", null))
}
```

---

## Data Flow

### Recording Flow
```
User taps Record → MainViewModel.onRecordClick()
    → AudioRecorder.startRecording()
    → User taps Stop
    → AudioRecorder.stopRecording() → ByteArray
    → TranscribeAudioUseCase.invoke(audioData)
        → SpeechToTextEngine.transcribe() → Groq API
        → (If AI Polish) TextCleanupService.cleanup() → Gemini API
    → ManageHistoryUseCase.saveTranscription()
    → MainUiState updated → UI recomposes
```

### Auth Flow
```
User taps Sign In (in Drawer)
    → PlatformAuth.signIn()
        → FirebaseAuthManager.signInWithGoogle() → Google credential
        → SubscriptionManager.loginWithFirebaseUser() → RevenueCat
        → AccessManager.onUserLoggedIn() → Firestore user data
    → AccessState updated → App.kt LaunchedEffect
    → MainViewModel.setAuth() → MainUiState updated
```

---

## Key Files

| File | Purpose | Lines |
|------|---------|-------|
| `App.kt` | Single entry point, auth sync | ~50 |
| `MainViewModel.kt` | UI state management | ~100 |
| `MainUiState.kt` | Single source of truth for UI | ~45 |
| `MainScreen.kt` | Main UI (stateless) | ~100 |
| `PlatformAuth.kt` | Auth abstraction (expect) | ~35 |
| `PlatformAuth.android.kt` | Firebase/RevenueCat impl | ~80 |
| `AccessManager.kt` | Unified access control | ~100 |
| `FirestoreManager.kt` | User data operations | ~180 |
| `TranscribeAudioUseCase.kt` | STT + AI orchestration | ~60 |
| `CloudSpeechToTextEngine.kt` | Groq Whisper client | ~100 |

---

## Configuration

### API Keys (v1 Hardcoded - Move to Backend for Production)
- **Groq API**: `GroqConfig.API_KEY` in `data/stt/GroqConfig.kt`
- **Gemini API**: `LLMConfig.GEMINI_API_KEY` in `data/llm/LLMConfig.kt`
- **RevenueCat**: `AuthConfig.REVENUECAT_API_KEY` in `auth/AuthConfig.kt`
- **Firebase**: Auto-configured via `google-services.json`

### Firestore Structure
```
users/{userId}
├── freeTrialsRemaining: Int (0-3)
├── currentMonthUsageSeconds: Long
├── usageResetDate: String ("YYYY-MM-01")
├── activeDeviceId: String
├── createdAt: Long
└── lastActiveAt: Long
```

---

## Build Instructions

### Prerequisites
- Android Studio Ladybug or later
- JDK 17+
- Android SDK 34

### Android
```bash
# Debug build
./gradlew :composeApp:assembleDebug

# Release build (signed)
./gradlew :composeApp:assembleRelease
```

### Desktop (JVM)
```bash
./gradlew :composeApp:run
```

### ProGuard (Release)
See `composeApp/proguard-rules.pro` for Firebase/RevenueCat/Credential Manager rules.

---

## Roadmap

### v1 (Current)
- [x] Voice recording
- [x] Cloud transcription (Groq Whisper)
- [x] AI Polish (Gemini)
- [x] Local history (SQLDelight)
- [x] Google Sign-In
- [x] Free trials (3)
- [x] Pro subscription (RevenueCat)
- [x] Usage tracking (Firestore)

### v2 (Planned)
- [ ] Cross-device sync (Firestore history)
- [ ] Settings screen
- [ ] Cleanup style selection (Formal/Informal/Casual)
- [ ] Export to file/share
- [ ] Yearly subscription option
- [ ] Onboarding flow

### v3 (Future)
- [ ] iOS support
- [ ] Offline mode (Whisper.cpp)
- [ ] Team/Family plans
- [ ] API for integrations

---

## License

© 2026 Liftley. All rights reserved.

---

## Contact

For support or inquiries: [support@liftley.com](mailto:support@liftley.com)