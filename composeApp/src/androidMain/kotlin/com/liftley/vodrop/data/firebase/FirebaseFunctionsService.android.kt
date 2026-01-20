package com.liftley.vodrop.data.firebase

import android.util.Base64
import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

private const val TAG = "FirebaseFunctions"

class AndroidFirebaseFunctionsService : FirebaseFunctionsService {

    private val functions: FirebaseFunctions by lazy {
        FirebaseFunctions.getInstance()
    }

    override suspend fun transcribe(audioData: ByteArray): Result<String> {
        return try {
            Log.d(TAG, "Calling transcribe function with ${audioData.size} bytes")

            // Convert audio to base64
            val audioBase64 = Base64.encodeToString(audioData, Base64.NO_WRAP)

            val data = hashMapOf("audio" to audioBase64)

            val result = functions
                .getHttpsCallable("transcribe")
                .call(data)
                .await()

            // Use getData() instead of .data
            @Suppress("UNCHECKED_CAST")
            val response = result.getData() as? Map<String, Any>
            val text = response?.get("text") as? String ?: ""

            Log.d(TAG, "Transcription success: ${text.take(50)}...")
            Result.success(text)
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            Result.failure(e)
        }
    }

    override suspend fun cleanupText(text: String, style: String): Result<String> {
        return try {
            Log.d(TAG, "Calling cleanupText function, style: $style")

            val data = hashMapOf(
                "text" to text,
                "style" to style
            )

            val result = functions
                .getHttpsCallable("cleanupText")
                .call(data)
                .await()

            // Use getData() instead of .data
            @Suppress("UNCHECKED_CAST")
            val response = result.getData() as? Map<String, Any>
            val cleaned = response?.get("text") as? String ?: text

            Log.d(TAG, "Cleanup success: ${cleaned.take(50)}...")
            Result.success(cleaned)
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
            Result.failure(e)
        }
    }
}