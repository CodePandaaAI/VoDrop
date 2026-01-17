# AI Developer Prompt: VoDrop - Kotlin Multiplatform (KMP) Project Kickoff

**Context:** I am starting a new Kotlin Multiplatform (KMP) project named **VoDrop**. VoDrop is a privacy-focused, high-performance voice-to-text utility designed to work across Android, iOS, and Desktop. The core philosophy is **100% offline processing** for speed and privacy.

**Goal:** To implement **Phase 1: KMP Foundation & Core Logic** of the project.

---

## 1. Technical Stack & Architecture

| Component          | Technology                      | Implementation Detail                                                         |
|:-------------------|:--------------------------------|:------------------------------------------------------------------------------|
| **Project Type**   | Kotlin Multiplatform (KMP)      | Targets: `android`, `ios`, `desktop` (JVM).                                   |
| **UI**             | Compose Multiplatform (CMP)     | Shared UI in `commonMain`.                                                    |
| **Core Engine**    | **Whisper.cpp**                 | Integrated via **Kotlin/Native C Interop** in a `commonMain` module.          |
| **Local Database** | **SQLDelight**                  | Used for storing transcription history and settings in a `commonMain` module. |
| **Project File**   | `VoDrop_Project_Brief_Final.md` | This file is included in the project root for full context.                   |

---

## 2. Phase 1: Implementation Tasks (Priority Order)

### Task 1: Whisper.cpp Integration (The Core)

The most critical task is setting up the C Interop for Whisper.cpp.

1.  **Setup C Interop:** Provide the necessary `cinterop` configuration in the KMP build files (`build.gradle.kts`) to link the C++ Whisper.cpp library.
2.  **`expect/actual` STT Service:** Create an `expect` interface in `commonMain` called `SpeechToTextEngine` with methods like `loadModel(path: String)`, `startRecording()`, and `stopRecording(): Flow<String>`.
3.  **Platform Implementation:** Provide the `actual` implementations for Android and iOS that use the C Interop to call the Whisper.cpp functions.

### Task 2: SQLDelight Database Setup

1.  **Schema Definition:** Define the initial SQLDelight schema (`.sq` file) in `commonMain` for a table named `Transcription` with columns: `id (INTEGER PRIMARY KEY)`, `timestamp (TEXT)`, and `text (TEXT)`.
2.  **Database Driver:** Provide the `expect/actual` mechanism for the database driver setup for Android (using `AndroidSqliteDriver`) and iOS (using `NativeSqliteDriver`).

### Task 3: Core Logic & Data Flow

1.  **Data Class:** Define a `Transcription` data class in `commonMain`.
2.  **Repository:** Create a `TranscriptionRepository` in `commonMain` that handles saving and retrieving data using the SQLDelight generated code.
3.  **Use Case:** Create a `RecordAndSaveTranscriptionUseCase` in `commonMain` that orchestrates the `SpeechToTextEngine` and the `TranscriptionRepository`.

### Task 4: Basic Shared UI (Compose Multiplatform)

1.  **Main Screen:** Create a simple `MainScreen` composable in `commonMain` that displays a large "Record" button and a status text (e.g., "Ready," "Listening...").
2.  **ViewModel:** Create a `MainViewModel` in `commonMain` that exposes the recording state and transcribed text.

---

## 3. Request to the AI

Based on the context and the detailed tasks above, please provide the following:

1.  **The necessary `build.gradle.kts` snippets** for setting up the Whisper.cpp C Interop and the SQLDelight dependencies for Android, iOS, and `commonMain`.
2.  **The Kotlin code for the `SpeechToTextEngine` `expect` interface** in `commonMain`.
3.  **The SQLDelight schema file content** (`Transcription.sq`).

This initial setup will allow me to quickly establish the KMP foundation and begin integrating the core offline STT functionality. Focus on providing clean, modern Kotlin code that adheres to KMP best practices.
