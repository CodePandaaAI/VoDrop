package com.liftley.vodrop.data.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import com.liftley.vodrop.service.RecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.log10

/**
 * Android audio recorder using AudioRecord API
 * Produces 16kHz, mono, 16-bit PCM audio
 * Supports background recording via foreground service
 */
class AndroidAudioRecorder : AudioRecorder, KoinComponent {

    private val context: Context by inject()

    private val _status = MutableStateFlow<RecordingStatus>(RecordingStatus.Idle)
    override val status: StateFlow<RecordingStatus> = _status.asStateFlow()

    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isCurrentlyRecording = false
    private var recordingThread: Thread? = null

    private var audioData = ByteArrayOutputStream(2_880_000)

    private val lock = Any()

    companion object {
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    override suspend fun startRecording() {
        if (isCurrentlyRecording) {
            throw AudioRecorderException("Already recording")
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            throw AudioRecorderException("MICROPHONE permission not granted")
        }

        withContext(Dispatchers.IO) {
            try {
                val minBufferSize = AudioRecord.getMinBufferSize(
                    AudioConfig.SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT
                )

                val bufferSize = if (minBufferSize > 0) minBufferSize * 2 else 4096

                synchronized(lock) {
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        AudioConfig.SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize
                    ).also { record ->
                        if (record.state != AudioRecord.STATE_INITIALIZED) {
                            record.release()
                            throw AudioRecorderException("Failed to initialize AudioRecord")
                        }
                    }

                    audioData.reset()
                    isCurrentlyRecording = true
                    audioRecord?.startRecording()
                    _status.value = RecordingStatus.Recording(0f)
                }

                // START FOREGROUND SERVICE for background recording
                startRecordingService()

                recordingThread = thread(name = "VoDrop-AudioRecorder") {
                    val buffer = ByteArray(bufferSize)

                    while (isCurrentlyRecording) {
                        val record = audioRecord ?: break
                        val bytesRead = record.read(buffer, 0, buffer.size)

                        if (bytesRead > 0) {
                            synchronized(lock) {
                                audioData.write(buffer, 0, bytesRead)
                            }

                            val amplitude = calculateAmplitudeDb(buffer, bytesRead)
                            _status.value = RecordingStatus.Recording(amplitude)
                        } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                            _status.value = RecordingStatus.Error("Recording error: invalid operation")
                            isCurrentlyRecording = false
                            break
                        } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                            _status.value = RecordingStatus.Error("Recording error: bad value")
                            isCurrentlyRecording = false
                            break
                        }
                    }
                }
            } catch (e: SecurityException) {
                _status.value = RecordingStatus.Error("Permission denied")
                throw AudioRecorderException("Microphone permission denied", e)
            } catch (e: Exception) {
                _status.value = RecordingStatus.Error("Recording error: ${e.message}")
                throw AudioRecorderException("Failed to start recording", e)
            }
        }
    }

    override suspend fun cancelRecording() {
        if (!isCurrentlyRecording) return

        withContext(Dispatchers.IO) {
            synchronized(lock) {
                isCurrentlyRecording = false
            }
            recordingThread?.join(2000)
            recordingThread = null

            synchronized(lock) {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                audioData.reset()
                _status.value = RecordingStatus.Idle
            }

            // STOP FOREGROUND SERVICE
            stopRecordingService()
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

            recordingThread?.let { thread ->
                thread.join(2000)
                if (thread.isAlive) {
                    thread.interrupt()
                }
            }
            recordingThread = null

            synchronized(lock) {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null

                _status.value = RecordingStatus.Idle

                val data = audioData.toByteArray()

                // STOP FOREGROUND SERVICE
                stopRecordingService()

                data
            }
        }
    }

    override fun isRecording(): Boolean = isCurrentlyRecording

    override fun release() {
        synchronized(lock) {
            isCurrentlyRecording = false
            recordingThread?.interrupt()
            recordingThread = null
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            audioData.reset()
            _status.value = RecordingStatus.Idle
        }
        stopRecordingService()
    }

    // ═══════════════════════════════════════════════════════════════
    // FOREGROUND SERVICE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    private fun startRecordingService() {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopRecordingService() {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        context.startService(intent)
    }

    private fun calculateAmplitudeDb(buffer: ByteArray, bytesRead: Int): Float {
        var sum = 0.0
        val samples = bytesRead / 2

        for (i in 0 until samples) {
            val low = buffer[i * 2].toInt() and 0xFF
            val high = buffer[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            sum += abs(sample).toDouble()
        }

        val average = if (samples > 0) sum / samples else 0.0
        val normalized = average / 32768.0

        return if (normalized > 0.0001) {
            (20 * log10(normalized)).toFloat().coerceIn(-60f, 0f)
        } else {
            -60f
        }
    }
}

actual fun createAudioRecorder(): AudioRecorder = AndroidAudioRecorder()