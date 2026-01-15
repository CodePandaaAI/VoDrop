package com.liftley.vodrop.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class IosAudioRecorder : AudioRecorder {

    private val _status = MutableStateFlow<RecordingStatus>(RecordingStatus.Idle)
    override val status: StateFlow<RecordingStatus> = _status.asStateFlow()
    private var isCurrentlyRecording = false

    override suspend fun startRecording() {
        isCurrentlyRecording = true
        _status.value = RecordingStatus.Recording(0f)
        // TODO: Implement with AVAudioEngine
    }

    override suspend fun stopRecording(): ByteArray {
        isCurrentlyRecording = false
        _status.value = RecordingStatus.Idle
        return ByteArray(0) // TODO: Return AVAudioEngine data
    }

    override fun isRecording(): Boolean = isCurrentlyRecording
    override fun release() {
        isCurrentlyRecording = false
        _status.value = RecordingStatus.Idle
    }
}

actual fun createAudioRecorder(): AudioRecorder = IosAudioRecorder()