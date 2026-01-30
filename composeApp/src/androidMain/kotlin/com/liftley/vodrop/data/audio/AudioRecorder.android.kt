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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.ByteArrayOutputStream

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

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw AudioRecorderException("MICROPHONE permission not granted")
        }

        // ✅ Start foreground service FIRST (on Main thread)
        sendServiceAction(RecordingService.ACTION_START)

        withContext(Dispatchers.IO) {
            val minBufferSize = AudioRecord.getMinBufferSize(
                AudioConfig.SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            if (minBufferSize <= 0) {
                // If we fail here, try to stop service
                sendServiceAction(RecordingService.ACTION_STOP)
                throw AudioRecorderException("Device doesn't support this audio configuration")
            }

            val bufferSize = minBufferSize * 2

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AudioConfig.SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            ).also { record ->
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    sendServiceAction(RecordingService.ACTION_STOP)
                    throw AudioRecorderException("Failed to initialize AudioRecord")
                }
            }

            audioData.reset()
            audioRecord?.startRecording()
            _status.update { RecordingStatus.Recording }

            Log.d(TAG, "Recording started at ${AudioConfig.SAMPLE_RATE}Hz")

            // Launch recording coroutine
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ByteArray(bufferSize)

                while (isActive) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: break

                    when {
                        bytesRead > 0 -> {
                            audioData.write(buffer, 0, bytesRead)
                            _status.update { RecordingStatus.Recording }
                        }

                        bytesRead == AudioRecord.ERROR_INVALID_OPERATION -> {
                            _status.update { RecordingStatus.Error("Recording error: invalid operation") }
                            break
                        }

                        bytesRead == AudioRecord.ERROR_BAD_VALUE -> {
                            _status.update { RecordingStatus.Error("Recording error: bad value") }
                            break
                        }
                    }
                }
            }
        }
    }

    override suspend fun cancelRecording() {
        Log.d(TAG, "cancelRecording() called")
        stopInternalWithoutData()  // Don't copy data
        sendServiceAction(RecordingService.ACTION_STOP)
    }

    override suspend fun stopRecording(): ByteArray {
        Log.d(TAG, "stopRecording() called")

        if (recordingJob?.isActive != true) {
            throw AudioRecorderException("Not currently recording")
        }

        val data = stopInternalWithData()  // Copy data
        Log.d(TAG, "Recording stopped, ${data.size} bytes captured")
        return data
    }

    private suspend fun stopInternalWithData(): ByteArray {
        recordingJob?.cancelAndJoin()
        recordingJob = null

        return withContext(Dispatchers.IO) {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            _status.value = RecordingStatus.Idle
            val data = audioData.toByteArray()  // Copy only when needed
            audioData.reset()
            data
        }
    }

    private suspend fun stopInternalWithoutData() {
        recordingJob?.cancelAndJoin()
        recordingJob = null

        withContext(Dispatchers.IO) {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            _status.value = RecordingStatus.Idle
            audioData.reset()  // Just clear, don't copy
        }
    }

    override fun isRecording(): Boolean = recordingJob?.isActive == true

    // ═══════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════

    private fun sendServiceAction(action: String) {
        val intent = Intent(context, RecordingService::class.java).apply {
            this.action = action
            putExtra(RecordingService.EXTRA_FROM_RECORDER, true)
        }
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send service action: $action", e)
        }
    }
}

actual fun createAudioRecorder(): AudioRecorder = AndroidAudioRecorder()