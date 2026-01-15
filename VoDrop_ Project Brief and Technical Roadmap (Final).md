# VoDrop: Project Brief and Technical Roadmap (Final)

---

## 1. Executive Summary

**VoDrop is a minimalist, 100% offline-first utility that makes voice-to-text dictation instant, private, and effortless across Android, iOS, and Desktop.** Our core mission is to provide the fastest, most private path from spoken thought to usable text on any platform. We are building a high-performance system utility, leveraging **Kotlin Multiplatform (KMP)** and **Compose Multiplatform** to maximize code sharing and ensure a consistent, high-quality experience everywhere.

The core user experience is defined by its simplicity and speed: **Tap → Speak → Stop → Text is Copied.** This document serves as the definitive guide for the project's vision, technical architecture, and multi-stage scaling roadmap.

---

## 2. App Vision & Core Philosophy

### 2.1. The Problem Solved

Existing dictation solutions are slow, require an internet connection, and raise significant privacy concerns by sending voice data to the cloud. Competitors like VoiceType and Wispr Flow are fundamentally cloud services.

### 2.2. The VoDrop Solution: Privacy-First Utility

VoDrop solves this by adhering to a strict philosophy of **speed, privacy, and cross-platform consistency.**

*   **Speed:** By processing voice data directly on the device using **Whisper.cpp**, transcription is nearly instantaneous with zero network latency.
*   **Privacy:** **No voice data ever leaves the user's device.** All processing is 100% offline, ensuring complete user privacy. This is VoDrop's primary competitive advantage.
*   **Consistency:** Leveraging KMP, the core logic and user interface are shared, guaranteeing a unified, high-quality experience across all supported platforms.

---

## 3. Technical Architecture & Stack

The project is built on a modern, multiplatform foundation designed for performance, maintainability, and future scalability.

| Component                 | Technology                           | Rationale                                                                                                            |
|:--------------------------|:-------------------------------------|:---------------------------------------------------------------------------------------------------------------------|
| **Architecture**          | **Kotlin Multiplatform (KMP)**       | Enables simultaneous development for Android, iOS, and Desktop, maximizing code reuse (business logic, data models). |
| **UI Framework**          | **Compose Multiplatform (CMP)**      | Shared declarative UI for Android, iOS, and Desktop, ensuring a consistent and modern look and feel.                 |
| **Speech-to-Text Engine** | **Whisper.cpp**                      | **MIT License** (Commercial-friendly), integrated via Kotlin/Native C Interop for high-accuracy, on-device STT.      |
| **Local Database**        | **SQLDelight**                       | Robust, type-safe, and truly multiplatform solution for storing transcription history and settings.                  |
| **Monetization**          | Google Play Billing / Apple StoreKit | Platform-specific billing integrated via KMP's `expect/actual` mechanism.                                            |

---

## 4. Adaptive UI Strategy: Floating UI Implementation

VoDrop's signature feature is its intelligent, adaptive interface that provides instant access without opening the main app. The implementation is tailored to each platform's best practices.

| Platform    | UI Implementation                                         | KMP Integration Point                                                        | User Experience                                                                                            |
|:------------|:----------------------------------------------------------|:-----------------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------|
| **Android** | **Floating Bubble** (Overlay) / **Notification Fallback** | `expect/actual` for system-level permission checks and service management.   | Mimics the Dynamic Island/floating bubble experience of OxygenOS/OneUI.                                    |
| **iOS**     | **Live Activity** / **Dynamic Island**                    | `expect/actual` for Live Activity API calls and background audio processing. | Provides a native, non-intrusive, always-on dictation status in the Dynamic Island or Lock Screen.         |
| **Desktop** | **System Tray Icon** / **Global Hotkey**                  | Platform-specific code for system tray and hotkey listeners.                 | Instant activation via a keyboard shortcut, with the transcribed text automatically sent to the clipboard. |

---

