# VoDrop - Complete Project Context & Summary

> **Last Updated:** January 25, 2026  
> **Current Branch:** `gemini-hackathon` (stripped of auth for hackathon submission)  
> **Main Branch:** `main` (full auth, payments, trials - for Play Store release)

---

## 📋 Executive Summary

**VoDrop** is a voice-to-text transcription Android application built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**. The app allows users to record voice memos and instantly convert them to text using **Google Cloud Speech-to-Text V2 (Chirp 3)**, with optional AI-powered text cleanup using **Gemini 3 Flash**.

### What Makes VoDrop Special
1. **Simple & Fast** - One tap to record, one tap to transcribe
2. **High Accuracy** - Uses Google's latest Chirp 3 model for STT
3. **AI Polish** - Gemini 3 removes filler words and fixes grammar
4. **Background Recording** - Foreground service for recording while using other apps
5. **Cloud-Only Architecture** - No local models, all processing in Firebase Cloud Functions

### Current State (January 2026)
We are preparing VoDrop for the **Gemini 3 Hackathon** (deadline: February 9, 2026). The hackathon branch has all authentication, payments, and trial logic removed to meet the requirement of "no login required."

---

## 🎯 Project Origin & Evolution

### Initial Vision
The developer wanted to create a simple, reliable voice-to-text app because:
- **Gboard's voice typing** has poor accuracy for long speech
- **WhisperFlow** isn't available on Android
- Other solutions are paid or have complex UIs

### Technical Journey

1. **Phase 1: Local Whisper (Abandoned)**
   - Initially tried **Whisper.cpp** for offline transcription
   - Built native libraries for Android using NDK
   - Problem: Transcription hung indefinitely on real devices
   - Debugging revealed build/model compatibility issues
   - Decision: **Pivot to cloud-only** for reliability

2. **Phase 2: Groq Whisper API (Temporary)**
   - Used Groq's hosted Whisper API
   - Worked but required hardcoded API keys in app
   - Security concern for production

3. **Phase 3: Firebase + Chirp 3 (Current)**
   - Moved all API keys to Firebase Cloud Functions
   - Switched to **Google Cloud Speech-to-Text V2 with Chirp 3**
   - Added **Gemini 3 Flash** for text cleanup
   - All sensitive operations happen server-side

4. **Phase 4: Hackathon Preparation (Now)**
   - Created `gemini-hackathon` branch
   - Stripped all authentication (Firebase Auth, RevenueCat)
   - Removed trial/Pro logic
   - App now works without login

---

## 🏗️ Architecture Overview

### Tech Stack

| Layer                    | Technology                                          |
|--------------------------|-----------------------------------------------------|
| **UI Framework**         | Compose Multiplatform + Material 3                  |
| **Architecture Pattern** | MVVM (ViewModel + StateFlow)                        |
| **Dependency Injection** | Koin                                                |
| **Local Database**       | SQLDelight (transcription history)                  |
| **Cloud Speech-to-Text** | Google Chirp 3 via Firebase Cloud Functions         |
| **AI Text Cleanup**      | Gemini 3 Flash Preview via Firebase Cloud Functions |
| **Audio Recording**      | Android AudioRecord API (48kHz, mono, 16-bit)       |
| **Background Service**   | Android Foreground Service with notification        |
| **Cloud Infrastructure** | Firebase (Functions, Storage, Auth*)                |
| **Payments**             | RevenueCat*                                         |

*Auth and payments exist in main branch, removed in hackathon branch*

### Project Structure

