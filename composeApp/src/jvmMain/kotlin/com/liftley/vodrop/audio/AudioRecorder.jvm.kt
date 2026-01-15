package com.liftley.vodrop.audio

import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

class JvmAudioRecorder : AudioRecorder {

    private var targetLine: TargetDataLine? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private val audioData = ByteArrayOutputStream()

    private val audioFormat = AudioFormat(
        16000f,  // Sample rate (Whisper expects 16kHz)
        16,      // Sample size in bits
        1,       // Channels (mono)
        true,    // Signed
        false    // Little endian
    )

    override fun startRecording() {
        if (isRecording) return

        val info = DataLine.Info(TargetDataLine::class.java, audioFormat)

        if (!AudioSystem.isLineSupported(info)) {
            throw UnsupportedOperationException("Audio line not supported")
        }

        targetLine = (AudioSystem.getLine(info) as TargetDataLine).apply {
            open(audioFormat)
            start()
        }

        audioData.reset()
        isRecording = true

        recordingThread = Thread {
            val buffer = ByteArray(4096)
            while (isRecording) {
                val read = targetLine?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    synchronized(audioData) {
                        audioData.write(buffer, 0, read)
                    }
                }
            }
        }.apply { start() }
    }

    override fun stopRecording(): ByteArray {
        isRecording = false
        recordingThread?.join(1000)
        recordingThread = null

        targetLine?.stop()
        targetLine?.close()
        targetLine = null

        return synchronized(audioData) {
            audioData.toByteArray()
        }
    }

    override fun isRecording(): Boolean = isRecording
}

actual fun createAudioRecorder(): AudioRecorder = JvmAudioRecorder()