## 5. Feature Breakdown & Monetization

VoDrop will launch with a freemium model, keeping the core offline functionality free forever to maximize adoption.

| Feature                    | Free Version            | Pro Version (Paid)                       |
|:---------------------------|:------------------------|:-----------------------------------------|
| **Core Dictation**         | ✅                       | ✅                                        |
| **Offline Processing**     | ✅                       | ✅                                        |
| **Automatic Clipboard**    | ✅                       | ✅                                        |
| **Floating UI Access**     | ❌                       | ✅ (Floating Bubble/Live Activity/Hotkey) |
| **Transcription History**  | ❌                       | ✅ (Stored via SQLDelight)                |
| **Multi-language Support** | ❌ (System Default Only) | ✅ (User-selectable models)               |
| **Hybrid Cloud Boost**     | ❌                       | ✅ (Future Elite Tier)                    |

---

## 6. High-Level Development Roadmap (KMP Focus)

The project is broken down into four clear, achievable phases, with an emphasis on establishing the KMP foundation early.

### Phase 1: KMP Foundation & Core Logic (Est. 3 Weeks)
*   **Goal:** Establish the KMP project structure and integrate the core cross-platform logic.
*   **Tasks:** Set up KMP project (Android, iOS, Desktop targets). Integrate **Whisper.cpp** via Kotlin/Native C Interop. Implement **SQLDelight** for data storage. Implement the core "Record Audio → Process → Save" logic in the common module.
*   **Outcome:** A functional KMP core module with working STT and database on all three platforms.

### Phase 2: Shared UI & Platform Integration (Est. 3 Weeks)
*   **Goal:** Implement the shared UI and the platform-specific Floating UI features.
*   **Tasks:** Implement the shared UI (Main Screen, History Screen) using **Compose Multiplatform**. Implement platform-specific **Floating UI** (Bubble/Live Activity/Hotkey) via `expect/actual`.
*   **Outcome:** A beautiful, daily-usable app on all three platforms, ready for release.

### Phase 3: MVP Release (Est. 1 Week)
*   **Goal:** Prepare for and launch on the Google Play Store, Apple App Store, and Desktop.
*   **Tasks:** Finalize branding, write the Privacy Policy (emphasizing offline processing), and publish to all stores.
*   **Outcome:** Version 1.0 of VoDrop is live.

### Phase 4: Scaling & Hybrid Strategy (Post-Launch)
*   **Goal:** Integrate the payment system and introduce advanced features to compete with cloud services.
*   **Tasks:** Integrate Google Play Billing and Apple StoreKit. Implement Pro features. Begin development on the Hybrid Scaling features:
    *   **Local LLM Integration:** Use lightweight models (e.g., Gemma-2B) for on-device "Smart Refine" (auto-formatting, tone matching).
    *   **Cloud Boost Elite Tier:** Offer a premium subscription that uses **Groq's Whisper API** for ultra-high accuracy and long-form transcription, ensuring VoDrop can compete on every metric while maintaining the offline-first core.
*   **Outcome:** A sustainable, revenue-generating product with a clear competitive edge.

---

## 7. Competitive Advantage Summary

VoDrop's strategy is to be the **best of both worlds** while maintaining a core identity that competitors cannot replicate.

| VoDrop Advantage              | Competitor Weakness                                       |
|:------------------------------|:----------------------------------------------------------|
| **100% Offline Core**         | Cloud-dependent (Wispr Flow, VoiceType)                   |
| **Zero Latency**              | Network-dependent latency                                 |
| **MIT License (Whisper.cpp)** | Proprietary models, limiting resale value                 |
| **KMP/Shared UI**             | Platform-specific codebases, slower development           |
| **Hybrid Scaling**            | No clear path to offering both privacy and ultra-accuracy |

By focusing on the offline experience first, VoDrop builds a loyal user base that values privacy and speed, creating a defensible market position.
