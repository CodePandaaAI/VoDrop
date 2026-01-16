package com.liftley.vodrop.data.stt

import android.util.Log

/**
 * JNI wrapper for native whisper.cpp library.
 *
 * The native library is built via CMake and follows the official
 * whisper.cpp Android example pattern.
 */
object WhisperJni {

    private const val LOG_TAG = "WhisperJni"

    init {
        try {
            System.loadLibrary("whisper_jni")
            Log.d(LOG_TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(LOG_TAG, "Failed to load native library", e)
            throw RuntimeException("Failed to load whisper_jni native library", e)
        }
    }

    /**
     * Initialize whisper context from a model file path.
     * @param modelPath Absolute path to the GGML model file
     * @return Native context pointer, or 0 if initialization failed
     */
    external fun init(modelPath: String): Long

    /**
     * Transcribe audio samples to text.
     * @param contextPtr Native context pointer from init()
     * @param audioData Float array of audio samples (normalized -1.0 to 1.0, 16kHz mono)
     * @return Transcribed text, or empty string if failed
     */
    external fun transcribe(contextPtr: Long, audioData: FloatArray): String

    /**
     * Release native whisper context and free memory.
     * @param contextPtr Native context pointer from init()
     */
    external fun release(contextPtr: Long)

    /**
     * Get whisper.cpp system information for debugging.
     * @return System info string
     */
    external fun getSystemInfo(): String
}