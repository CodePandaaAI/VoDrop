package com.liftley.vodrop.di

import com.liftley.vodrop.data.audio.createAudioRecorder
import com.liftley.vodrop.domain.repository.TranscriptionRepositoryImpl
import com.liftley.vodrop.db.VoDropDatabase
import com.liftley.vodrop.domain.repository.TranscriptionRepository
import com.liftley.vodrop.data.stt.createSpeechToTextEngine
import com.liftley.vodrop.domain.manager.RecordingSessionManager
import com.liftley.vodrop.domain.usecase.ManageHistoryUseCase
import com.liftley.vodrop.domain.usecase.TranscribeAudioUseCase
import com.liftley.vodrop.ui.main.MainViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for shared/common dependencies.
 * Hackathon version - no auth, no payments.
 */
val appModule = module {

    // ═══════════ DATABASE ═══════════
    single { get<DatabaseDriverFactory>().createDriver() }
    single { VoDropDatabase(get()) }
    single<TranscriptionRepository> { TranscriptionRepositoryImpl(get()) }

    // ═══════════ SERVICES ═══════════
    single { createAudioRecorder() }
    single { createSpeechToTextEngine() }

    // ═══════════ USE CASES ═══════════
    single { TranscribeAudioUseCase(get(), get()) }
    single { ManageHistoryUseCase(get()) }

    // ═══════════ MANAGERS (SSOT) ═══════════
    single { 
        RecordingSessionManager(
            audioRecorder = get(),
            transcribeUseCase = get(),
            historyUseCase = get()
        ) 
    }

    // ═══════════ VIEWMODEL ═══════════
    viewModel {
        MainViewModel(
            sessionManager = get(),
            historyUseCase = get(),
            transcribeUseCase = get()
        )
    }
}