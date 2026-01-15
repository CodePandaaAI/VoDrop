# VoDrop - Session Summary
> **Last Updated:** 2026-01-15  
> **Status:** MVP Complete (Desktop Fully Functional, Android Recording Works)

---

## 🎯 Project Overview

**VoDrop** is a cross-platform voice-to-text transcription app built with Kotlin Multiplatform (KMP) and Compose Multiplatform. It allows users to record audio and transcribe it to text using OpenAI's Whisper model running locally on-device.

### Key Features
- 🎤 Voice recording with real-time amplitude visualization
- 📝 Speech-to-text transcription using Whisper.cpp
- 💾 Transcription history with SQLite persistence
- 📋 Copy, edit, and delete transcriptions
- ⚙️ Three AI model choices (Fast, Balanced, Quality)
- 🌐 One-time model download with progress tracking
- 🖥️ Works on Desktop (Windows/macOS/Linux) and Android

---

## 📱 Platform Status

| Platform          | Recording              | Transcription       | Status                         |
|-------------------|------------------------|---------------------|--------------------------------|
| **Desktop (JVM)** | ✅ Real (`javax.sound`) | ✅ Real (WhisperJNI) | **Production Ready**           |
| **Android**       | ✅ Real (`AudioRecord`) | ⚠️ Placeholder      | Recording works, STT needs JNI |
| **iOS**           | ⚠️ Placeholder         | ⚠️ Placeholder      | Requires Swift interop         |

---

## 🏗️ Architecture

### Design Pattern: **MVVM + Clean Architecture**

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                              │
│  ┌─────────────────┐  ┌─────────────────────────────────┐   │
│  │   MainScreen    │  │       ViewModel (MainViewModel) │   │
│  │   (Compose)     │←→│   - UI State Management         │   │
│  └─────────────────┘  │   - Business Logic Orchestration│   │
│                       └─────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│                     Domain Layer                             │
│  ┌─────────────────┐  ┌─────────────────────────────────┐   │
│  │   Transcription │  │        Repository               │   │
│  │   (Model)       │  │   (TranscriptionRepository)     │   │
│  └─────────────────┘  └─────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│                     Platform Layer                           │
│  ┌─────────────────┐  ┌─────────────────────────────────┐   │
│  │  AudioRecorder  │  │     SpeechToTextEngine          │   │
│  │  (expect/actual)│  │     (expect/actual)             │   │
│  └─────────────────┘  └─────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│                    Data Layer                                │
│  ┌─────────────────────────────────────────────────────────┐│
│  │                  SQLDelight (VoDropDatabase)            ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

### Dependency Injection: **Koin**
- Platform-agnostic DI framework
- Modules: `appModule` (common), `platformModule` (platform-specific)

---

## 📁 Project Structure

```
VoDrop/
├── composeApp/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/liftley/vodrop/
│       │   ├── App.kt                    # Root Composable
│       │   ├── audio/
│       │   │   └── AudioRecorder.kt      # Recording interface + AudioConfig
│       │   ├── di/
│       │   │   └── AppModule.kt          # Koin DI module
│       │   ├── model/
│       │   │   └── Transcription.kt      # Domain model
│       │   ├── repository/
│       │   │   └── TranscriptionRepository.kt
│       │   ├── stt/
│       │   │   └── SpeechToTextEngine.kt # STT interface + WhisperModel enum
│       │   └── ui/
│       │       ├── MainScreen.kt         # Main UI with dialogs
│       │       ├── MainViewModel.kt      # UI state management
│       │       └── theme/
│       │           └── Theme.kt          # Material 3 theming
│       │
│       ├── jvmMain/kotlin/com/liftley/vodrop/
│       │   ├── main.kt                   # Desktop entry point
│       │   ├── Platform.jvm.kt
│       │   ├── audio/
│       │   │   └── AudioRecorder.jvm.kt  # javax.sound implementation
│       │   ├── di/
│       │   │   └── PlatformModule.jvm.kt # JVM-specific DI
│       │   └── stt/
│       │       └── SpeechToTextEngine.jvm.kt  # WhisperJNI implementation
│       │
│       ├── androidMain/kotlin/com/liftley/vodrop/
│       │   ├── MainActivity.kt
│       │   ├── VoDropApplication.kt      # Application class with Koin init
│       │   ├── audio/
│       │   │   └── AudioRecorder.android.kt  # AudioRecord implementation
│       │   ├── di/
│       │   │   └── PlatformModule.android.kt
│       │   └── stt/
│       │       └── SpeechToTextEngine.android.kt  # Placeholder (needs JNI)
│       │
│       ├── iosMain/kotlin/com/liftley/vodrop/
│       │   ├── audio/
│       │   │   └── AudioRecorder.ios.kt  # Placeholder
│       │   └── stt/
│       │       └── SpeechToTextEngine.ios.kt  # Placeholder
│       │
│       └── commonMain/sqldelight/com/liftley/vodrop/db/
│           └── Transcription.sq          # SQL schema and queries
│
├── gradle/
│   └── libs.versions.toml                # Version catalog
│
└── settings.gradle.kts
```

