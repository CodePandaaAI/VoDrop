<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%20%7C%20Desktop-blue" alt="Platform">
  <img src="https://img.shields.io/badge/Kotlin-2.1.21-purple" alt="Kotlin">
  <img src="https://img.shields.io/badge/Compose-Multiplatform-green" alt="Compose">
</p>

# VoDrop - Voice to Text, Instantly

> **Record. Transcribe. Polish. Done.**

VoDrop transforms your voice into clean, polished text in seconds. Powered by Google's Chirp 3 speech recognition and Gemini AI for intelligent text cleanup.

---

## 🎯 What is VoDrop?

VoDrop is a **voice-to-text transcription app** that solves a simple but persistent problem: **quickly converting speech to clean, usable text**.

### The Problem
- Gboard's voice typing has poor accuracy for longer speech
- WhisperFlow isn't available on Android
- Existing solutions are either paid, complex, or inaccurate
- Raw transcriptions often need manual cleanup (filler words, grammar mistakes)

### The Solution
VoDrop provides:
1. **One-tap recording** - Start speaking immediately
2. **High-accuracy transcription** - Google Chirp 3 speech recognition
3. **AI Polish** - Optional Gemini cleanup removes "um", "uh", fixes grammar
4. **Instant copy** - Get your text to clipboard in seconds
5. **Local history** - Your transcriptions saved for later

---

## ✨ Features

| Feature                      | Description                                             |
|------------------------------|---------------------------------------------------------|
| 🎤 **Voice Recording**       | Background-capable with foreground service notification |
| ☁️ **Cloud Transcription**   | Google Chirp 3 via Firebase Cloud Functions             |
| ✨ **AI Polish**              | Gemini cleans grammar, removes filler words             |
| 📋 **One-Tap Copy**          | Instant clipboard access                                |
| 📝 **Transcription History** | Local storage with edit/delete                          |
| 🌙 **Material 3 UI**         | Modern, clean interface                                 |

### Transcription Modes

| Mode          | What It Does                                                       |
|---------------|--------------------------------------------------------------------|
| **Standard**  | Pure Chirp 3 transcription                                         |
| **AI Polish** | Transcription + Gemini cleanup (removes "um", "uh", fixes grammar) |

---

## 🏗️ Architecture

VoDrop uses a **unified state architecture** with unidirectional data flow:

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                             │
│  MainScreen, RecordingCard, RecordButton                    │
│  └── Observes AppState directly (no translation)            │
└─────────────────────────────┬───────────────────────────────┘
                              │ collectAsState()
┌─────────────────────────────▼───────────────────────────────┐
│                      MainViewModel                           │
│  - Thin layer, exposes appState directly                    │
│  - Handles UI events (onRecordClick, onCancel)              │
│  - Manages UI-only state (dialogs, history)                 │
└─────────────────────────────┬───────────────────────────────┘
                              │ delegates to
┌─────────────────────────────▼───────────────────────────────┐
│                 RecordingSessionManager                      │
│  ★ SINGLE SOURCE OF TRUTH for recording state ★             │
│  - Owns AppState (Ready → Recording → Processing → Success) │
│  - Orchestrates AudioRecorder, TranscribeUseCase            │
│  - Controls foreground service via ServiceController        │
└─────────────────────────────┬───────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────────┐
│                      Data Layer                              │
│  AudioRecorder (pure bytes) │ CloudTranscriptionService     │
│  (STT + AI Polish unified)  │ TranscriptionRepository (SQL) │
└─────────────────────────────────────────────────────────────┘
```

### Core State: `AppState`

```kotlin
sealed interface AppState {
    data object Ready : AppState
    data object Recording : AppState
    data class Processing(val message: String) : AppState
    data class Success(val text: String) : AppState
    data class Error(val message: String) : AppState
}
```

This single sealed interface replaces what were previously 3 separate state classes. All UI components observe this directly.

---

## 🛠️ Tech Stack

| Layer              | Technology            | Purpose                           |
|--------------------|-----------------------|-----------------------------------|
| **UI**             | Compose Multiplatform | Cross-platform UI                 |
| **Design**         | Material 3            | Modern design system              |
| **Architecture**   | MVVM + SSOT           | Clean separation                  |
| **DI**             | Koin                  | Dependency injection              |
| **Database**       | SQLDelight            | Local transcription history       |
| **Speech-to-Text** | Google Chirp 3        | High-accuracy cloud STT           |
| **AI Cleanup**     | Gemini 3 Flash        | Text polish & grammar fix         |
| **Cloud**          | Firebase Functions    | Secure API key management         |
| **Background**     | Foreground Service    | Recording while app in background |

---

## 📁 Project Structure

```
VoDrop/
├── composeApp/src/
│   ├── commonMain/kotlin/com/liftley/vodrop/
│   │   ├── data/
│   │   │   ├── audio/AudioConfig.kt              # Audio constants + AudioRecorder interface
│   │   │   └── cloud/CloudTranscriptionService.kt # ★ Unified STT + AI Polish interface
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   │   ├── AppState.kt              # ★ Unified state
│   │   │   │   └── Transcription.kt
│   │   │   ├── manager/RecordingSessionManager.kt  # ★ SSOT
│   │   │   ├── repository/TranscriptionRepository.kt
│   │   │   └── usecase/TranscribeAudioUseCase.kt
│   │   ├── service/ServiceController.kt     # Foreground service control
│   │   ├── di/AppModule.kt                  # Koin DI
│   │   └── ui/
│   │       ├── main/
│   │       │   ├── MainScreen.kt
│   │       │   ├── MainViewModel.kt
│   │       │   └── MainUiState.kt
│   │       ├── components/
│   │       │   ├── recording/RecordButton.kt, RecordingCard.kt
│   │       │   └── history/HistoryCard.kt
│   │       └── theme/Theme.kt
│   │
│   ├── androidMain/kotlin/com/liftley/vodrop/
│   │   ├── data/
│   │   │   ├── audio/AudioRecorder.android.kt
│   │   │   └── cloud/CloudTranscriptionService.android.kt  # ★ Chirp 3 + Gemini
│   │   ├── service/RecordingService.kt      # Foreground service
│   │   └── service/RecordingCommandReceiver.kt
│   │
│   └── jvmMain/kotlin/com/liftley/vodrop/
│       └── (Desktop implementations - STT & cleanup via HTTP)
│
├── functions/src/index.ts    # Firebase Cloud Functions
└── gradle files
```

---

## 🔄 Data Flow

### Recording → Transcription → Polish

```
1. User taps Record
   └── MainViewModel.onRecordClick()
       └── RecordingSessionManager.startRecording()
           ├── AppState → Recording
           ├── ServiceController.startForeground()
           └── AudioRecorder.startRecording()

