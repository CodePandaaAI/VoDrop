package com.liftley.vodrop.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.log10

/**
 * JVM/Desktop audio recorder using javax.sound API
 * Produces 16kHz, mono, 16-bit PCM audio for Whisper compatibility
 */
class JvmAudioRecorder : AudioRecorder {

    private val _status = MutableStateFlow<RecordingStatus>(RecordingStatus.Idle)
    override val status: StateFlow<RecordingStatus> = _status.asStateFlow()

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
        false                               // Little endian (required by Whisper)
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
                    _status.value = RecordingStatus.Recording(0f)
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

                            // Calculate amplitude for UI feedback
                            val amplitude = calculateAmplitudeDb(buffer, bytesRead)
                            _status.value = RecordingStatus.Recording(amplitude)
                        }
                    }
                }
            } catch (e: LineUnavailableException) {
                _status.value = RecordingStatus.Error("Microphone unavailable: ${e.message}")
                throw AudioRecorderException("Microphone is unavailable or in use", e)
            } catch (e: Exception) {
                _status.value = RecordingStatus.Error("Recording error: ${e.message}")
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

                _status.value = RecordingStatus.Idle

                audioData.toByteArray()
            }
        }
    }

    override fun isRecording(): Boolean = isCurrentlyRecording

    override fun release() {
        synchronized(lock) {
            isCurrentlyRecording = false
            recordingThread?.interrupt()
            recordingThread = null
            targetLine?.stop()
            targetLine?.close()
            targetLine = null
            audioData.reset()
            _status.value = RecordingStatus.Idle
        }
    }

    /**
     * Calculate amplitude in dB from audio buffer for visual feedback
     */
    private fun calculateAmplitudeDb(buffer: ByteArray, bytesRead: Int): Float {
        var sum = 0.0
        val samples = bytesRead / 2

        for (i in 0 until samples) {
            val low = buffer[i * 2].toInt() and 0xFF
            val high = buffer[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            sum += abs(sample).toDouble()
        }

        val average = sum / samples
        val normalized = average / 32768.0

        return if (normalized > 0.0001) {
            (20 * log10(normalized)).toFloat().coerceIn(-60f, 0f)
        } else {
            -60f
        }
    }
}

actual fun createAudioRecorder(): AudioRecorder = JvmAudioRecorder()