---

## 🔧 Technology Stack

### Versions (as of 2026-01-15)

| Technology            | Version | Purpose                      |
|-----------------------|---------|------------------------------|
| Kotlin                | 2.1.21  | Main language                |
| Compose Multiplatform | 1.8.0   | UI framework                 |
| AGP                   | 8.7.3   | Android build                |
| Android compileSdk    | 36      | Android 16                   |
| Android targetSdk     | 36      | Android 16                   |
| Android minSdk        | 24      | Android 7.0+                 |
| Koin                  | 4.0.2   | Dependency Injection         |
| SQLDelight            | 2.0.2   | Database                     |
| Ktor                  | 3.0.3   | HTTP client (model download) |
| Lifecycle             | 2.8.7   | ViewModel                    |
| Coroutines            | 1.10.1  | Async operations             |
| WhisperJNI            | 1.7.1   | Desktop STT                  |
| Accompanist           | 0.36.0  | Android permissions          |
| Java Target           | 17      | JVM compatibility            |

---

## 🎙️ Audio Configuration

Whisper.cpp requires specific audio format:

```kotlin
object AudioConfig {
    const val SAMPLE_RATE = 16000      // 16kHz
    const val CHANNELS = 1              // Mono
    const val BITS_PER_SAMPLE = 16      // 16-bit PCM
    const val BYTES_PER_SAMPLE = 2      // Little-endian
}
```

### Recording Flow
1. User taps record button
2. `AudioRecorder.startRecording()` captures raw PCM audio
3. Real-time amplitude updates via `StateFlow<RecordingStatus>`
4. User taps stop → `stopRecording()` returns `ByteArray`
5. Audio data passed to `SpeechToTextEngine.transcribe()`

---

## 🤖 Whisper Models

### Available Models
| Model        | File                 | Size   | Quality | Use Case       |
|--------------|----------------------|--------|---------|----------------|
| **Fast**     | `ggml-tiny.en.bin`   | 75 MB  | ⭐⭐      | Quick notes    |
| **Balanced** | `ggml-small.en.bin`  | 466 MB | ⭐⭐⭐⭐    | Default choice |
| **Quality**  | `ggml-medium.en.bin` | 1.5 GB | ⭐⭐⭐⭐⭐   | Important work |

### Model Management
- Models downloaded from HuggingFace on first use
- Stored locally: `~/.vodrop/models/` (Desktop), `app filesDir` (Android)
- One-time download with progress tracking
- Model persists until user clears app storage

### Licensing
- **OpenAI Whisper**: MIT License ✅
- **whisper.cpp**: MIT License ✅
- **WhisperJNI**: MIT License ✅
- **Commercial use allowed** with license attribution

---

## 🗄️ Database Schema

