<div align="center">

# 🎙️ VoDrop
### Messaging. Journaling. Prompting. Just drop your voice.

[![Platform](https://img.shields.io/badge/Platform-Android_|_Desktop-blue?style=for-the-badge&logo=android)](https://kotlinlang.org/docs/multiplatform.html)
[![AI Power](https://img.shields.io/badge/AI-Gemini_1.5_Flash-purple?style=for-the-badge&logo=google-gemini)](https://deepmind.google/technologies/gemini/)
[![Speech](https://img.shields.io/badge/STT-Chirp_3-green?style=for-the-badge&logo=google-cloud)](https://cloud.google.com/speech-to-text)

> **"Weight on every second."**  
> No Complex Setup. No Learning or figuring out. Just tap, speak, and get perfect text.

[🎥 **Watch the Demo Video**](LINK_TO_YOUTUBE_VIDEO_HERE)

</div>

---

## 🎯 The Purpose

Life happens on the go. You have a brilliant idea or a complex thought, but:
*   **Typing is a nightmare** when you're walking or holding a coffee.
*   **Voice notes are annoying** for the person receiving them.
*   **Standard transcription** gives you a messy wall of text full of "ums" and "uhs".

## 💡 The Solution: VoDrop

VoDrop is simple. **No fluff. Instant results.**
It takes your chaotic stream of consciousness and structures it perfectly.

### Core Use Cases

#### 1. 💬 Messaging
Don't spend minutes typing out a long explanation. Speak it naturally. VoDrop instantly turns your rambling into a clean, readable message ready for WhatsApp, Slack, or Email.

#### 2. 🤖 Prompt Engineering
LLMs (like Gemini) need clear instructions. If you ramble, they hallucinate.
**With VoDrop:** Speak your complex request → VoDrop structures it → Paste into Gemini -> Get perfect results.

#### 3. 📔 Journaling
Capture your day without the friction of typing. Turn a 2-minute stream of consciousness into a beautifully formatted journal entry.

---

## ❤️ Philosophy: Keep It Real
**"Keep your voice original."**

Most AI tools rewrite your text until you sound like a corporate robot. VoDrop does the opposite.
*   We fix the grammar.
*   We add the punctuation.
*   **But we keep your words.**
We ensure that when people read your message, they recognize **your** voice. It's just you, but polished.

---

## ✨ Features

| Feature                   | Tech Stack                                                                          |
|:--------------------------|:------------------------------------------------------------------------------------|
| **Microphone Management** | Android Foreground Service for bulletproof background recording.                    |
| **Smart Transcription**   | **Chirp 3 (USM)** automatically handles accents and noise.                          |
| **AI Polish**             | **Gemini 1.5 Flash** rewrites unstructured thought streams into crisp paragraphs.   |
| **Unified Architecture**  | Single Source of Truth (SSOT) architecture for robust state management.             |
| **Material 3 Design**     | sleek, modern UI with dark mode support.                                            |

---

## 🧠 AI & Cloud Architecture

VoDrop leverages a serverless architecture to keep client-side minimal and secure.

### 1. The Pipeline
```mermaid
graph LR
    A[User Speaks] -->|Raw PCM| B(Firebase Storage)
    B --> C{Cloud Function}
    C -->|Long Audio| D[Chirp 3 Batch]
    C -->|Short Audio| E[Chirp 3 Sync]
    D & E --> F[Raw Text]
    F -->|Auth Token| G[Gemini 1.5 Flash]
    G --> H[Polished Text]
    H --> I[Android App]
```

### 2. Why Gemini 1.5 Flash?
We chose **Gemini 1.5 Flash** for the "AI Polish" feature because:
*   **Speed**: It returns rewritten text in milliseconds, feeling instant to the user.
*   **Cost Efficiency**: It is extremely efficient for high-volume text processing, making the business model viable.
*   **Intelligent Instruction Following**: It perfectly understands the nuance of "fixing grammar without changing the tone," a task where smaller models often fail.

---

## 🏗️ App Architecture

VoDrop is built with **Kotlin Multiplatform (KMP)** and follows strict **MVVM + SSOT** principles.

```mermaid
classDiagram
    class MainViewModel {
        +appState: StateFlow
        +uiState: StateFlow
        +onRecordClick()
    }
    class RecordingSessionManager {
        <<SSOT>>
        +state: StateFlow
        +start()
        +stop()
    }
    class CloudTranscriptionService {
        +transcribe()
        +polish()
    }
    class AudioRecorder {
        <<Interface>>
        +start(Config)
        +stop()
    }

    MainViewModel --> RecordingSessionManager : Observes
    RecordingSessionManager --> CloudTranscriptionService : Calls
    RecordingSessionManager --> AudioRecorder : Controls
```

*   **Single Source of Truth**: `RecordingSessionManager` holds the *only* valid state of the recording session.
*   **Platform Specifics**: The `AudioRecorder` uses native implementations (Android `AudioRecord`, Java `TargetDataLine` for Desktop) to ensure low-latency raw byte capture across platforms.
*   **Thin UI**: The ViewModel simply exposes this state and forwards user intents, keeping the logic decoupled from the UI/Platform.

---

## 🚀 Getting Started

### Prerequisites
*   Android Studio Ladybug+
*   Firebase Project (Blaze Plan required for Cloud Functions)
*   Google Cloud Project with **Speech-to-Text API** enabled

### Installation

1.  **Clone the repo**
    ```bash
    git clone https://github.com/yourusername/vodrop.git
    ```

2.  **Setup Firebase**
    *   Add `google-services.json` to `composeApp/`.
    *   Deploy functions:
        ```bash
        cd functions
        npm install
        firebase deploy --only functions
        ```

3.  **Run the App**
    ```bash
    ./gradlew :composeApp:installDebug
    ```

---

## 🔮 Roadmap

*   [x] **Hackathon MVP**: Recording, Chirp 3, Gemini 1.5 Flash Polish.
*   [ ] **Style Selection**: Choose between "Formal", "Casual", or "Bullet Points" (Powered by dynamic Gemini prompts).
*   [ ] **Desktop Support**: Fully enable the desktop target (currently logic-only).
*   [ ] **iOS Support**: Expand the KMP codebase to target iOS users.

---

<div align="center">
Built with ❤️ for the Gemini Hackathon
</div>
