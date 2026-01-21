Below is a **clean, copy-paste ready Markdown (.md) file** that captures **everything we discussed**, **locks scope**, and gives you a **clear first-launch roadmap**.

This document is intentionally strict.
Anything **not listed here is OUT of scope** for v1.

---

# VoDrop — First Launch Roadmap (Locked Scope)

> **Goal:**
> Ship a small, correct, fast, and reliable v1 of VoDrop with **zero scope creep**, **clear monetization**, and **no architectural debt**.

---

## 1. Core Product Definition (v1)

**VoDrop v1 is a cloud-based voice-to-text utility with limited free trials and a paid Pro version.**

**Core promise:**

```
Login → Speak → Transcribe → Copy → Done
```

No extra intelligence, no over-automation, no experiments.

---

## 2. Authentication Strategy (FINAL DECISION)

### ✅ Login is REQUIRED before using the app

**Reason:**

* Prevents free-trial abuse via app data clearing
* Allows free-trial count to be tied to a real user
* Allows subscription to be linked to an account

### Authentication method

* Google Sign-In
* Firebase Authentication
* UID is the permanent user identifier

**User cannot use the app unless logged in.**

---

## 3. Monetization Model (FINAL)

### 3.1 Free Trial Rules

* Every new user gets **exactly 3 free trials**
* A “trial” = **one transcription**

    * Applies to:

        * Standard
        * Standard + AI
* Trials are **account-bound**
* Trials are **never reset**
* Clearing app data **must not reset trials**

### 3.2 After Trials End

* User is **blocked from transcription**
* App shows **Upgrade to Pro** dialog
* No background usage allowed
* No partial access

### 3.3 Pro Subscription

* Monthly subscription only (v1)
* Pro status:

    * `true` → unlimited usage
    * `false` → blocked if trials = 0
* Pro is **linked to the user account**
* When subscription expires:

    * Pro → false
    * Trials remain 0
    * User must renew

---

## 4. Technology Responsibility Split (IMPORTANT)

### 4.1 Firebase Authentication

Handles:

* Google Sign-In
* User identity (`uid`)
* Login persistence

Does NOT handle:

* Subscriptions
* Usage counting

---

### 4.2 RevenueCat

Handles:

* Subscription purchase
* Renewal
* Expiry
* Refunds
* Cross-device subscription sync

RevenueCat answers **only one question**:

```
Is this user Pro right now? (true / false)
```

RevenueCat does NOT:

* Track free trials
* Track usage
* Replace Firebase Auth

---

### 4.3 Free Trial Count (v1)

* Stored locally (DataStore / SharedPreferences)
* Keyed by Firebase UID
* Example:

```kotlin
userIdremainingFreeUses = 3
```

This is acceptable for v1.

---

## 5. Access Logic (MUST BE FOLLOWED)

### 5.1 Can User Transcribe?

```kotlin
canTranscribe = isPro || remainingFreeUses > 0
```

### 5.2 Before Transcription Starts

* If `canTranscribe == false`

    * Show Upgrade Dialog
    * Abort action

### 5.3 After Successful Transcription

* If user is NOT Pro:

    * `remainingFreeUses--`
    * Persist value

---

## 6. Transcription Modes (v1)

### Mode 1 — Standard

* Cloud Whisper transcription
* Basic rule-based cleanup
* Counts as 1 usage

### Mode 2 — Standard + AI

* Cloud Whisper transcription
* Gemini cleanup
* Counts as 1 usage

No additional modes allowed.

---

## 7. History (v1)

### Included

* Local history storage only
* SQLDelight
* Editable
* Deletable

### NOT included

* Cloud sync
* Account-based history
* Backup / restore

History does NOT affect billing or usage limits.

---

## 8. UI Principles (STRICT)

### Must Be

* Simple
* Fast
* Minimal
* Reliable
* No clutter

### UI Scope (Allowed)

* Main screen
* Record button
* Mode selector (Standard / Standard + AI)
* History list
* Upgrade dialog
* Login screen
* Settings (minimal)

### UI Scope (NOT Allowed)

* Onboarding flows
* Multiple paywalls
* Advanced settings
* AI configuration panels
* Feature discovery screens

---

## 9. API Key Security Plan

### v1 (Closed / Early Launch)

* API keys may live in app temporarily
* Hard spending limits must be set
* Usage must be monitored daily

### Before Public Release

* Move AI API calls to backend
* App → Backend → AI Provider
* API keys removed from app

---

## 10. v1 Feature Checklist (LOCKED)

### Authentication

* [ ] Google Sign-In required
* [ ] Firebase Auth integration

### Access Control

* [ ] 3 free trials per account
* [ ] Trial decrement on success
* [ ] Block usage after trials end
* [ ] Pro unlock via RevenueCat

### Transcription

* [ ] Cloud Whisper transcription
* [ ] Standard mode
* [ ] Standard + AI mode

### History

* [ ] Local history storage
* [ ] Edit / delete

### UI

* [ ] Minimal main screen
* [ ] Clear state feedback
* [ ] No unnecessary dialogs

### Stability

* [ ] No crashes
* [ ] Proper error handling
* [ ] Clean lifecycle management

---

## 11. Explicitly Out of Scope (v1)

These **must NOT exist** in the first launch:

* Offline Whisper on Android
* Multiple subscription tiers
* Yearly plans
* Usage analytics dashboard
* Cloud history sync
* Account deletion flows
* Advanced AI features
* Prompt customization

---

## 12. Success Criteria for First Launch

VoDrop v1 is considered **successful** if:

* Login works 100%
* Trial logic cannot be bypassed easily
* Subscription correctly toggles Pro
* Transcription is fast and accurate
* UI feels lightweight and stable
* No unexpected costs occur

---

## 13. Core Rule (Do Not Forget)

> **Ship a small, correct product first.
> Add power only after trust is earned.**

---

**End of Document — Scope Locked**

---