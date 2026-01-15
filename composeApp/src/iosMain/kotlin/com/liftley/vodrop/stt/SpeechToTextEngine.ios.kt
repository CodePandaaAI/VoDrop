package com.liftley.vodrop.stt

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class IosSpeechToTextEngine : SpeechToTextEngine {

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotLoaded)
    override val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    private var _currentModel: WhisperModel = WhisperModel.DEFAULT
    override val currentModel: WhisperModel get() = _currentModel

    override suspend fun loadModel(model: WhisperModel) {
        _currentModel = model
        _modelState.value = ModelState.Ready // TODO: Real implementation
    }

    override fun isModelAvailable(model: WhisperModel): Boolean = false

    override suspend fun transcribe(audioData: ByteArray): TranscriptionResult {
        return TranscriptionResult.Error("iOS STT not implemented")
    }

    override fun release() {
        _modelState.value = ModelState.NotLoaded
    }
}

actual fun createSpeechToTextEngine(): SpeechToTextEngine = IosSpeechToTextEngine()