```
VoDrop/
├── composeApp/
│   └── src/
│       ├── commonMain/          # Shared code (all platforms)
│       │   └── kotlin/com/liftley/vodrop/
│       │       ├── App.kt                 # Compose entry point
│       │       ├── data/
│       │       │   ├── audio/AudioConfig.kt        # 48kHz, mono, 16-bit
│       │       │   ├── stt/SpeechToTextEngine.kt   # STT interface
│       │       │   ├── llm/TextCleanupService.kt   # Cleanup interface
│       │       │   └── firebase/FirebaseFunctionsService.kt
│       │       ├── domain/
│       │       │   ├── model/Transcription.kt      # Data model
│       │       │   ├── repository/TranscriptionRepository.kt
│       │       │   └── usecase/
│       │       │       ├── TranscribeAudioUseCase.kt  # Orchestrates STT + AI
│       │       │       └── ManageHistoryUseCase.kt    # CRUD for history
│       │       ├── di/AppModule.kt         # Koin module (shared)
│       │       └── ui/
│       │           ├── main/
│       │           │   ├── MainScreen.kt       # Main UI
│       │           │   ├── MainViewModel.kt    # State management
│       │           │   └── MainUiState.kt      # UI state model
│       │           ├── components/
│       │           │   ├── recording/
│       │           │   │   ├── RecordButton.kt     # Animated record button
│       │           │   │   └── RecordingCard.kt    # Recording UI card
│       │           │   └── history/
│       │           │       ├── HistoryCard.kt      # Transcription item
│       │           │       └── EmptyState.kt       # No history UI
│       │           └── theme/Theme.kt          # Material 3 theme
│       │
│       ├── androidMain/         # Android-specific implementations
│       │   └── kotlin/com/liftley/vodrop/
│       │       ├── VoDropApplication.kt    # Application class (Koin init)
│       │       ├── MainActivity.kt         # Activity entry
│       │       ├── service/
│       │       │   └── RecordingService.kt # Foreground service for recording
│       │       ├── data/
│       │       │   ├── audio/AudioRecorder.android.kt  # AudioRecord API
│       │       │   ├── stt/SpeechToTextEngine.android.kt
│       │       │   ├── llm/FirebaseTextCleanupService.kt
│       │       │   └── firebase/FirebaseFunctionsService.android.kt
│       │       └── di/PlatformModule.android.kt
│       │
│       └── jvmMain/             # Desktop (JVM) implementation
│           └── kotlin/com/liftley/vodrop/
│               ├── main.kt
│               ├── data/...
│               └── di/PlatformModule.jvm.kt
│
├── functions/                   # Firebase Cloud Functions (TypeScript)
│   └── src/index.ts            # transcribeChirp + cleanupText functions
│
├── iosApp/                      # iOS (placeholder, not implemented)
└── gradle files...
```

---

## 🔄 Data Flow

