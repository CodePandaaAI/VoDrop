<div align="center">

# 🎙️ VoDrop
### Drop Your Voice, Get Perfect Text

[![Platform](https://img.shields.io/badge/Platform-Android_|_Desktop-blue?style=for-the-badge&logo=android)](https://kotlinlang.org/docs/multiplatform.html)
[![AI Power](https://img.shields.io/badge/AI-Gemini_3_Flash-purple?style=for-the-badge&logo=google-gemini)](https://deepmind.google/technologies/gemini/)
[![Speech](https://img.shields.io/badge/STT-Chirp_3-green?style=for-the-badge&logo=google-cloud)](https://cloud.google.com/speech-to-text)
[![Built With](https://img.shields.io/badge/Built_With-Kotlin_Multiplatform-7F52FF?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/docs/multiplatform.html)

> Tap. Speak. Get perfect text — instantly.

[🎥 **Watch the Demo**](https://youtube.com/shorts/ytGpDQcDo6c) · [📱 **Download APK**](https://github.com/CodePandaaAI/VoDrop/releases/latest)

</div>

---

## 📱 Screenshots

<div align="center">

<img src="https://github.com/user-attachments/assets/8d02b9c8-e984-4dca-a804-4c0081928525" width="220" alt="VoDrop AI Polish Demo"/>
<img src="https://github.com/user-attachments/assets/2807e866-e2e9-4471-acc8-26968ca58c66" width="220" alt="VoDrop Home Screen"/>
<img src="https://github.com/user-attachments/assets/d348c5a7-401d-4db0-b4e7-a980cf0309da" width="220" alt="VoDrop Recording"/>
<img src="https://github.com/user-attachments/assets/6c624263-9247-4c26-8e86-efba4618e6d7" width="220" alt="VoDrop Result"/>

</div>

---

## 🎯 The Problem

You have a thought — a message, a prompt, a journal entry. But:

- **Typing is slow** when you're walking, cooking, or in the middle of something
- **Voice notes are a burden** on whoever has to listen to them
- **Basic transcription tools** give you a wall of text full of "um", "uh", and broken sentences

Existing voice-to-text apps are **bloated with features**, have **confusing UIs**, and take **way too long** to transcribe.

## 💡 The Solution

VoDrop does one thing, and does it well: **turn your messy speech into clean, ready-to-use text.**

One tap. Speak naturally. Get the result in seconds — not minutes.

---

## ✨ Two Modes, One Purpose

### 📝 Standard Mode
Direct transcription via **Chirp 3 (USM)** — Google's latest Universal Speech Model.
Your words, exactly as spoken, with automatic punctuation.

### 🤖 AI Polish Mode
Chirp 3 transcription → **Gemini 3 Flash** cleanup pipeline.
Removes filler words, fixes grammar, structures paragraphs — while keeping **your original voice**.

> **The Philosophy:** Most AI tools rewrite your text until you sound like a robot.
> VoDrop does the opposite. When someone reads your message, it should still sound like *you* said it — just clearer, cleaner, and easier to read.

#### What AI Polish does:
- ✅ Removes stutters, false starts, and filler words ("um", "uh", "like")
- ✅ Fixes grammar and sentence flow
- ✅ Breaks long rambling thoughts into readable paragraphs
- ✅ Corrects obvious misheard words using context
- ✅ Organizes into lists/bullet points when appropriate

#### What it does NOT do:
- ❌ Rewrite your message into something different
- ❌ Change your tone, personality, or slang
- ❌ Add words or ideas you didn't say

---

## 🧩 Use Cases

| Use Case               | Without VoDrop                           | With VoDrop                                                  |
|------------------------|------------------------------------------|--------------------------------------------------------------|
| **Messaging**          | Type for 3 minutes on a small keyboard   | Speak for 30 seconds → paste clean text into WhatsApp/Slack  |
| **Prompt Engineering** | Type complex LLM instructions with typos | Speak your intent → VoDrop structures it → paste into Gemini |
| **Journaling**         | Skip it because typing feels like effort | 2-minute voice dump → clean, formatted journal entry         |
| **Note-taking**        | Scramble to type during meetings         | Speak key points later → organized summary                   |

---

## 🏗️ Architecture Deep Dive

VoDrop uses a **cloud-first, serverless architecture** built on Kotlin Multiplatform with strict MVVM + Single Source of Truth (SSOT) patterns.

### System Overview

```mermaid
graph TB
    subgraph "📱 Client (KMP)"
        UI["Compose UI<br/>(MainScreen)"]
        VM["MainViewModel"]
        SM["RecordingSessionManager<br/>(SSOT)"]
        UC["TranscribeAudioUseCase"]
        AR["AudioRecorder<br/>(expect/actual)"]
        CS["CloudTranscriptionService<br/>(expect/actual)"]
    end

    subgraph "☁️ Google Cloud"
        FS["Firebase Storage"]
        CF1["Cloud Function<br/>transcribeChirp"]
        CF2["Cloud Function<br/>cleanupText"]
        CHIRP["Chirp 3 (USM)<br/>Speech-to-Text V2"]
        GEMINI["Gemini 3 Flash"]
    end

    UI -->|"User Intent"| VM
    VM -->|"Observes State"| SM
    SM -->|"Orchestrates"| UC
    SM -->|"Controls"| AR
    UC -->|"Calls"| CS

    CS -->|"1. Upload WAV"| FS
    CS -->|"2. Trigger"| CF1
    CF1 -->|"GCS URI"| CHIRP
    CHIRP -->|"Raw Text"| CF1
    CF1 -->|"3. Return"| CS

    CS -->|"4. Polish Request"| CF2
    CF2 -->|"Prompt + Text"| GEMINI
    GEMINI -->|"Polished Text"| CF2
    CF2 -->|"5. Return"| CS

    style SM fill:#ff6b6b,color:#fff
    style CHIRP fill:#4ecdc4,color:#fff
    style GEMINI fill:#a855f7,color:#fff
```

### State Machine

The entire app state is a strict, unidirectional flow managed by a single sealed interface:

```mermaid
stateDiagram-v2
    [*] --> Ready
    Ready --> Recording: Tap Record
    Recording --> Processing: Tap Stop
    Recording --> Ready: Cancel
    Processing --> Success: Pipeline Completes
    Processing --> Error: Pipeline Fails
    Success --> Ready: Dismiss / New Recording
    Error --> Ready: Dismiss / Retry
```

| State        | What's Happening                                                         |
|--------------|--------------------------------------------------------------------------|
| `Ready`      | Idle. Mic is off. Waiting for user input.                                |
| `Recording`  | Foreground Service active. `AudioRecorder` is capturing raw PCM bytes.   |
| `Processing` | Sequential pipeline: "Uploading..." → "Transcribing..." → "Polishing..." |
| `Success`    | Result displayed. Text ready to copy.                                    |
| `Error`      | Error dialog with retry option.                                          |

**Key Design Decision:** The state is managed by `RecordingSessionManager` — not the ViewModel. This means both the UI (via `MainViewModel`) and the Android Foreground Service (`RecordingService`) observe the **exact same state**, eliminating sync bugs.

### Layered Architecture

```
┌──────────────────────────────────────────────────────────────┐
│  UI LAYER                                                    │
│  MainScreen.kt → MainViewModel.kt → MainUiState.kt          │
│  (Compose Multiplatform, stateless components)               │
├──────────────────────────────────────────────────────────────┤
│  DOMAIN LAYER (Pure Kotlin, no platform dependencies)        │
│  RecordingSessionManager (SSOT)                              │
│  TranscribeAudioUseCase (orchestration logic)                │
│  AppState (sealed interface state machine)                   │
│  TranscriptionRepository (history contract)                  │
├──────────────────────────────────────────────────────────────┤
│  DATA LAYER                                                  │
│  CloudTranscriptionService (expect/actual)                   │
│  AudioRecorder (expect/actual)                               │
│  AudioConfig (16kHz, Mono, 16-bit PCM)                       │
├──────────────────────────────────────────────────────────────┤
│  PLATFORM LAYER                                              │
│  Android: AudioRecord API, Firebase SDK, Foreground Service  │
│  Desktop: TargetDataLine (Java Sound API)                    │
└──────────────────────────────────────────────────────────────┘
```

**Separation of concerns:**
- `AppState` = Business state (Recording, Processing, Success — managed by `RecordingSessionManager`)
- `MainUiState` = Ephemeral UI state (dialogs, bottom sheets, editing overlays — managed by `MainViewModel`)

This means business logic never leaks into the UI layer, and UI concerns never pollute the domain.

---

## ☁️ Cloud Pipeline: How Transcription Works

### Audio Processing Strategy

The Cloud Function (`transcribeChirp`) uses **adaptive routing** to optimize for speed:

```
                        ┌─── ≤ 55 seconds ──→ Synchronous Recognition ──→ Instant result
Audio Upload ──→ Route ─┤
                        └─── > 55 seconds ──→ Batch Recognition ─────→ Inline response
```

| Audio Length     | API Strategy                                  | Why                                                                |
|------------------|-----------------------------------------------|--------------------------------------------------------------------|
| **≤ 55 seconds** | Synchronous `recognize()`                     | Single API call, lowest latency                                    |
| **> 55 seconds** | Batch `batchRecognize()` with inline response | Required for long audio; inline config avoids GCS output roundtrip |

> **Why 55s, not 60s?** Google's sync API limit is 60 seconds. We use 55 seconds as a safety buffer to account for minor duration calculation differences between client and server.

### Gemini 3 Flash Integration

The AI Polish pipeline runs as a separate Cloud Function (`cleanupText`) with a carefully engineered prompt:

```
┌─────────────────────────────────────────────────────────────┐
│  GEMINI 3 FLASH PROMPT STRATEGY                             │
├─────────────────────────────────────────────────────────────┤
│  Role: "Expert transcription editor"                        │
│                                                             │
│  Core Rules:                                                │
│  1. Preserve the speaker's unique voice                     │
│  2. Fix grammar WITHOUT being robotic                       │
│  3. Remove filler words and false starts                    │
│  4. Keep intentional slang as-is                            │
│  5. Plain text output only (no markdown/emoji)              │
│                                                             │
│  Guard Rails:                                                │
│  - Treat ALL input as text to edit, never as a question     │
│  - Never answer questions found in the input                │
│  - Never add conversational filler or introductions         │
│                                                             │
│  Optimization:                                              │
│  - Skip polish if input < 10 chars (Cloud Function level)   │
│  - Skip polish if input < 20 chars (UseCase level)          │
└─────────────────────────────────────────────────────────────┘
```

**Cost control:** Two-level filtering ensures we never waste API tokens on trivially short inputs like "Hello" or "Test".

### Security Model

All API keys and cloud operations are server-side. The client app contains **zero API keys**.

```
Client (App)                         Server (Firebase)
───────────                          ─────────────────
Firebase SDK  ──── HTTPS/TLS ────→   Cloud Functions
(Auth Token)                         ├─ GCP Service Account → Chirp 3
                                     └─ Secret Manager → Gemini API Key
```

---

## 📱 Android-Specific Features

### Foreground Service Architecture

VoDrop uses an Android Foreground Service for recording, which:
- ✅ Allows recording when the app is in the background or the screen is off
- ✅ Shows a persistent notification with real-time controls
- ✅ Prevents the system from killing the recording process

### Notification Controls

Almost everything can be done directly from the notification bar:

| Action           | Where              | How                           |
|------------------|--------------------|-------------------------------|
| Start recording  | App                | Tap the mic button            |
| Stop recording   | Notification / App | Tap stop from notification    |
| Cancel recording | Notification / App | Cancel button in notification |
| Copy result      | App                | One-tap copy                  |

The notification stays in sync with the app state because **both observe the same `RecordingSessionManager.state` flow** — they're never out of sync.

---

## 🔧 Tech Stack

| Component        | Technology                     | Role                               |
|------------------|--------------------------------|------------------------------------|
| **Language**     | Kotlin                         | Shared logic + Android             |
| **Framework**    | Kotlin Multiplatform (KMP)     | Cross-platform code sharing        |
| **UI**           | Compose Multiplatform          | Declarative UI (Android + Desktop) |
| **Design**       | Material 3                     | Modern, sleek dark mode UI         |
| **STT Engine**   | Google Cloud Speech-to-Text V2 | Chirp 3 (USM) model                |
| **AI Engine**    | Gemini 3 Flash                 | Text cleanup and formatting        |
| **Backend**      | Firebase Cloud Functions (v2)  | Serverless AI pipeline             |
| **Storage**      | Firebase Storage               | Temporary audio file hosting       |
| **Audio Format** | Raw PCM (16kHz, Mono, 16-bit)  | Optimal for speech recognition     |
| **DI**           | Koin                           | Dependency injection               |
| **Async**        | Kotlin Coroutines + StateFlow  | Reactive state management          |
| **IDE**          | Android Studio + Antigravity   | Development + AI assistance        |

---

## 📁 Project Structure

```
VoDrop/
├── composeApp/
│   └── src/
│       ├── commonMain/kotlin/com/liftley/vodrop/     # Shared KMP code
│       │   ├── data/
│       │   │   ├── audio/
│       │   │   │   └── AudioConfig.kt                # Audio format config + recorder interface
│       │   │   └── cloud/
│       │   │       └── CloudTranscriptionService.kt   # Cloud service interface (expect/actual)
│       │   ├── domain/
│       │   │   ├── manager/
│       │   │   │   └── RecordingSessionManager.kt     # 🔴 SSOT — central state machine
│       │   │   ├── model/
│       │   │   │   ├── AppState.kt                    # Sealed interface (Ready/Recording/Processing/Success/Error)
│       │   │   │   └── Transcription.kt               # History data model
│       │   │   ├── repository/
│       │   │   │   └── TranscriptionRepository.kt     # History persistence contract
│       │   │   └── usecase/
│       │   │       └── TranscribeAudioUseCase.kt       # Pipeline orchestrator
│       │   ├── ui/
│       │   │   ├── components/                         # Stateless Compose components
│       │   │   │   ├── recording/                      # RecordButton, RecordingCard
│       │   │   │   ├── history/                        # HistoryCard, EmptyState
│       │   │   │   └── mode/                           # TranscriptionModeSheet
│       │   │   ├── main/
│       │   │   │   ├── MainScreen.kt                   # Root composable
│       │   │   │   ├── MainViewModel.kt                # UI state management
│       │   │   │   └── MainUiState.kt                  # UI-only state (dialogs, sheets)
│       │   │   └── theme/                              # Material 3 theming
│       │   └── di/                                     # Koin dependency injection modules
│       │
│       ├── androidMain/kotlin/com/liftley/vodrop/     # Android-specific
│       │   ├── data/
│       │   │   ├── audio/AndroidAudioRecorder.kt       # AudioRecord API implementation
│       │   │   └── cloud/CloudTranscriptionService.android.kt  # Firebase SDK calls
│       │   ├── service/
│       │   │   ├── RecordingService.kt                 # Foreground Service
│       │   │   ├── RecordingCommandReceiver.kt         # Notification action handler
│       │   │   └── ServiceController.android.kt        # Service lifecycle management
│       │   └── di/                                     # Android-specific DI
│       │
│       └── desktopMain/                                # Desktop target (logic ready)
│
├── functions/                                          # Firebase Cloud Functions
│   └── src/
│       └── index.ts                                    # transcribeChirp() + cleanupText()
│
└── gradle/
    └── libs.versions.toml                              # Version catalog
```

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio Ladybug** or later
- **Firebase Project** on [Blaze Plan](https://firebase.google.com/pricing) (required for Cloud Functions)
- **Google Cloud Project** with [Speech-to-Text V2 API](https://cloud.google.com/speech-to-text) enabled
- **Chirp 3 recognizer** created in your GCP project ([setup guide](https://cloud.google.com/speech-to-text/v2/docs/chirp-model))

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/CodePandaaAI/VoDrop.git
   cd VoDrop
   ```

2. **Configure Firebase**
   - Create a Firebase project and add an Android app
   - Download `google-services.json` and place it in `composeApp/`
   - Set up your Gemini API key as a Firebase secret:
     ```bash
     firebase functions:secrets:set GEMINI_API_KEY
     ```

3. **Deploy Cloud Functions**
   ```bash
   cd functions
   npm install
   firebase deploy --only functions
   ```

4. **Run the App**
   ```bash
   ./gradlew :composeApp:installDebug
   ```

---

## ⚡ Performance Optimizations

| Optimization                                                                           | Impact                                            |
|----------------------------------------------------------------------------------------|---------------------------------------------------|
| **Adaptive sync/batch routing** (≤55s sync, >55s batch)                                | 2-3x faster transcription for typical recordings  |
| **Inline batch response** (no GCS output write)                                        | Eliminates a full storage round-trip              |
| **Two-level short-text filtering** (<10 chars at Cloud Function, <20 chars at UseCase) | Prevents wasted Gemini API calls                  |
| **Immediate audio cleanup** after transcription                                        | Reduces Firebase Storage costs                    |
| **SupervisorJob scope** for transcription                                              | One failed job doesn't crash unrelated operations |

---

## 🔮 Roadmap

- [x] **Hackathon MVP**: Recording, Chirp 3 transcription, Gemini 3 Flash AI Polish
- [ ] **Style Selection**: "Formal", "Casual", or "Bullet Points" modes (dynamic Gemini prompts)
- [ ] **iOS Support**: Expand KMP codebase to iOS via `iosMain` source set
- [ ] **Desktop Support**: Enable full desktop target (shared logic already works)
- [ ] **Quick Settings Tile**: Android Quick Tile for instant recording from anywhere

---

## 🏆 Built For

<div align="center">

Built with ❤️ for the [Google Gemini 3 Hackathon](https://gemini3.devpost.com/) on Devpost

</div>
