package com.liftley.vodrop.data.firebase

/**
 * Service interface for calling Firebase Cloud Functions.
 * Android: Uses Firebase SDK
 * Desktop: Stub (not supported)
 */
interface FirebaseFunctionsService {
    suspend fun transcribe(audioData: ByteArray): Result<String>
    suspend fun cleanupText(text: String, style: String): Result<String>
}