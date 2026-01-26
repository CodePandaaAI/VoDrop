# VoDrop Project Evolution & Context

> **Date**: January 26, 2026
> **Session Goal**: Architectural Refactor (SSOT), User-Led Code Review/Learning, and Critical Bug Fixes.

## 1. Architectural Foundation (The "SSOT" Refactor)
We successfully moved away from the "ViewModel doing everything" anti-pattern to a clean **Single Source of Truth (SSOT)**.

- **New Component**: `RecordingSessionManager` (Singleton).
    - **Responsibility**: Manages the entire lifecycle of recording, processing, and error handling.
    - **State**: Exposes a single `StateFlow<SessionState>` (Idle, Recording, Processing, Success, Error).
    - **Safety**: Robustly handles race conditions (optimistic updates) and errors (try-catch around OS calls).
- **AudioRecorder**: Refactored to be "dumb". It just records and emits raw status. It no longer manages app state.
- **RecordingService**: Now purely a "Foreground Service" holder. It observes `RecordingSessionManager` to update the notification UI. It does **not** drive logic.
- **MainViewModel**: Reduced to a simple "View Mapper". It just exposes `RecordingSessionManager.state` to the UI.

### Critical Fixes in this Layer
- **Infinite Recursion**: Fixed a bug where `RecordingService` started `AudioRecorder`, which started `Service`, ad infinitum. Added `EXTRA_FROM_RECORDER` flag to break the cycle.
- **Race Conditions**: Added optimistic state updates in `startRecording()` to prevent double-taps crashing the media recorder.

## 2. User-Led Code Review & UI Refactor
The user (you) took the lead in reviewing and refactoring the UI Layer to learn Jetpack Compose patterns.

### Achievements
- **Design System**: Introduced `Dimens` object for consistent spacing (replacing magic `16.dp` numbers).
- **Reusable Components**:
    - `ExpressiveIconButton`: Standardized styling for consistent usage.
    - `HistoryCardButton`: Refactored `HistoryCard` actions to use this shared component (DRY principle).
    - `TranscriptionModeBox`: Cleaned up the TopAppBar mode selector.
- **Theming**: Refined `Theme.kt` and `VoDropShape` to match Material 3 Expressive guidelines.

### Current Status of Review
- **Completed**: UI Layer (Composables, Theme, Components).
- **Next Up**: View Layer (ViewModel & State) and Business Logic. The user plans to continue this deep-dive in future sessions.

## 3. Critical Bug Fix: Gemini Cleanup Crash
**Issue**: The "AI Polish" features suddenly stopped working, crashing with `FirebaseNoSignedInUserException`.
- **Root Cause**: The Firebase Android SDK defaults to requiring a "Handshake" (Auth/App Check) before talking to Cloud Functions. A previous anonymous session likely expired, causing the failure on fresh installs.
- **Fix**: Implemented `ensureAuth()` in `AndroidFirebaseFunctionsService`.
    - It silently signs the user in via **Anonymous Auth** before every request.
    - This ensures a valid user context always exists without user intervention.

## 4. Build & Dependency Cleanup
We audited `build.gradle.kts` and removed unused libraries to reduce bloat.

- **Removed**:
    - RevenueCat (Payments stripped for Hackathon).
    - Credential Manager / GoogleId (Not using Google Sign-In UI).
    - Play Services Auth (Legacy).
    - Firebase Firestore & Storage (Client-side libraries not needed; we use Cloud Functions for logic).
- **Kept**:
    - `firebase-auth-ktx` (For Anonymous Auth).
    - `firebase-functions-ktx` (For Gemini/Cloud Calls).

## 5. Next Steps for AI Context
If picking up from here:
1.  **Continue Code Review**: The user wants to analyze the **ViewModel** and **Domain** layers next. Be a "Learning Partner"—explain concepts deeply rather than just changing code.
2.  **Verify UI Logic**: Ensure the new `Dimens` and components scale well across different screen sizes if needed.
3.  **Monitor Auth**: Anonymous Auth is working, but ensure Firebase Console remains configured to allow it.

## Key Files Created/Modified
- `RecordingSessionManager.kt`: The brain of the operation.
- `AndroidFirebaseFunctionsService.kt`: The bridge to AI (now with auto-auth).
- `MainUiState.kt` / `MainViewModel.kt`: The clean View layer.
- `Ui Components`: `HistoryCard.kt`, `RecordingCard.kt`, `Dimens.kt`.
