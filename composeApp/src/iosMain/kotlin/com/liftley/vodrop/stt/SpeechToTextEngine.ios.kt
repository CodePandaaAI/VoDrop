package com.liftley.vodrop.stt

class IosSpeechToTextEngine : SpeechToTextEngine {
    
    private var modelLoaded = false
    
    override suspend fun transcribe(audioData: ByteArray): String {
        // TODO: Integrate via C-interop
        val durationSeconds = audioData.size / (16000 * 2)
        return "[Audio captured: ${durationSeconds}s - Whisper.cpp C-interop pending]"
    }
    
    override fun isModelLoaded(): Boolean = modelLoaded
    
    override suspend fun loadModel() {
        modelLoaded = true
    }
}

actual fun createSpeechToTextEngine(): SpeechToTextEngine = IosSpeechToTextEngine()