```sql
CREATE TABLE TranscriptionEntity (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp TEXT NOT NULL,
    text TEXT NOT NULL
);

-- Queries
selectAll:   SELECT * FROM TranscriptionEntity ORDER BY id DESC;
insertItem:  INSERT INTO TranscriptionEntity (timestamp, text) VALUES (?, ?);
updateText:  UPDATE TranscriptionEntity SET text = ? WHERE id = ?;
deleteItem:  DELETE FROM TranscriptionEntity WHERE id = ?;
```

---

## 📱 UI Components

### Main Screen Features
1. **Top Bar**: App title + current model badge + settings button
2. **Recording Section**: Status text, record button, current transcription
3. **History Section**: Scrollable list of past transcriptions

### Dialogs
- **Model Selector**: First-launch model chooser (also accessible via settings)
- **Edit Transcription**: Modify saved text
- **Delete Confirmation**: Confirm before deleting

### Recording Button States
| State       | Color  | Icon |
|-------------|--------|------|
| Ready       | Purple | 🎤   |
| Listening   | Red    | ⏹    |
| Processing  | Gray   | ⏳    |
| Downloading | Blue   | ⬇️   |

---

## 🎨 Design Standards

### Theme
- Material 3 Design
- Dark/Light mode support
- Custom color scheme (Purple primary)

### Code Standards
1. **No deprecated APIs** - Using latest stable methods
2. **Reactive UI** - StateFlow for all state management
3. **Coroutines** - All I/O on `Dispatchers.IO`
4. **Exception handling** - Custom exceptions with clear messages
5. **Resource cleanup** - `release()` methods for native resources

---

## ⚠️ Known Limitations

### Android STT
The Android `SpeechToTextEngine` is a **placeholder**. To enable real transcription:
1. Build `whisper.cpp` with Android NDK
2. Create JNI bindings
3. Bundle `.so` files for arm64-v8a, x86_64

### iOS
Both audio recording and STT require native Swift implementation:
- Audio: Use `AVAudioEngine`
- STT: C-interop with `whisper.cpp`

---

## 🚀 Build & Run Commands

```bash
# Desktop
./gradlew :composeApp:run

# Android
./gradlew :composeApp:installDebug

# Clean build
./gradlew clean build
```

---

## 📋 Key Decisions Made

### 1. WhisperJNI for Desktop
**Why**: Pre-built library with native binaries for Windows/macOS/Linux. No compilation needed.

### 2. Three Model Choices
**Why**: Balance between download size, accuracy, and user choice. Users pick once, use forever.

### 3. SQLDelight over Room
**Why**: Multiplatform support. Works on iOS, Desktop, and Android.

### 4. Koin over Hilt
**Why**: Multiplatform DI. Hilt is Android-only.

### 5. Ktor for Downloads
**Why**: Multiplatform HTTP client. Supports streaming downloads to avoid OOM.

### 6. Kotlin 2.1.21 + Compose 1.8.0
**Why**: Required for AGP 8.7.3 compatibility. Stable K2 compiler.

---

## 📜 AndroidManifest Permissions

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## 🔮 Future Improvements

### High Priority
- [ ] Android Whisper.cpp JNI integration
- [ ] iOS implementation (Swift interop)
- [ ] Multi-language support

### Nice to Have
- [ ] Audio waveform visualization
- [ ] Export transcriptions (TXT, PDF)
- [ ] Keyboard shortcut for desktop
- [ ] Background transcription service

---

## 🏷️ Project Standards

### Code Quality
- ✅ Latest stable library versions
- ✅ No deprecated methods
- ✅ Type-safe sealed classes for state
- ✅ Proper error handling
- ✅ Clean architecture separation
- ✅ Reactive state management (StateFlow)
- ✅ Coroutines for async operations

### Compatibility
- ✅ Android 7.0+ (API 24)
- ✅ Android 16 (API 36) - latest
- ✅ Java 17 target
- ✅ Windows, macOS, Linux desktop

---

## 📞 Contact & License

**Company**: Liftley  
**App**: VoDrop  
**License**: Open-source components (MIT), App code proprietary

---

*This document should be updated whenever significant changes are made to the project architecture, dependencies, or features.*
