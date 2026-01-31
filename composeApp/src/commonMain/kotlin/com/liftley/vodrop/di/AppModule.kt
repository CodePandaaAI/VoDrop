package com.liftley.vodrop.di

import com.liftley.vodrop.data.audio.createAudioRecorder
import com.liftley.vodrop.domain.repository.TranscriptionRepositoryImpl
import com.liftley.vodrop.db.VoDropDatabase
import com.liftley.vodrop.domain.repository.TranscriptionRepository
import com.liftley.vodrop.data.stt.createSpeechToTextEngine
import com.liftley.vodrop.domain.manager.RecordingSessionManager
import com.liftley.vodrop.domain.usecase.TranscribeAudioUseCase
import com.liftley.vodrop.service.ServiceController
import com.liftley.vodrop.service.createServiceController
import com.liftley.vodrop.ui.main.MainViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for shared/common dependencies.
 * 
 * Architecture: Unified AppState with unidirectional data flow.
 * - SessionManager is SSOT for recording state
 * - ViewModel directly exposes SessionManager.state
 * - All components observe AppState (no translation)
 */
val appModule = module {

    // ═══════════ DATABASE ═══════════
    single { get<DatabaseDriverFactory>().createDriver() }
    single { VoDropDatabase(get()) }
    single<TranscriptionRepository> { TranscriptionRepositoryImpl(get()) }

    // ═══════════ SERVICES ═══════════
    single { createAudioRecorder() }
    single { createSpeechToTextEngine() }
    single<ServiceController> { createServiceController() }  // Platform-specific service control

    // ═══════════ USE CASES ═══════════
    single { TranscribeAudioUseCase(get(), get()) }

    // ═══════════ MANAGERS (SSOT) ═══════════
    single {
        RecordingSessionManager(
            audioRecorder = get(),
            transcribeUseCase = get(),
            historyRepository = get(),
            serviceController = get()  // NEW: service lifecycle control
        )
    }

    // ═══════════ VIEWMODEL ═══════════
    viewModel {
        MainViewModel(
            sessionManager = get(),
            historyRepository = get(),
            transcribeUseCase = get()
        )
    }
}