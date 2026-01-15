package com.liftley.vodrop.stt

interface SpeechToTextEngine {
    suspend fun transcribe(audioData: ByteArray): String
    fun isModelLoaded(): Boolean
    suspend fun loadModel()
}

expect fun createSpeechToTextEngine(): SpeechToTextEngine