package com.liftley.vodrop.stt

class JvmSpeechToTextEngine : SpeechToTextEngine {
    
    private var modelLoaded = false
    
    override suspend fun transcribe(audioData: ByteArray): String {
        if (!modelLoaded) {
            loadModel()
        }
        
        // TODO: Integrate Whisper.cpp via JNI
        val durationSeconds = audioData.size / (16000 * 2)
        return "[Audio captured: ${durationSeconds}s - Whisper.cpp JNI pending]"
    }
    
    override fun isModelLoaded(): Boolean = modelLoaded
    
    override suspend fun loadModel() {
        modelLoaded = true
    }
}

actual fun createSpeechToTextEngine(): SpeechToTextEngine = JvmSpeechToTextEngine()
