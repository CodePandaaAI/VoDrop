package com.liftley.vodrop.audio

// iOS implementation requires AVFoundation - placeholder for now
class IosAudioRecorder : AudioRecorder {

    private var isRecording = false
    private var audioData = ByteArray(0)

    override fun startRecording() {
        isRecording = true
        // TODO: Implement with AVAudioEngine
        // This requires platform-specific Swift/ObjC code
    }

    override fun stopRecording(): ByteArray {
        isRecording = false
        // TODO: Return recorded audio data
        return audioData
    }

    override fun isRecording(): Boolean = isRecording
}

actual fun createAudioRecorder(): AudioRecorder = IosAudioRecorder()