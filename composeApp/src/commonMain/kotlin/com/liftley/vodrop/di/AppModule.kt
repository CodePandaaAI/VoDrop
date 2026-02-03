package com.liftley.vodrop.di

import com.liftley.vodrop.data.audio.createAudioRecorder
import com.liftley.vodrop.data.cloud.createCloudTranscriptionService
import com.liftley.vodrop.domain.repository.TranscriptionRepositoryImpl
import com.liftley.vodrop.db.VoDropDatabase
import com.liftley.vodrop.domain.repository.TranscriptionRepository
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

    // ═══════════ CLOUD SERVICES ═══════════
    single { createAudioRecorder() }
    single { createCloudTranscriptionService() }  // Unified: transcription + polish
    single<ServiceController> { createServiceController() }

    // ═══════════ USE CASES ═══════════
    single { TranscribeAudioUseCase(get()) }  // Single dependency now!

    // ═══════════ MANAGERS (SSOT) ═══════════
    single {
        RecordingSessionManager(
            audioRecorder = get(),
            transcribeUseCase = get(),
            historyRepository = get(),
            serviceController = get()
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