2. User taps Stop
   └── RecordingSessionManager.stopRecording()
       ├── AppState → Processing("Stopping...")
       ├── AudioRecorder.stopRecording() → ByteArray
       ├── AppState → Processing("Transcribing...")
       ├── TranscribeAudioUseCase()
       │   ├── Upload WAV to Firebase Storage
       │   ├── Call transcribeChirp Cloud Function
       │   └── (If AI Polish) Call cleanupText Cloud Function
       ├── TranscriptionRepository.save(text)
       └── AppState → Success(text)

3. UI recomposes with transcription result
```

---

## ☁️ Cloud Architecture

VoDrop uses Firebase Cloud Functions to keep API keys secure:

```
┌──────────────┐     ┌─────────────────────────────┐     ┌──────────────────┐
│   Android    │ ──▶ │  Firebase Cloud Functions   │ ──▶ │  Google Cloud    │
│     App      │     │  (transcribeChirp,          │     │  Speech-to-Text  │
│              │     │   cleanupText)              │     │  + Gemini API    │
└──────────────┘     └─────────────────────────────┘     └──────────────────┘
```

**Cloud Functions:**
- `transcribeChirp` - Calls Google Speech-to-Text V2 with Chirp 3 model
- `cleanupText` - Calls Gemini 3 Flash for AI cleanup

---

## 🎧 Audio Configuration

```kotlin
object AudioConfig {
    const val SAMPLE_RATE = 16000   // Standard for speech recognition
    const val CHANNELS = 1          // Mono
    const val BITS_PER_SAMPLE = 16  // 16-bit PCM
}
```

Audio is recorded as raw PCM, then WAV header is added before upload.

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug+
- JDK 17+
- Android SDK 34

### Build & Run

```bash
# Android Debug
./gradlew :composeApp:installDebug

# Android Release
./gradlew :composeApp:assembleRelease

# Desktop
./gradlew :composeApp:run
```

### Deploy Cloud Functions

```bash
cd functions
npm install
firebase deploy --only functions
```

---

## 📱 Platform Status

| Platform          | Status                               |
|-------------------|--------------------------------------|
| **Android**       | ✅ Fully functional                   |
| **Desktop (JVM)** | ✅ Functional (no background service) |
| **iOS**           | 📋 Placeholder (not implemented)     |

---

## 🔮 Roadmap

### Current (v1)
- [x] Voice recording with foreground service
- [x] Cloud transcription (Chirp 3)
- [x] AI Polish (Gemini 3 Flash)
- [x] Local history (SQLDelight)
- [x] Unified AppState architecture

### Planned (v2)
- [ ] Settings screen
- [ ] Cleanup style selection (Formal/Informal/Casual)
- [ ] Export to file/share
- [ ] Cross-device sync

---

## 📄 License

© 2026 Liftley. All rights reserved.

---

## 📞 Contact

For support: [support@liftley.com](mailto:support@liftley.com)