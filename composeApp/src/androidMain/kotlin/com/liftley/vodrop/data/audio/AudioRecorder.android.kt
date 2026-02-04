package com.liftley.vodrop.data.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.ByteArrayOutputStream

private const val TAG = "AudioRecorder"

/**
 * **Android Audio Recorder Implementation**
 * 
 * Uses the low-level [AudioRecord] API to capture raw PCM bytes.
 * 
 * **Constraint Compliance:**
 * - Sample Rate: [AudioConfig.SAMPLE_RATE] (16kHz)
 * - Channel: Mono
 * - Format: 16-bit PCM
 * 
 * **Memory Strategy:**
 * Captures to an in-memory [ByteArrayOutputStream]. 
 * Note: 1 minute of audio = ~1.9MB. 
 * This is safe for short voice notes (up to 5-10 mins) but not for hour-long meetings.
 */
class AndroidAudioRecorder : AudioRecorder, KoinComponent {

    private val context: Context by inject()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    // Pre-allocating buffer for ~30 seconds to minimize resizing
    // 16000 * 2 (bytes) * 30 = 960,000 bytes
    private var audioData = ByteArrayOutputStream(960_000)

    companion object {
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    override suspend fun startRecording() {
        Log.d(TAG, "startRecording() called")

        // Idempotency check
        if (recordingJob?.isActive == true) {
            Log.d(TAG, "startRecording() called while already recording, ignoring.")
            return
        }

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw AudioRecorderException("MICROPHONE permission not granted")
        }

        withContext(Dispatchers.IO) {
            val minBufferSize = AudioRecord.getMinBufferSize(
                AudioConfig.SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            if (minBufferSize <= 0) {
                throw AudioRecorderException("Device doesn't support this audio configuration")
            }

            // Double the buffer for safety
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
                    throw AudioRecorderException("Failed to initialize AudioRecord")
                }
            }

            audioData.reset()
            audioRecord?.startRecording()

            Log.d(TAG, "Recording started at ${AudioConfig.SAMPLE_RATE}Hz")

            // Block-free reading loop
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ByteArray(bufferSize)

                while (isActive) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: break

                    when {
                        bytesRead > 0 -> {
                            // Accumulate data
                            audioData.write(buffer, 0, bytesRead)
                        }

                        bytesRead == AudioRecord.ERROR_INVALID_OPERATION -> {
                            Log.e(TAG, "Recording error: invalid operation")
                            break
                        }

                        bytesRead == AudioRecord.ERROR_BAD_VALUE -> {
                            Log.e(TAG, "Recording error: bad value")
                            break
                        }
                    }
                }
            }
        }
    }

    override suspend fun cancelRecording() {
        Log.d(TAG, "cancelRecording() called")
        stopInternalWithoutData()
    }

    override suspend fun stopRecording(): ByteArray {
        Log.d(TAG, "stopRecording() called")

        if (recordingJob?.isActive != true) {
            throw AudioRecorderException("Not currently recording")
        }

        val data = stopInternalWithData()
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
            
            val data = audioData.toByteArray()
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
            audioData.reset()
        }
    }

    override fun isRecording(): Boolean = recordingJob?.isActive == true
}

actual fun createAudioRecorder(): AudioRecorder = AndroidAudioRecorder()