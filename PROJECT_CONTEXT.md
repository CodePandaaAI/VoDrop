# VoDrop - Project Context & Motive

> **Last Updated:** February 4, 2026  
> **Version:** Hackathon Ready (v1.0)

---

## 🎯 Project Motive

### Why VoDrop Exists

**The Problem**: Voice-to-text on mobile is frustrating.

1. **Gboard voice typing** - Poor accuracy for anything beyond 10-15 seconds
2. **WhisperFlow/other solutions** - Not available on Android, or paid
3. **Raw transcriptions** - Full of "um", "uh", stutters that need manual cleanup
4. **Complexity** - Most apps have too many features, too many buttons

**The Solution**: VoDrop is intentionally simple.

```
Record → Transcribe → (Optional AI Polish) → Copy → Done
```

No accounts required (for hackathon version). No settings to configure. No learning curve.

---

## 📱 Core Philosophy

### 1. Simplicity Over Features

VoDrop does ONE thing well: voice-to-text with optional AI cleanup.

We deliberately avoid:
- ❌ Complex settings panels
- ❌ Multiple subscription tiers
- ❌ Cloud sync (v1)
- ❌ Social features
- ❌ Multi-language (v1)

### 2. Speed Over Perfection

Users want their text NOW, not in 30 seconds:
- One-tap recording
- No model downloads (cloud STT)
- Results in 3-5 seconds for typical recordings

### 3. Clean Architecture Over Clever Code

The codebase prioritizes:
- **Readability** - New developers can understand flow in minutes
- **Single Source of Truth** - One `AppState`, one place to debug
- **Minimal Layers** - ViewModel is thin, doesn't translate state

---

## 🏗️ Architecture Decisions

### Why Unified `AppState`?

**Before (Old Architecture):**
```
AudioRecorder → RecordingStatus
RecordingService → observed status, commanded SessionManager
SessionManager → SessionState 
ViewModel → MicPhase (translated from SessionState)
UI → observed MicPhase
```

**Problem**: State was scattered across 4 different classes. Race conditions, circular dependencies, hard to debug.

**After (Current Architecture):**
```
SessionManager → AppState (SSOT)
ViewModel → exposes AppState directly (no translation)
UI → observes AppState
AudioRecorder → pure bytes (no state)
RecordingService → pure observer (no commanding)
```

**Benefits**:
- One place to look for state
- No translation bugs
- Easy to add new states
- Simple debugging

### Why Cloud STT (Not Local Whisper)?

We initially tried local Whisper.cpp but:
1. Hung indefinitely on real Android devices
2. Required complex NDK builds per architecture
3. Debugging native crashes is extremely painful

Cloud STT (Chirp 3) provides:
- 99%+ accuracy
- Works immediately (no model downloads)
- Simple debugging (just HTTP logs)
- Secure (API keys in Cloud Functions)

---

## 🔧 Key Components

| Component                   | Responsibility                                            |
|-----------------------------|-----------------------------------------------------------|
| `RecordingSessionManager`   | **SSOT** - Owns AppState, orchestrates recording flow     |
| `AudioRecorder`             | **Pure byte recorder** - No state, just records PCM bytes |
| `CloudTranscriptionService` | **Unified cloud** - Transcription (Chirp 3) + AI Polish (Gemini) |
| `RecordingService`          | **Pure observer** - Shows notification based on AppState  |
| `ServiceController`         | **Platform abstraction** - Start/stop foreground service  |
| `TranscribeAudioUseCase`    | **Orchestrator** - Uses CloudTranscriptionService         |
| `MainViewModel`             | **Thin UI layer** - Exposes AppState, handles UI events   |

---

## 🎯 Design Principles

### 1. State Flows Down, Actions Flow Up

```
AppState (SessionManager)
    ↓ observes
ViewModel
    ↓ collectAsState
UI Composables
    ↓ user interaction
Actions (onRecordClick, onCancel)
    ↓ calls
ViewModel
    ↓ delegates
SessionManager
    ↓ updates
AppState ← cycle continues
```

### 2. Components Should Be "Dumb"

- **AudioRecorder**: Just records bytes. Doesn't know about app state.
- **RecordingService**: Just shows notifications. Doesn't command anyone.
- **UI Composables**: Just render state. Business logic forbidden.

### 3. One Source of Truth Per Domain

- **Recording state**: `SessionManager.state` (AppState)
- **Transcription mode**: `SessionManager.currentMode`
- **History**: `TranscriptionRepository.getAllTranscriptions()`
- **UI dialogs**: `MainUiState` (ViewModel)

### 4. Reactive UI & Service
Both the **UI** (Jetpack Compose) and the **Foreground Service** are pure consumers of `AppState`.
- The Service automatically updates the notification when `AppState` changes to `Recording` or `Processing`.
- The UI automatically shows the waveform or spinner.
- Neither component knows about the other; they only verify the `SessionManager`.

---

## 📊 State Diagram

```
         ┌─────────────────┐
         │                 │
         │     Ready       │ ◄─── Initial state / After cancel / After clear
         │                 │
         └────────┬────────┘
                  │ startRecording()
                  ▼
         ┌─────────────────┐
         │                 │
         │   Recording     │ ◄─── User is speaking
         │                 │
         └────────┬────────┘
                  │ stopRecording()
                  ▼
         ┌─────────────────┐
         │                 │
         │   Processing    │ ◄─── "Transcribing...", "Polishing..."
         │   (message)     │
         └────────┬────────┘
                  │
         ┌───────┴───────┐
         ▼               ▼
┌─────────────────┐ ┌─────────────────┐
│                 │ │                 │
│    Success      │ │     Error       │
│    (text)       │ │    (message)    │
│                 │ │                 │
└─────────────────┘ └─────────────────┘
         │                   │
         └─────────┬─────────┘
                   │ resetState() / clearError()
                   ▼
              ┌─────────────────┐
              │     Ready       │
              └─────────────────┘
```

---

## 🚀 Evolution Summary

| Phase       | What Changed                                                |
|-------------|-------------------------------------------------------------|
| **Phase 1** | Started with local Whisper.cpp (abandoned - hung on device) |
| **Phase 2** | Moved to Groq Whisper API (worked but insecure keys)        |
| **Phase 3** | Firebase + Chirp 3 + Gemini (separate STT + cleanup)        |
| **Phase 4** | Unified AppState refactor (simplified state management)     |
| **Phase 5** | Unified CloudTranscriptionService (merged STT + AI Polish)  |
| **Phase 6** | Documentation Overhaul & Hackathon Polish (Ready for Submission) |

---

## 👤 Developer Preferences

- Prefers **understanding** over copy-paste solutions
- Values **simplicity** - if code is complex, it's probably wrong
- Manual file updates preferred over automated edits
- 16kHz audio sample rate (standard for speech recognition)

---

*This document provides context for AI assistants and new developers to understand the "why" behind VoDrop's architecture.*
