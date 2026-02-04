package com.liftley.vodrop.domain.model

/**
 * Unified application state for the recording flow.
 * Single Source of Truth - used by SessionManager, ViewModel, and Service.
 */
sealed interface AppState {
    /** Ready to record */
    data object Ready : AppState
    
    /** Currently recording audio */
    data object Recording : AppState
    
    /** Processing audio (transcribing, polishing) */
    data class Processing(val message: String) : AppState
    
    /** Transcription complete */
    data class Success(val text: String) : AppState
    
    /** An error occurred */
    data class Error(val message: String) : AppState
}
