package com.liftley.vodrop.data.audio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.liftley.vodrop.service.RecordingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.log10

private const val TAG = "AudioRecorder"

/**
 * Android audio recorder using AudioRecord API.
 * Produces 16kHz, mono, 16-bit PCM audio.
 * Supports background recording via foreground service.
 */
class AndroidAudioRecorder : AudioRecorder, KoinComponent {

    private val context: Context by inject()

    private val _status = MutableStateFlow<RecordingStatus>(RecordingStatus.Idle)
    override val status: StateFlow<RecordingStatus> = _status.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    // ~30 seconds at 16kHz mono 16-bit = 16000 * 2 * 30 = 960,000 bytes
    private var audioData = ByteArrayOutputStream(960_000)

    companion object {
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    override suspend fun startRecording() {
        Log.d(TAG, "startRecording() called")

        if (recordingJob?.isActive == true) {
            throw AudioRecorderException("Already recording")
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw AudioRecorderException("MICROPHONE permission not granted")
        }

        // ✅ Start foreground service FIRST (on Main thread)
        // Add EXTRA_FROM_RECORDER to tell Service NOT to call manager.startRecording() again
        sendServiceAction(RecordingService.ACTION_START, fromRecorder = true)

        withContext(Dispatchers.IO) {
            val minBufferSize = AudioRecord.getMinBufferSize(
                AudioConfig.SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            if (minBufferSize <= 0) {
                // If we fail here, try to stop service
                sendServiceAction(RecordingService.ACTION_STOP, fromRecorder = true)
                throw AudioRecorderException("Device doesn't support this audio configuration")
            }

            val bufferSize = minBufferSize * 2

            @Suppress("MissingPermission")
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AudioConfig.SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            ).also { record ->
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    sendServiceAction(RecordingService.ACTION_STOP, fromRecorder = true)
                    throw AudioRecorderException("Failed to initialize AudioRecord")
                }
            }

            audioData.reset()
            audioRecord?.startRecording()
            _status.value = RecordingStatus.Recording(0f)

            Log.d(TAG, "Recording started at ${AudioConfig.SAMPLE_RATE}Hz")

            // Launch recording coroutine
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ByteArray(bufferSize)

                while (isActive) {
                    val record = audioRecord ?: break
                    val bytesRead = record.read(buffer, 0, buffer.size)

                    when {
                        bytesRead > 0 -> {
                            audioData.write(buffer, 0, bytesRead)
                            val amplitude = calculateAmplitudeDb(buffer, bytesRead)
                            _status.value = RecordingStatus.Recording(amplitude)
                        }

                        bytesRead == AudioRecord.ERROR_INVALID_OPERATION -> {
                            _status.value = RecordingStatus.Error("Recording error: invalid operation")
                            break
                        }

                        bytesRead == AudioRecord.ERROR_BAD_VALUE -> {
                            _status.value = RecordingStatus.Error("Recording error: bad value")
                            break
                        }
                    }
                }
            }
        }
    }

    override suspend fun cancelRecording() {
        Log.d(TAG, "cancelRecording() called")
        stopInternal()
        // Send STOP action to service to reset notification
        sendServiceAction(RecordingService.ACTION_STOP, fromRecorder = true) 
    }

    override suspend fun stopRecording(): ByteArray {
        Log.d(TAG, "stopRecording() called")

        if (recordingJob?.isActive != true) {
            throw AudioRecorderException("Not currently recording")
        }

        val data = stopInternal()
        Log.d(TAG, "Recording stopped, ${data.size} bytes captured")
        return data
    }

    private suspend fun stopInternal(): ByteArray {
        recordingJob?.cancelAndJoin()
        recordingJob = null

        return withContext(Dispatchers.IO) {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            _status.value = RecordingStatus.Idle
            val data = audioData.toByteArray()
            audioData.reset()
            data
        }
    }

    override fun isRecording(): Boolean = recordingJob?.isActive == true

    override fun release() {
        Log.d(TAG, "release() called")
        CoroutineScope(Dispatchers.IO).launch {
            stopInternal()
        }
        val intent = Intent(context, RecordingService::class.java)
        context.stopService(intent)
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════

    private fun sendServiceAction(action: String, fromRecorder: Boolean = false) {
        val intent = Intent(context, RecordingService::class.java).apply {
            this.action = action
            if (fromRecorder) {
                putExtra(RecordingService.EXTRA_FROM_RECORDER, true)
            }
        }
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send service action: $action", e)
        }
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