package com.liftley.vodrop.data.llm

import android.util.Log
import com.liftley.vodrop.data.firebase.FirebaseFunctionsService

private const val TAG = "FirebaseCleanup"

/**
 * Text cleanup service using Firebase Cloud Functions.
 * All API keys are secured on the server.
 */
class FirebaseTextCleanupService(
    private val firebaseFunctions: FirebaseFunctionsService
) : TextCleanupService {

    override suspend fun cleanupText(rawText: String, style: CleanupStyle): Result<String> {
        if (rawText.isBlank() || rawText.length < 10) {
            return Result.success(rawText)
        }

        Log.d(TAG, "Cleanup request: ${rawText.length} chars, style: ${style.name}")

        return firebaseFunctions.cleanupText(rawText, style.name.lowercase())
    }

    override fun isAvailable(): Boolean = true
}