package com.liftley.vodrop.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Audio format specification for Whisper.cpp compatibility.
 * Whisper expects: 16kHz, mono, 16-bit PCM (little-endian)
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

    /** Convert PCM bytes to float samples (normalized to -1.0 to 1.0) */
    fun pcmBytesToFloatSamples(pcmData: ByteArray): FloatArray {
        val samples = FloatArray(pcmData.size / BYTES_PER_SAMPLE)
        for (i in samples.indices) {
            val low = pcmData[i * 2].toInt() and 0xFF
            val high = pcmData[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            samples[i] = sample / 32768f
        }
        return samples
    }
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
 * Implementations must produce 16kHz, mono, 16-bit PCM audio for Whisper compatibility.
 */
interface AudioRecorder {
    /** Current recording status as a reactive flow */
    val status: StateFlow<RecordingStatus>

    /**
     * Start recording audio.
     * @throws AudioRecorderException if recording cannot be started
     */
    suspend fun startRecording()

    /**
     * Stop recording and return captured audio data.
     * @return PCM audio data (16kHz, mono, 16-bit)
     * @throws AudioRecorderException if recording was not active
     */
    suspend fun stopRecording(): ByteArray

    /** Check if currently recording */
    fun isRecording(): Boolean

    /** Release any resources held by the recorder */
    fun release()
}

/**
 * Exception thrown by audio recording operations
 */
class AudioRecorderException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Factory function to create platform-specific audio recorder
 */
expect fun createAudioRecorder(): AudioRecorder