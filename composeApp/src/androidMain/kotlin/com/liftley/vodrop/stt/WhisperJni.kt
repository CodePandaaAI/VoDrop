package com.liftley.vodrop.stt

/**
 * JNI bridge to the native whisper.cpp library.
 *
 * This class loads the native library and provides three functions:
 * - init: Load a model file and return a handle
 * - transcribe: Send PCM audio and get text back
 * - release: Free the native memory
 */
object WhisperJni {

    init {
        // Load the native library we built with CMake
        // Android will look for libwhisper_jni.so in the APK
        System.loadLibrary("whisper_jni")
    }

    /**
     * Initialize Whisper with a model file.
     *
     * @param modelPath Absolute path to the GGML model file (e.g., ggml-tiny.en.bin)
     * @return A native pointer as Long (0 = error)
     */
    external fun init(modelPath: String): Long

    /**
     * Transcribe raw PCM audio.
     *
     * @param contextPtr The handle returned by init()
     * @param pcmData Raw PCM bytes (16-bit, 16kHz, mono, little-endian)
     * @return Transcribed text (empty string if failed)
     */
    external fun transcribe(contextPtr: Long, pcmData: ByteArray): String

    /**
     * Release native resources.
     *
     * @param contextPtr The handle to release
     */
    external fun release(contextPtr: Long)
}