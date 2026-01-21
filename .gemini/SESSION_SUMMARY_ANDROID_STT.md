# VoDrop Android STT Implementation - Complete Technical Summary
**Session Date:** January 15, 2026  
**Last Updated:** 23:19 IST

> ⚠️ **Related Document:** See `SESSION_SUMMARY.md` for overall project architecture, UI components, database schema, and Desktop implementation details.

---

## 📋 Table of Contents
1. [Project Overview](#1-project-overview)
2. [Technical Architecture](#2-technical-architecture)
3. [The Core Problem](#3-the-core-problem)
4. [What We Attempted](#4-what-we-attempted)
5. [Current Status](#5-current-status)
6. [Files Modified/Created](#6-files-modifiedcreated)
7. [Key Learnings](#7-key-learnings)
8. [Recommended Next Steps](#8-recommended-next-steps)
9. [Code References](#9-code-references)

---

## 1. Project Overview

### What is VoDrop?
VoDrop is a **Kotlin Multiplatform (KMP) voice-to-text transcription application** built with Compose Multiplatform. It aims to be a cross-platform app supporting:
- **Desktop (JVM)** - Windows, macOS, Linux
- **Android**
- **iOS** (placeholder, not yet implemented)

### Core Philosophy
- **100% Offline Processing** - All transcription happens on-device
- **Privacy-First** - User voice data never leaves the device
- **Same User Experience** across all platforms

### Technology Stack
| Component | Technology |
|-----------|------------|
| UI Framework | Compose Multiplatform |
| Architecture | MVVM + Clean Architecture |
| DI | Koin |
| Database | SQLDelight |
| HTTP Client | Ktor |
| Desktop STT | WhisperJNI (whisper.cpp Java bindings) |
| Android STT | **IN PROGRESS** - Native whisper.cpp via JNI |

### Project Location
```
c:\Users\4444444\IdeaProjects\VoDrop\
```

---

## 2. Technical Architecture

### Speech-to-Text Flow
```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Audio Input   │────▶│   STT Engine    │────▶│  Transcription  │
│   (Microphone)  │     │   (Platform)    │     │     Result      │
└─────────────────┘     └─────────────────┘     └─────────────────┘

Desktop:  AudioRecorder.jvm.kt ──▶ SpeechToTextEngine.jvm.kt (WhisperJNI)
Android:  AudioRecorder.android.kt ──▶ SpeechToTextEngine.android.kt (Native JNI)
```

### Audio Format Requirements (Critical!)
Whisper.cpp expects a very specific audio format:
- **Sample Rate:** 16,000 Hz (16 kHz)
- **Channels:** 1 (Mono)
- **Bit Depth:** 16-bit signed
- **Encoding:** PCM, Little-Endian
- **Data Type:** Raw bytes (no WAV header)

### Whisper Models Used
| Model | File Name | Size | Speed | Accuracy |
|-------|-----------|------|-------|----------|
| FAST (Tiny) | `ggml-tiny.en.bin` | ~75 MB | Fastest | Good |
| BALANCED (Base) | `ggml-base.en.bin` | ~142 MB | Medium | Better |
| QUALITY (Small) | `ggml-small.en.bin` | ~466 MB | Slowest | Best |

All models are downloaded from HuggingFace:
```
https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-{model}.en.bin
```

---

## 3. The Core Problem

### Why Can't We Just Use Desktop WhisperJNI on Android?

The Desktop version uses `io.github.givimad:whisper-jni` which is a Java wrapper around whisper.cpp. **This library only provides pre-compiled native binaries for:**
- x86_64 (Intel/AMD 64-bit)
- macOS arm64 (Apple Silicon)

**Android devices use ARM processors**, specifically:
- `arm64-v8a` (modern 64-bit ARM)
- `armeabi-v7a` (older 32-bit ARM)

The desktop `.dll`/`.dylib`/`.so` files **CANNOT run on Android** because:
1. Different CPU architecture (x86 vs ARM)
2. Different C runtime library (glibc vs Bionic)
3. Different binary format (ELF on Linux vs Android-specific ELF)

### The Solution We Attempted
We attempted to **compile whisper.cpp natively for Android** using:
- **Android NDK** (Native Development Kit) - provides ARM compiler
- **CMake** - build system for C/C++ code
- **JNI** (Java Native Interface) - bridge between Kotlin and C++

---

## 4. What We Attempted

### Attempt 1: WhisperKit Android (Third-Party Library)
**Library:** `com.argmaxinc:whisperkit:0.3.3`

**Result:** Failed
- Required Qualcomm QNN dependencies that couldn't be resolved
- The library is experimental and not stable
- Callback API was confusing and poorly documented

**Code Added (later removed):**
```kotlin
implementation("com.argmaxinc:whisperkit:0.3.3")
implementation("com.qualcomm.qnn:qnn-runtime:2.34.0")
implementation("com.qualcomm.qnn:qnn-litert-delegate:2.34.0")
```

---

### Attempt 2: Native whisper.cpp via JNI (Current Approach)

#### Step 1: Downloaded whisper.cpp Source
- Source: https://github.com/ggerganov/whisper.cpp
- Location: `c:\Users\4444444\IdeaProjects\VoDrop\whisper-cpp-source\`
- Contains the full C++ implementation of Whisper

#### Step 2: Created CMakeLists.txt
**File:** `composeApp/src/androidMain/cpp/CMakeLists.txt`
```cmake
cmake_minimum_required(VERSION 3.22.1)
project(whisper_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(WHISPER_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../whisper-cpp-source")

# Disable unused features
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_SYCL OFF CACHE BOOL "" FORCE)
set(GGML_HIP OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Use whisper.cpp's own CMake
add_subdirectory(${WHISPER_CPP_DIR} whisper_build)

# Create JNI library
add_library(whisper_jni SHARED
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper_jni.cpp
)

target_include_directories(whisper_jni PRIVATE
    ${WHISPER_CPP_DIR}/include
    ${WHISPER_CPP_DIR}/ggml/include
)

find_library(log-lib log)
target_link_libraries(whisper_jni PRIVATE whisper ${log-lib})

# 16KB page alignment for Android 15+
target_link_options(whisper_jni PRIVATE "-Wl,-z,max-page-size=16384")
```

#### Step 3: Created JNI Bridge (C++)
**File:** `composeApp/src/androidMain/cpp/whisper_jni.cpp`

Three functions exposed:
1. `Java_com_liftley_vodrop_stt_WhisperJni_init` - Load model
2. `Java_com_liftley_vodrop_stt_WhisperJni_transcribe` - Transcribe audio
3. `Java_com_liftley_vodrop_stt_WhisperJni_release` - Free resources

```cpp
#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_liftley_vodrop_stt_WhisperJni_init(
    JNIEnv* env,
    jobject thiz,
    jstring modelPath
) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model from: %s", path);

    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    
    struct whisper_context* ctx = whisper_init_from_file_with_params(path, cparams);

    env->ReleaseStringUTFChars(modelPath, path);

    if (ctx == nullptr) {
        LOGE("Failed to initialize whisper context!");
        return 0L;
    }

    LOGI("Whisper model loaded successfully!");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_liftley_vodrop_stt_WhisperJni_transcribe(
    JNIEnv* env,
    jobject thiz,
    jlong contextPtr,
    jbyteArray pcmData
) {
    // ... converts PCM bytes to float samples
    // ... calls whisper_full() for transcription
    // ... returns text result
}

JNIEXPORT void JNICALL
Java_com_liftley_vodrop_stt_WhisperJni_release(
    JNIEnv* env,
    jobject thiz,
    jlong contextPtr
) {
    // ... frees native memory
}

}
```

#### Step 4: Created Kotlin JNI Wrapper
**File:** `composeApp/src/androidMain/kotlin/com/liftley/vodrop/stt/WhisperJni.kt`

```kotlin
package com.liftley.vodrop.stt

object WhisperJni {
    init {
        System.loadLibrary("whisper_jni")
    }
    
    external fun init(modelPath: String): Long
    external fun transcribe(contextPtr: Long, pcmData: ByteArray): String
    external fun release(contextPtr: Long)
}
```

#### Step 5: Updated build.gradle.kts
```kotlin
android {
    // NDK configuration
    ndk {
        abiFilters += listOf("arm64-v8a")
    }
    
    // CMake configuration
    externalNativeBuild {
        cmake {
            path = file("src/androidMain/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}
```

---

## 5. Current Status

### What Works ✅
1. **Native library compiles** - `libwhisper_jni.so` is built successfully
2. **APK builds** - The app package includes the native library
3. **Library loads** - `System.loadLibrary("whisper_jni")` succeeds
4. **Model downloads** - GGML model files download correctly
5. **Model loads** - `whisper_init_from_file_with_params()` returns a valid context
6. **Audio records** - PCM audio is captured correctly (16kHz, mono, 16-bit)
7. **JNI passes data** - Audio bytes are passed to native code successfully

### What Doesn't Work ❌
**`whisper_full()` NEVER RETURNS!**

The native transcription function is called but hangs indefinitely:
```
WhisperJNI: Audio duration: 7.04 seconds
WhisperJNI: Starting whisper_full transcription with 112640 samples...
(HANGS FOREVER - no further logs)
```

This happens on:
- ❌ Android Emulator (x86_64 with ARM translation) - Expected to be slow
- ❌ **Real Device (OnePlus Nord CE 5 - ARM64)** - Should work but doesn't!

### Crashes Encountered
When user tried to switch models during transcription:
```
Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0xf9680
at ggml_vec_dot_f16+312
at whisper_full_with_state+3972
```

**Cause:** Race condition - releasing context while `whisper_full()` was still running.
**Fix:** Added mutex and `isTranscribing` flag to prevent concurrent access.

---

## 6. Files Modified/Created

### New Files Created
| File | Purpose |
|------|---------|
| `composeApp/src/androidMain/cpp/CMakeLists.txt` | CMake build script for native code |
| `composeApp/src/androidMain/cpp/whisper_jni.cpp` | JNI bridge implementation |
| `composeApp/src/androidMain/kotlin/com/liftley/vodrop/stt/WhisperJni.kt` | Kotlin JNI declarations |
| `whisper-cpp-source/` | Full whisper.cpp source code (downloaded) |

### Modified Files
| File | Changes |
|------|---------|
| `composeApp/build.gradle.kts` | Added NDK config, CMake, removed WhisperKit |
| `gradle/libs.versions.toml` | Removed WhisperKit versions |
| `composeApp/src/commonMain/.../SpeechToTextEngine.kt` | Simplified WhisperModel enum |
| `composeApp/src/jvmMain/.../SpeechToTextEngine.jvm.kt` | Updated property names |
| `composeApp/src/androidMain/.../SpeechToTextEngine.android.kt` | Full native JNI implementation |

---

## 7. Key Learnings

### Why JNI is Complex
1. **Binary Compatibility** - Must compile for exact target architecture
2. **Memory Management** - C++ and JVM have different memory models
3. **Threading** - Native code runs on JVM threads, need synchronization
4. **Error Handling** - Native crashes take down the whole app

### Why whisper.cpp Might Be Hanging
Possible causes (needs investigation):
1. **Model file corruption** - Download might have been incomplete
2. **Threading configuration** - `n_threads` parameter might be wrong
3. **CPU backend issues** - GGML CPU backend might have ARM-specific bugs
4. **Memory allocation** - Device might not have enough RAM
5. **Floating point issues** - ARM NEON vs x86 SSE differences

### Alternative Solutions That Exist
| Library | Pros | Cons |
|---------|------|------|
| **Vosk** | Proven, tested, works | Different model format |
| **Whisper Android (Whisper.cpp)** | Same as desktop | Complex to build |
| **TensorFlow Lite Whisper** | Google-supported | Conversion needed |
| **Cloud API (OpenAI)** | Simple, accurate | Requires internet, costs money |

---

## 8. Recommended Next Steps

### Option A: Try Vosk (Easiest)
Vosk is a proven speech recognition library with pre-built Android binaries:
```kotlin
implementation("com.alphacephei:vosk-android:0.3.47")
```
- Downloads ~40-50MB model
- Works offline
- Similar accuracy to Whisper tiny/base
- **No native compilation needed**

### Option B: Debug whisper.cpp Further
1. **Verify model file integrity:**
   ```kotlin
   val file = File(modelDir, "ggml-tiny.en.bin")
   Log.d("Model", "Size: ${file.length()} bytes") // Should be ~75MB
   ```

2. **Try different threading:**
   ```cpp
   params.n_threads = 4;  // Try 2, 4, or Runtime.getRuntime().availableProcessors()
   ```

3. **Add timeout with coroutine:**
   ```kotlin
   withTimeout(60_000) { // 60 second timeout
       WhisperJni.transcribe(context, audio)
   }
   ```

4. **Check available RAM:**
   - Whisper tiny needs ~200-300MB RAM during inference

### Option C: Use Cloud API as Fallback
Keep offline for privacy, but offer cloud as optional:
- OpenAI Whisper API: $0.006/minute
- Google Speech-to-Text: $0.006/minute
- Azure Speech Services: Free tier available

---

## 9. Code References

### Current Android STT Engine
**File:** `composeApp/src/androidMain/kotlin/com/liftley/vodrop/stt/SpeechToTextEngine.android.kt`

Key methods:
- `loadModel(model: WhisperModel)` - Downloads & loads model
- `transcribe(audioData: ByteArray)` - Converts audio to text
- `release()` - Frees native resources

### Current Audio Recorder
**File:** `composeApp/src/androidMain/kotlin/com/liftley/vodrop/audio/AudioRecorder.android.kt`

Produces: 16kHz, mono, 16-bit PCM audio in `ByteArray`

### Common Interface (shared)
**File:** `composeApp/src/commonMain/kotlin/com/liftley/vodrop/stt/SpeechToTextEngine.kt`

```kotlin
interface SpeechToTextEngine {
    val modelState: StateFlow<ModelState>
    val currentModel: WhisperModel
    
    suspend fun loadModel(model: WhisperModel = WhisperModel.DEFAULT)
    fun isModelAvailable(model: WhisperModel = WhisperModel.DEFAULT): Boolean
    suspend fun transcribe(audioData: ByteArray): TranscriptionResult
    fun release()
}
```

---

## 📝 Quick Context for Next AI

**TL;DR:** 
- VoDrop is a KMP voice-to-text app
- Desktop works with WhisperJNI
- Android needs native whisper.cpp
- We compiled whisper.cpp for ARM64 via NDK+CMake+JNI
- Native library loads, model loads, but `whisper_full()` hangs forever
- Tested on real device (OnePlus Nord CE 5) - still hangs
- Options: Debug more, switch to Vosk, or use cloud API

**Key files to look at:**
1. `composeApp/src/androidMain/cpp/whisper_jni.cpp`
2. `composeApp/src/androidMain/kotlin/com/liftley/vodrop/stt/SpeechToTextEngine.android.kt`
3. `composeApp/src/androidMain/cpp/CMakeLists.txt`

**The user wants:** Fully offline Android STT that works reliably.

---

*End of Session Summary*
