package com.liftley.vodrop.data.llm

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

private const val TAG = "TextCleanup"

/**
 * Text cleanup service using Firebase Cloud Functions.
 * Calls the cleanupText function directly - no extra wrapper needed.
 */
class FirebaseTextCleanupService : TextCleanupService {

    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private suspend fun ensureAuth() {
        if (auth.currentUser == null) {
            try {
                auth.signInAnonymously().await()
                Log.d(TAG, "Anonymous sign-in success")
            } catch (e: Exception) {
                Log.e(TAG, "Anonymous sign-in failed", e)
            }
        }
    }

    override suspend fun cleanupText(rawText: String): Result<String> {
        if (rawText.isBlank() || rawText.length < 10) {
            return Result.success(rawText)
        }

        return try {
            ensureAuth()
            
            Log.d(TAG, "Cleanup request: ${rawText.length} chars")

            val data = hashMapOf("text" to rawText)

            val result = functions
                .getHttpsCallable("cleanupText")
                .apply { setTimeout(300, java.util.concurrent.TimeUnit.SECONDS) }
                .call(data)
                .await()

            @Suppress("UNCHECKED_CAST")
            val response = result.getData() as? Map<String, Any>
            val cleaned = response?.get("text") as? String ?: rawText

            Log.d(TAG, "Cleanup success: ${cleaned.take(50)}...")
            Result.success(cleaned)
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup failed", e)
            Result.failure(e)
        }
    }

    override fun isAvailable(): Boolean = true
}