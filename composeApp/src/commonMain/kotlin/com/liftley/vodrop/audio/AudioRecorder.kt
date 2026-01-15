package com.liftley.vodrop.audio

interface AudioRecorder {
    fun startRecording()
    fun stopRecording(): ByteArray
    fun isRecording(): Boolean
}

expect fun createAudioRecorder(): AudioRecorder