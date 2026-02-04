package com.liftley.vodrop.data.audio

/**
 * **Audio Configuration Strategy**
 * 
 * Defines the strict audio format required by Google Cloud Speech-to-Text V2.
 * We enforce 16kHz Mono PCM across all platforms (Android/Desktop).
 */
object AudioConfig {
    /** 16,000 Hz is required for optimal speech recognition accuracy */
    const val SAMPLE_RATE = 16000
    
    /** Mono channel is sufficient and reduces upload size */
    const val CHANNELS = 1
    
    /** 16-bit PCM is the standard uncompressed format */
    const val BITS_PER_SAMPLE = 16
    const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8

    /** 
     * Helper to estimate duration from raw bytes.
     * Used to decide between Sync (short) and Batch (long) API calls.
     */
    fun calculateDurationSeconds(audioData: ByteArray): Float {
        return audioData.size.toFloat() / (SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE)
    }
}

/**
 * **Audio Recorder Interface**
 * 
 * Platform-agnostic contract for capturing raw audio bytes.
 * Implementations (Android/Desktop) must adhere to [AudioConfig].
 */
interface AudioRecorder {
    suspend fun startRecording()
    
    /** 
     * Stops recording and returns the full captured buffer.
     * WARNING: Kept in memory. For V2, we might stream to disk to handle hour-long audio.
     */
    suspend fun stopRecording(): ByteArray
    
    suspend fun cancelRecording()
    fun isRecording(): Boolean
}

class AudioRecorderException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

expect fun createAudioRecorder(): AudioRecorder