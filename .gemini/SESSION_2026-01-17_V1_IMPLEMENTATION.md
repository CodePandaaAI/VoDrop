# VoDrop v1 Implementation Session Summary
**Session Date:** January 17, 2026  
**Duration:** ~2 hours  
**Status:** In Progress

> **For Future AI Context:** This document contains the complete context of the v1 implementation session. Read this alongside `/docs/v1_guide.md` for full understanding of the project scope and decisions.

---

## 📋 Table of Contents

1. [Project Overview](#project-overview)
2. [Business Model & Pricing](#business-model--pricing)
3. [Technical Architecture](#technical-architecture)
4. [Files Created This Session](#files-created-this-session)
5. [Files Modified This Session](#files-modified-this-session)
6. [Current Project State](#current-project-state)
7. [Known Issues & Blockers](#known-issues--blockers)
8. [Cleanup Required](#cleanup-required)
9. [Next Steps](#next-steps)
10. [Key Decisions Made](#key-decisions-made)

---

## Project Overview

**VoDrop** is a cross-platform voice-to-text application built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**. The v1 launch focuses on Android with a simple, reliable core experience.

### Core Promise
```
Login → Speak → Transcribe → Copy → Done
```

### v1 Scope (What's IN)
- ✅ Cloud-based transcription (Groq Whisper Large v3)
- ✅ AI text cleanup (Gemini 2.5 Flash)
- ✅ Local history (SQLDelight)
- ✅ Google Sign-In (Firebase Auth)
- ✅ Subscription management (RevenueCat)
- ✅ Usage tracking (Firebase Firestore)
- ✅ Device restriction (1 device per account)
- ✅ Free trials (3 transcriptions)

### v1 Scope (What's OUT)
- ❌ Offline transcription
- ❌ Multiple subscription tiers
- ❌ Yearly plans
- ❌ Cloud history sync
- ❌ Onboarding flow
- ❌ Settings screen
- ❌ Account deletion
- ❌ Style selection UI (hardcoded to INFORMAL)

---

## Business Model & Pricing

### Final Pricing Decision (Confirmed)

| Plan | Price | Limit | Features |
|------|-------|-------|----------|
| **Free** | $0 | 3 transcriptions (lifetime) | Standard mode only |
| **Pro** | $2.99/month (~₹252 INR) | 120 minutes/month | Both modes (Standard + AI Polish) |

### Cost Analysis (Per User Per Month)

| API | Cost per 120 min | Notes |
|-----|-----------------|-------|
| Groq Whisper | $0.072 | $0.0006/min |
| Gemini 2.5 Flash | $0.046 | ~$0.00038/request |
| **Total Cost** | ~$0.12 | Per active Pro user |
| **Revenue** | $2.99 | Per Pro user |
| **Margin** | ~$2.87 | 96% margin |

### Why Capped Minutes (Not Unlimited)

1. **Predictable costs** - API-based model means we pay per use
2. **Prevents abuse** - No bots or resellers
3. **120 min is generous** - Average user needs ~30-60 min/month
4. **Marketing friendly** - "Up to 2 hours of voice-to-text"

### Device Restriction Policy

- **One active device per account** (stored in Firestore)
- When logging in on new device:
  - Show dialog: "Already logged in elsewhere"
  - Options: "Use This Device" (switch) or "Sign Out"
- If user clears app data, they must log in again

---

## Technical Architecture

### Tech Stack

| Layer | Technology |
|-------|------------|
| **UI** | Compose Multiplatform |
| **State** | StateFlow + MutableStateFlow |
| **DI** | Koin 4.x |
| **Database** | SQLDelight |
| **Auth** | Firebase Authentication |
| **User Data** | Firebase Firestore |
| **Subscriptions** | RevenueCat |
| **STT** | Groq Whisper API (Cloud) |
| **LLM** | Gemini 2.5 Flash API |
| **HTTP** | Ktor |

### Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                         MainActivity                             │
│  - Initializes Koin, Auth, Subscriptions                        │
│  - Syncs AccessManager state to ViewModel                       │
│  - Handles login/logout/purchase callbacks                      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         AccessManager                            │
│  - Combines: FirestoreManager + DeviceManager + SubscriptionMgr │
│  - Exposes: accessState (StateFlow<AccessState>)                │
│  - Handles: Device restriction, usage tracking                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         MainViewModel                            │
│  - Receives auth state from Activity                            │
│  - Controls recording flow                                      │
│  - Calls TranscribeAudioUseCase                                 │
│  - Notifies Activity on transcription complete (for tracking)  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      TranscribeAudioUseCase                      │
│  1. Send audio to Groq Whisper API                              │
│  2. If AI Polish mode: Send text to Gemini 2.5 Flash            │
│  3. Return cleaned text                                         │
└─────────────────────────────────────────────────────────────────┘
```

### Firestore Data Structure

```
/users/{userId}
├── freeTrialsRemaining: Int (starts at 3)
├── currentMonthUsageSeconds: Long
├── usageResetDate: String (ISO date "2026-02-01")
├── activeDeviceId: String (UUID)
├── createdAt: Long (timestamp)
└── lastActiveAt: Long (timestamp)
```

---

## Files Created This Session

### commonMain (Shared Code)

| File | Description |
|------|-------------|
| `data/firestore/UserData.kt` | Firestore user data model with usage tracking |
| `data/firestore/AccessController.kt` | ⚠️ Created but UNUSED - can delete |

### androidMain (Android-Specific)

| File | Description |
|------|-------------|
| `auth/AccessManager.kt` | Unified access control (login, device, usage) |
| `data/firestore/FirestoreManager.kt` | Firestore CRUD operations |
| `data/firestore/DeviceManager.kt` | Device ID generation & storage |

---

## Files Modified This Session

### Core Files

| File | Changes |
|------|---------|
| `ui/main/MainUiState.kt` | Simplified - removed CleanupStyle, removed showSettings, added statusText |
| `ui/main/MainViewModel.kt` | Added setAuth(), decrementTrials(), onTranscriptionComplete callback |
| `ui/main/MainScreen.kt` | Simplified - inline dialogs, removed external dialog components |
| `MainActivity.kt` | Integrated AccessManager, removed onboarding, added usage tracking |
| `di/PlatformModule.android.kt` | Added AccessManager, FirestoreManager, DeviceManager |
| `auth/AuthConfig.kt` | Added PRICE_MONTHLY_USD, PRICE_MONTHLY_INR constants |
| `auth/SubscriptionManager.android.kt` | Simplified - removed yearly plan |
| `build.gradle.kts` | Added firebase-firestore-ktx dependency |

### Deleted/To Delete

| File/Folder | Status |
|-------------|--------|
| `ui/components/dialogs/` | Deleted entire folder |
| `ui/settings/` | Deleted entire folder |
| `ui/onboarding/` (commonMain) | Deleted |
| `ui/onboarding/` (androidMain) | ⚠️ Still exists - needs deletion |
| `ui/components/common/LoadingButton.kt` | ⚠️ Empty file - needs deletion |
| `ui/components/common/VoDropTopBar.kt` | ⚠️ Empty file - needs deletion |

---

## Current Project State

### MainUiState.kt (50 lines)

```kotlin
data class MainUiState(
    // Recording
    val recordingPhase: RecordingPhase = RecordingPhase.IDLE,
    val transcriptionState: TranscriptionState = TranscriptionState.NotReady,
    val currentTranscription: String = "",
    val progressMessage: String = "",
    val error: String? = null,

    // History
    val history: List<Transcription> = emptyList(),
    
    // Mode
    val transcriptionMode: TranscriptionMode = TranscriptionMode.DEFAULT,
    val showModeSheet: Boolean = false,

    // Dialogs
    val deleteConfirmationId: Long? = null,
    val editingTranscription: Transcription? = null,
    val showUpgradeDialog: Boolean = false,

    // User
    val isLoggedIn: Boolean = false,
    val isPro: Boolean = false,
    val freeTrialsRemaining: Int = 3,

    // AI improvement
    val improvingId: Long? = null
) {
    val canTranscribe: Boolean get() = isLoggedIn && (isPro || freeTrialsRemaining > 0)
    val statusText: String get() = when { ... }
}
```

### TranscriptionMode (Simplified)

```kotlin
enum class TranscriptionMode(val displayName: String) {
    STANDARD("Standard"),
    WITH_AI_POLISH("AI Polish");
    companion object { val DEFAULT = STANDARD }
}
```

### Feature Status

| Feature | Working? | Notes |
|---------|----------|-------|
| Recording | ✅ | Android audio capture working |
| Cloud Transcription | ✅ | Groq Whisper API integrated |
| AI Polish | ✅ | Gemini 2.5 Flash integrated |
| Local History | ✅ | SQLDelight working |
| Copy to Clipboard | ✅ | Works |
| Edit Transcription | ✅ | Inline dialog |
| Delete Transcription | ✅ | Inline dialog with confirmation |
| Google Sign-In | ⚠️ | Was having "no credentials" error - needs verification |
| Firebase Firestore | ✅ | Integration complete |
| Device Restriction | ✅ | Logic implemented |
| Usage Tracking | ✅ | Firestore operations ready |
| RevenueCat SDK | ✅ | SDK integrated, needs Play Console products |
| Subscription Purchase | ❌ | Blocked - needs Play Console ($25 fee) |

---

## Known Issues & Blockers

### 1. Google Sign-In "No Credentials" Error
- **Previous Issue:** Credential Manager returning no credentials
- **Status:** May still be an issue - needs testing after cleanup
- **Resolution:** Verify client_id in `google-services.json` matches Firebase console

### 2. Play Console Not Connected
- **Issue:** RevenueCat needs Play Console to test real purchases
- **Workaround:** Test mode with `isPro = true` force-enabled
- **Blocker:** Requires $25 Google Play Developer fee

### 3. Gradle Cache Error
- **Error:** "Could not pack tree" during compilation
- **Resolution:** Run `gradlew clean` and rebuild

### 4. API Keys in Source Code
- **Issue:** Groq and Gemini API keys are hardcoded
- **Risk:** Security concern for production
- **Plan:** Move to backend before public release

---

## Cleanup Required

### Files to Delete

```
DELETE: commonMain/.../data/firestore/AccessController.kt
DELETE: commonMain/.../ui/components/common/LoadingButton.kt
DELETE: commonMain/.../ui/components/common/VoDropTopBar.kt
DELETE: androidMain/.../ui/onboarding/ (entire folder)
```

### Empty Folders to Remove

After file deletion, remove empty folders:
```
DELETE: commonMain/.../ui/components/common/ (if empty)
```

---

## Next Steps

### Immediate (Before Next Session)

1. [ ] Clean up unused files listed above
2. [ ] Run `gradlew clean` then rebuild
3. [ ] Test Google Sign-In flow
4. [ ] Verify Firestore writes working

### Short-Term (This Week)

5. [ ] Pay Google Play Developer fee ($25)
6. [ ] Create subscription product in Play Console
7. [ ] Connect RevenueCat to Play Console
8. [ ] Test full purchase flow

### Pre-Launch

9. [ ] Move API keys to secure backend
10. [ ] Create privacy policy
11. [ ] Create terms of service
12. [ ] App store assets (icons, screenshots)
13. [ ] Internal testing on Play Console

---

## Key Decisions Made

### 1. Pricing: $2.99/month with 120 min limit
- **Why:** Generous limit, predictable costs, good margin
- **Alternative considered:** Unlimited (rejected due to API costs)

### 2. One device per account
- **Why:** Prevents credential sharing, ensures revenue
- **Implementation:** Device ID stored in Firestore

### 3. Free trials: 3 lifetime (not per month)
- **Why:** Simple, prevents abuse via reinstall
- **Storage:** Firestore (not local)

### 4. Removed Settings & Onboarding
- **Why:** v1 simplicity - ship fast, iterate later
- **Consequence:** CleanupStyle hardcoded to INFORMAL

### 5. Removed CleanupStyle UI
- **Why:** Reduce complexity for v1
- **Code:** CleanupStyle enum still exists (used by TranscribeAudioUseCase)
- **Default:** INFORMAL style

### 6. Monthly only (no yearly)
- **Why:** Simpler billing, easier to manage
- **Future:** Can add yearly plan post-launch

---

## Reference Files

For complete context, also read:

| File | Description |
|------|-------------|
| `/docs/v1_guide.md` | Complete v1 scope and guidelines |
| `/composeApp/src/commonMain/.../ui/main/MainUiState.kt` | Current UI state definition |
| `/composeApp/src/commonMain/.../ui/main/MainViewModel.kt` | Business logic |
| `/composeApp/src/androidMain/.../auth/AccessManager.kt` | Access control logic |
| `/composeApp/src/androidMain/.../data/firestore/FirestoreManager.kt` | Firestore operations |

---

## RevenueCat Configuration

### Current Setup

- **API Key:** `test_VdhpzgqMRstecnlYlWMrmEypIkh` (sandbox)
- **Entitlement:** `pro`
- **Product ID:** `vodrop_pro_monthly`
- **Price:** $2.99/month

### To Complete

1. Create Play Console developer account
2. Create subscription product with matching ID
3. Connect RevenueCat to Play Console via Service Account
4. Test sandbox purchase

---

## Session End State

**Build Status:** ⚠️ Needs clean + rebuild  
**App Runnable:** Should be after cleanup  
**Core Features:** Complete  
**Subscriptions:** SDK ready, needs Play Console  
**Next Priority:** Clean up files, verify build, test sign-in

---

*Last Updated: January 17, 2026 at 17:11 IST*
