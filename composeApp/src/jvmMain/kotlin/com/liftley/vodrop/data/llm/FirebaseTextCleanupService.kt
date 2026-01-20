package com.liftley.vodrop.data.llm

import com.liftley.vodrop.data.firebase.FirebaseFunctionsService

class FirebaseTextCleanupService(
    private val firebaseFunctions: FirebaseFunctionsService
) : TextCleanupService {

    override suspend fun cleanupText(rawText: String, style: CleanupStyle): Result<String> {
        if (rawText.isBlank() || rawText.length < 10) {
            return Result.success(rawText)
        }
        return firebaseFunctions.cleanupText(rawText, style.name.lowercase())
    }

    override fun isAvailable(): Boolean = true
}