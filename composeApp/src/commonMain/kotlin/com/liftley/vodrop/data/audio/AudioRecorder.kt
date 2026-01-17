package com.liftley.vodrop.data.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Audio format specification for cloud transcription.
 * Format: 16kHz, mono, 16-bit PCM
 */
object AudioConfig {
    const val SAMPLE_RATE = 16000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16
    const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8

    /** Calculate duration in seconds from byte array */
    fun calculateDurationSeconds(audioData: ByteArray): Float {
        return audioData.size.toFloat() / (SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE)
    }

    // REMOVED: pcmBytesToFloatSamples() - was for local Whisper.cpp, now cloud-only
}

/**
 * Recording state with amplitude for UI feedback
 */
sealed interface RecordingStatus {
    data object Idle : RecordingStatus
    data class Recording(val amplitudeDb: Float = 0f) : RecordingStatus
    data class Error(val message: String) : RecordingStatus
}

/**
 * Platform-agnostic audio recorder interface.
 */
interface AudioRecorder {
    val status: StateFlow<RecordingStatus>
    suspend fun startRecording()
    suspend fun stopRecording(): ByteArray
    fun isRecording(): Boolean
    fun release()
}

class AudioRecorderException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

expect fun createAudioRecorder(): AudioRecorder