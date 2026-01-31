package com.liftley.vodrop.data.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread

/**
 * JVM/Desktop audio recorder using javax.sound API
 * Produces 16kHz, mono, 16-bit PCM audio.
 * 
 * PURE RECORDER: No state exposure, just records and returns bytes.
 */
class JvmAudioRecorder : AudioRecorder {

    private var targetLine: TargetDataLine? = null
    @Volatile
    private var isCurrentlyRecording = false
    private var recordingThread: Thread? = null
    private val audioData = ByteArrayOutputStream()
    private val lock = Any()

    private val audioFormat = AudioFormat(
        AudioConfig.SAMPLE_RATE.toFloat(),  // Sample rate: 16kHz
        AudioConfig.BITS_PER_SAMPLE,        // Sample size: 16 bits
        AudioConfig.CHANNELS,               // Channels: mono
        true,                               // Signed
        false                               // Little endian
    )

    override suspend fun startRecording() {
        if (isCurrentlyRecording) {
            throw AudioRecorderException("Already recording")
        }

        withContext(Dispatchers.IO) {
            try {
                val info = DataLine.Info(TargetDataLine::class.java, audioFormat)

                if (!AudioSystem.isLineSupported(info)) {
                    throw AudioRecorderException(
                        "Audio format not supported. Required: 16kHz, 16-bit, mono PCM"
                    )
                }

                synchronized(lock) {
                    targetLine = (AudioSystem.getLine(info) as TargetDataLine).apply {
                        open(audioFormat)
                        start()
                    }
                    audioData.reset()
                    isCurrentlyRecording = true
                }

                // Start recording thread
                recordingThread = thread(name = "VoDrop-AudioRecorder") {
                    val bufferSize = 4096
                    val buffer = ByteArray(bufferSize)

                    while (isCurrentlyRecording) {
                        val line = targetLine ?: break
                        val bytesRead = line.read(buffer, 0, bufferSize)

                        if (bytesRead > 0) {
                            synchronized(lock) {
                                audioData.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                }
            } catch (e: LineUnavailableException) {
                throw AudioRecorderException("Microphone is unavailable or in use", e)
            } catch (e: Exception) {
                throw AudioRecorderException("Failed to start recording", e)
            }
        }
    }

    override suspend fun stopRecording(): ByteArray {
        if (!isCurrentlyRecording) {
            throw AudioRecorderException("Not currently recording")
        }

        return withContext(Dispatchers.IO) {
            synchronized(lock) {
                isCurrentlyRecording = false
            }

            // Wait for recording thread to finish
            recordingThread?.join(2000)
            recordingThread = null

            synchronized(lock) {
                targetLine?.stop()
                targetLine?.close()
                targetLine = null
                audioData.toByteArray()
            }
        }
    }

    override suspend fun cancelRecording() {
        synchronized(lock) {
            isCurrentlyRecording = false
            recordingThread?.interrupt()
            recordingThread = null
            targetLine?.stop()
            targetLine?.close()
            targetLine = null
            audioData.reset()
        }
    }

    override fun isRecording(): Boolean = isCurrentlyRecording
}

actual fun createAudioRecorder(): AudioRecorder = JvmAudioRecorder()