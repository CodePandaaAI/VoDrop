package com.liftley.vodrop.domain.model

/**
 * **Unified Application State**
 * 
 * This Sealed Interface represents the entire state of the recording/transcription session.
 * It follows a strict Unidirectional Data Flow.
 * 
 * **Single Source of Truth:**
 * - Managed by: [RecordingSessionManager]
 * - Observed by: [MainViewModel] (UI) and [RecordingService] (Android Foreground Service).
 * 
 * **Flow:**
 * [Ready] -> [Recording] -> [Processing] -> [Success] / [Error]
 */
sealed interface AppState {
    
    /** 
     * Initial state. The app is idle and waiting for user input.
     * Mic is inactive. 
     */
    data object Ready : AppState
    
    /** 
     * actively recording audio. 
     * The Foreground Service is running and showing a notification.
     */
    data object Recording : AppState
    
    /** 
     * Intermediate state during the pipeline execution.
     * @property message User-facing status (e.g., "Uploading...", "Transcribing...", "Polishing...").
     */
    data class Processing(val message: String) : AppState
    
    /** 
     * Terminal success state. 
     * @property text The final result to be displayed (either raw or polished).
     */
    data class Success(val text: String) : AppState
    
    /** 
     * Terminal error state.
     * @property message Error details to be shown in a dialog/snackbar.
     */
    data class Error(val message: String) : AppState
}
