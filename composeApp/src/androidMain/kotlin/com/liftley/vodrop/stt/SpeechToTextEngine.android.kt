package com.liftley.vodrop.stt

import android.content.Context
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AndroidSpeechToTextEngine : SpeechToTextEngine, KoinComponent {
    
    private val context: Context by inject()
    private var modelLoaded = false
    
    override suspend fun transcribe(audioData: ByteArray): String {
        if (!modelLoaded) {
            loadModel()
        }
        
        // TODO: Integrate actual Whisper.cpp JNI
        // For now, return placeholder showing audio was captured
        val durationSeconds = audioData.size / (16000 * 2) // 16kHz, 16-bit
        return "[Audio captured: ${durationSeconds}s - Whisper.cpp integration pending]"
    }
    
    override fun isModelLoaded(): Boolean = modelLoaded
    
    override suspend fun loadModel() {
        // TODO: Load Whisper model from assets
        modelLoaded = true
    }
}

actual fun createSpeechToTextEngine(): SpeechToTextEngine = AndroidSpeechToTextEngine()
