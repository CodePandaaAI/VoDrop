package com.liftley.vodrop.data.firebase

import android.util.Base64
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

private const val TAG = "FirebaseFunctions"

class AndroidFirebaseFunctionsService : FirebaseFunctionsService {

    private val functions: FirebaseFunctions by lazy {
        FirebaseFunctions.getInstance()
    }

    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    private suspend fun ensureAuth() {
        if (auth.currentUser == null) {
            Log.d(TAG, "No user signed in. Signing in anonymously...")
            try {
                auth.signInAnonymously().await()
                Log.d(TAG, "Anonymous sign-in success. UID: ${auth.currentUser?.uid}")
            } catch (e: Exception) {
                Log.e(TAG, "Anonymous sign-in failed", e)
                // Continue anyway, maybe unauthenticated access is allowed on server
                // but usually this fails if SDK expects it.
            }
        }
    }

    override suspend fun transcribe(audioData: ByteArray): Result<String> {
        return try {
            ensureAuth() // Authenticate first

            Log.d(TAG, "Calling transcribe function with ${audioData.size} bytes")

            // Convert audio to base64
            val audioBase64 = Base64.encodeToString(audioData, Base64.NO_WRAP)

            val data = hashMapOf("audio" to audioBase64)

            val result = functions
                .getHttpsCallable("transcribe")
                .apply { setTimeout(540, java.util.concurrent.TimeUnit.SECONDS) }
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
            ensureAuth() // Authenticate first

            Log.d(TAG, "Calling cleanupText function, style: $style")

            val data = hashMapOf(
                "text" to text,
                "style" to style
            )

            val result = functions
                .getHttpsCallable("cleanupText")
                .apply { setTimeout(540, java.util.concurrent.TimeUnit.SECONDS) }
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
