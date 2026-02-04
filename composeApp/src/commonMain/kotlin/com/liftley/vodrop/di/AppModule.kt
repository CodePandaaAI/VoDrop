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
 * **Dependency Injection Graph (Koin)**
 * 
 * Defines the application's wiring.
 * 
 * **Strict Singleton Policy:**
 * - [RecordingSessionManager] MUST be a `single`. It holds the global state.
 * - [AudioRecorder] and [CloudTranscriptionService] are stateless utils, so `single` is efficient.
 * - [MainViewModel] is a `viewModel` (scoped to screen lifecycle).
 * 
 * **Data Flow:**
 * UI -> ViewModel -> SessionManager -> UseCases -> Repositories/Services
 */
val appModule = module {

    // ═══════════ DATA LAYER ═══════════
    single { get<DatabaseDriverFactory>().createDriver() }
    single { VoDropDatabase(get()) }
    single<TranscriptionRepository> { TranscriptionRepositoryImpl(get()) }

    // ═══════════ INFRASTRUCTURE ═══════════
    single { createAudioRecorder() }
    single { createCloudTranscriptionService() }  // Unified backend interface
    single<ServiceController> { createServiceController() }

    // ═══════════ DOMAIN USE CASES ═══════════
    single { TranscribeAudioUseCase(get()) }

    // ═══════════ STATE MANAGEMENT (SSOT) ═══════════
    // Critical: This instance lives as long as the app process.
    single {
        RecordingSessionManager(
            audioRecorder = get(),
            transcribeUseCase = get(),
            historyRepository = get(),
            serviceController = get()
        )
    }

    // ═══════════ UI LAYER ═══════════
    viewModel {
        MainViewModel(
            sessionManager = get(),
            historyRepository = get(),
            transcribeUseCase = get()
        )
    }
}