### Complete Recording → Transcription Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           USER ACTION                                    │
│                    User taps Record Button                               │
└─────────────────────────────┬───────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        MainViewModel                                     │
│  onRecordClick() → Check micPhase → startRecording()                    │
└─────────────────────────────┬───────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      AudioRecorder (Android)                             │
│  1. Check RECORD_AUDIO permission                                        │
│  2. Start RecordingService (foreground notification)                     │
│  3. Initialize AudioRecord (48kHz, mono, 16-bit)                        │
│  4. Start recording thread → write to ByteArrayOutputStream             │
│  5. Update status with amplitude for UI visualization                   │
└─────────────────────────────┬───────────────────────────────────────────┘
                              │
                         User taps Stop
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      AudioRecorder.stopRecording()                       │
│  1. Stop recording thread                                                │
│  2. Stop RecordingService                                                │
│  3. Return ByteArray of raw PCM audio                                   │
└─────────────────────────────┬───────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     TranscribeAudioUseCase                               │
│  invoke(audioData, mode, onProgress, onIntermediateResult)              │
└─────────────────────────────┬───────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                   SpeechToTextEngine (Android)                           │
│  1. createWavFile(pcmData) - Add 44-byte WAV header using ByteBuffer    │
│  2. Upload WAV to Firebase Storage (gs://post-3424f.firebasestorage.app)│
│  3. Call transcribeChirp Cloud Function with GCS URI                    │
│  4. Delete uploaded file after transcription                            │
│  5. Return TranscriptionResult.Success(text)                            │
└─────────────────────────────┬───────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│               Firebase Cloud Function: transcribeChirp                   │
│  Location: us (Multi-Region)                                            │
│  1. Validate auth (currently required, will be issue for hackathon)     │
│  2. Call Google Speech-to-Text V2 batchRecognize()                      │
│     - Recognizer: vodrop-chirp (Chirp 3 model)                          │
│     - Config: autoDecodingConfig, en-US, punctuation enabled            │
│  3. Download result JSON from GCS                                       │
│  4. Extract transcript text                                              │
│  5. Return { text: "transcribed text" }                                 │
└─────────────────────────────┬───────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                 TranscribeAudioUseCase (continued)                       │
│  If mode == WITH_AI_POLISH && text.length > 20:                         │
│    → Call TextCleanupService.cleanupText(text)                          │
└─────────────────────────────┬───────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│              Firebase Cloud Function: cleanupText                        │
│  1. Validate auth                                                        │
│  2. Build prompt with BASE_CLEANUP_RULES + style (FORMAL/INFORMAL/CASUAL)│
│  3. Call Gemini 3 Flash Preview API                                     │
│  4. Return { text: "cleaned text" }                                     │
└─────────────────────────────┬───────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                       ManageHistoryUseCase                               │
│  saveTranscription(text) → SQLDelight database                          │
└─────────────────────────────┬───────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        MainViewModel                                     │
│  Update MainUiState:                                                     │
│    - currentTranscription = result.text                                  │
│    - micPhase = MicPhase.Idle                                           │
│    - history = [updated list]                                            │
└─────────────────────────────┬───────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          UI Recompose                                    │
│  MainScreen displays transcription in RecordingCard                     │
│  User can Copy, Edit, or Delete                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 📱 Current UI State Management

### MainUiState (Hackathon Branch - Simplified)

```kotlin
sealed interface MicPhase {
    data object Idle : MicPhase
    data object Recording : MicPhase
    data object Processing : MicPhase
    data class Error(val message: String) : MicPhase
}

enum class TranscriptionMode(val displayName: String) {
    STANDARD("Standard"),
    WITH_AI_POLISH("AI Polish")
}

data class MainUiState(
    val micPhase: MicPhase = MicPhase.Idle,
    val currentTranscription: String = "",
    val progressMessage: String = "",
    val history: List<Transcription> = emptyList(),
    val transcriptionMode: TranscriptionMode = TranscriptionMode.STANDARD,
    val deleteConfirmationId: Long? = null,
    val editingTranscription: Transcription? = null,
    val editText: String = "",
    val isDrawerOpen: Boolean = false,
    val improvingId: Long? = null
) {
    val canTranscribe get() = true  // Always true in hackathon branch
    val statusText get() = "VoDrop • Free"
}
```

### MainViewModel (Hackathon Branch - Simplified)

Key functions:
- `onRecordClick()` - Start/stop recording based on current phase
- `onCancelRecording()` - Cancel recording and discard
- `cancelProcessing()` - Cancel transcription in progress
- `selectMode(mode)` - Switch between Standard and AI Polish
- `clearError()` - Dismiss error state
- `requestDelete(id)`, `confirmDelete()`, `cancelDelete()` - History management
- `startEdit(t)`, `updateEditText(text)`, `saveEdit()`, `cancelEdit()` - Edit transcription
- `onImproveWithAI(t)` - Improve existing transcription with AI

---

## ☁️ Firebase Cloud Functions

### File: `functions/src/index.ts`

#### 1. `transcribeChirp`
Transcribes audio using Google Cloud Speech-to-Text V2 with Chirp 3.

```typescript
// Configuration
const REGION = "us";  // Multi-region for Chirp 3
const RECOGNIZER_ID = "vodrop-chirp";

// Flow:
// 1. Receive GCS URI from app (gs://bucket/uploads/uuid.wav)
// 2. Call batchRecognize with Chirp 3 model
// 3. Wait for operation to complete
// 4. Download result JSON from GCS
// 5. Extract transcript and return
```

#### 2. `cleanupText`
Cleans up transcription using Gemini 3 Flash.

```typescript
// Model: gemini-3-flash-preview
// Styles: FORMAL, INFORMAL (default), CASUAL

// Prompt includes:
// - Core cleanup rules (fix grammar, remove filler words)
// - Style-specific guidelines
// - Examples

// Returns cleaned text
```

---

## 🎧 Audio Configuration

### AudioConfig (commonMain)

```kotlin
object AudioConfig {
    const val SAMPLE_RATE = 48000   // 48kHz - high quality
    const val CHANNELS = 1          // Mono
    const val BITS_PER_SAMPLE = 16  // 16-bit
    const val BYTES_PER_SAMPLE = 2

    fun calculateDurationSeconds(audioData: ByteArray): Float {
        return audioData.size.toFloat() / (SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE)
    }
}
```

### WAV File Creation

The app records raw PCM audio and adds a 44-byte WAV header before uploading:

```kotlin
private fun createWavFile(pcmData: ByteArray): ByteArray {
    val buffer = ByteBuffer.allocate(44 + pcmData.size)
        .order(ByteOrder.LITTLE_ENDIAN)

    // RIFF header
    buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
    buffer.putInt(36 + dataSize)
    buffer.put("WAVE".toByteArray(Charsets.US_ASCII))

    // fmt chunk
    buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
    buffer.putInt(16)              // Chunk size
    buffer.putShort(1)             // PCM format
    buffer.putShort(1)             // Mono
    buffer.putInt(48000)           // Sample rate
    buffer.putInt(96000)           // Byte rate
    buffer.putShort(2)             // Block align
    buffer.putShort(16)            // Bits per sample

    // data chunk
    buffer.put("data".toByteArray(Charsets.US_ASCII))
    buffer.putInt(dataSize)
    buffer.put(pcmData)

    return buffer.array()
}
```

---

## 🔒 Authentication (Main Branch Only)

*Note: This section describes the main branch. The hackathon branch has all auth removed.*

### Components
- **Firebase Authentication** - Google Sign-In
- **RevenueCat** - Pro subscription management
- **Firestore** - User data (trials, usage)

### Access Control
```kotlin
data class AccessState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val isPro: Boolean = false,
    val freeTrialsRemaining: Int = 0  // 3 free trials for new users
)
```

---

## 🎨 UI Components

### RecordButton
- Large circular button (160dp)
- States: Idle (blue mic icon), Recording (red with waveform animation), Processing (gray with spinner)
- Waveform animation uses Canvas with 5 animated bars

### RecordingCard
- Main recording UI
- Shows title/subtitle based on state
- Displays transcription result with copy button
- Shows progress message during processing
- Error state with dismiss button

### HistoryCard
- Displays past transcriptions
- Actions: Copy, Edit, Delete, AI Polish
- Shows loading indicator during AI polish

### AppDrawer
- Side navigation drawer
- In hackathon branch: Just shows app name and "Gemini 3 Hackathon Entry"
- In main branch: Login/logout buttons, Pro status, upgrade options

---

## 🏁 Hackathon Preparation

### Gemini 3 Hackathon Details
- **Prize Pool:** $100,000
- **Grand Prize:** $50,000
- **Deadline:** February 9, 2026 @ 8:00 PM EST
- **Requirement:** Must use Gemini 3 API, no login/paywall required

### Judging Criteria
| Criteria                | Weight |
|-------------------------|--------|
| Technical Execution     | 40%    |
| Innovation / Wow Factor | 30%    |
| Potential Impact        | 20%    |
| Presentation / Demo     | 10%    |

### Changes Made for Hackathon Branch

| Removed                 | Why                    |
|-------------------------|------------------------|
| Firebase Authentication | No login required      |
| Google Sign-In          | No login required      |
| RevenueCat              | No paywall             |
| Firestore user data     | No user accounts       |
| Trial limits            | Free unlimited use     |
| Pro restrictions        | All features available |
| Upgrade dialogs         | No monetization UI     |

### Files Deleted
- `commonMain/.../auth/` (entire folder)
- `androidMain/.../auth/` (entire folder)
- `jvmMain/.../auth/` (entire folder)
- `androidMain/.../data/firestore/` (entire folder)

### Files Modified
- `MainUiState.kt` - Removed auth-related state
- `MainViewModel.kt` - Removed PlatformAuth dependency
- `MainScreen.kt` - Removed login buttons, upgrade dialogs
- `AppModule.kt` - Removed PlatformAuth from ViewModel
- `PlatformModule.android.kt` - Removed auth dependencies
- `App.kt` - Simplified
- `MainActivity.kt` - Removed auth initialization
- `AppDrawer.kt` - Removed login UI
- `HistoryCard.kt` - Removed isPro checks

### ⚠️ IMPORTANT: Cloud Functions Still Require Auth

The Cloud Functions (`transcribeChirp` and `cleanupText`) still check `request.auth`:

```typescript
if (!request.auth) throw new HttpsError("unauthenticated", "Must be logged in");
```

**For hackathon submission, you need to either:**
1. Remove auth checks from Cloud Functions, OR
2. Use Anonymous Authentication in the app

---

## 📋 Planned Hackathon Features

### P0 - Must Have
1. ✅ Basic recording + transcription
2. ✅ AI Polish with Gemini 3
3. ✅ Remove login requirement (app side done)
4. ⏳ Update Cloud Functions to allow unauthenticated calls
5. ⏳ Notification actions (record/stop from notification)
6. ⏳ Copy from notification when transcription complete

### P1 - Should Have
1. Dual output view (raw + polished side-by-side)
2. Better UI polish for judges
3. 3-minute demo video

---

## 🛠️ Build & Run

### Prerequisites
- Android Studio Ladybug or later
- JDK 17+
- Android SDK 34

### Run Android
```bash
# In Android Studio: Run 'composeApp' configuration
# Or via command line:
./gradlew :composeApp:installDebug
```

### Run Desktop (JVM)
```bash
./gradlew :composeApp:run
```

### Deploy Cloud Functions
```bash
cd functions
npm install
firebase deploy --only functions
```

---

## 📚 Key Files Reference

| File                            | Purpose                                     |
|---------------------------------|---------------------------------------------|
| `VoDropApplication.kt`          | Application class, initializes Koin         |
| `MainActivity.kt`               | Entry point, requests mic permission        |
| `App.kt`                        | Compose entry point                         |
| `MainScreen.kt`                 | Main UI screen                              |
| `MainViewModel.kt`              | State management                            |
| `MainUiState.kt`                | UI state model                              |
| `TranscribeAudioUseCase.kt`     | Orchestrates STT + cleanup                  |
| `AudioRecorder.android.kt`      | Android audio recording                     |
| `SpeechToTextEngine.android.kt` | Calls transcribeChirp function              |
| `FirebaseTextCleanupService.kt` | Calls cleanupText function                  |
| `RecordingService.kt`           | Foreground service for background recording |
| `functions/src/index.ts`        | Firebase Cloud Functions                    |

---

## 🔗 Related Documentation

- [Google Cloud Speech-to-Text V2](https://cloud.google.com/speech-to-text/v2/docs)
- [Chirp 3 Model](https://cloud.google.com/speech-to-text/v2/docs/chirp-model)
- [Gemini API](https://ai.google.dev/docs)
- [Firebase Cloud Functions](https://firebase.google.com/docs/functions)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Koin DI](https://insert-koin.io/)

---

## 👤 Developer Notes

- The developer prefers clean, simple code without excessive complexity
- Values understanding "why" things work, not just "what" to do
- Prefers manual file updates over automated edits
- Already has background service infrastructure (RecordingService) ready for notification features
- The 48kHz sample rate is intentional for high quality, even though 16kHz would suffice for speech

---

*This document provides complete context for AI assistants to understand and work on the VoDrop project.*
