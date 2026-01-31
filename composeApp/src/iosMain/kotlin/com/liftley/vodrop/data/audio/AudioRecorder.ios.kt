package com.liftley.vodrop.data.audio

/**
 * iOS audio recorder stub.
 * TODO: Implement with AVAudioEngine when needed.
 * 
 * PURE RECORDER: No state exposure, just records and returns bytes.
 */
class IosAudioRecorder : AudioRecorder {

    private var isCurrentlyRecording = false

    override suspend fun startRecording() {
        isCurrentlyRecording = true
        // TODO: Implement with AVAudioEngine
    }

    override suspend fun stopRecording(): ByteArray {
        isCurrentlyRecording = false
        return ByteArray(0) // TODO: Return AVAudioEngine data
    }

    override suspend fun cancelRecording() {
        isCurrentlyRecording = false
    }

    override fun isRecording(): Boolean = isCurrentlyRecording
}

actual fun createAudioRecorder(): AudioRecorder = IosAudioRecorder()