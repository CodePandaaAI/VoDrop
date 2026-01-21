package com.liftley.vodrop.di

import com.liftley.vodrop.data.audio.createAudioRecorder
import com.liftley.vodrop.domain.repository.TranscriptionRepositoryImpl
import com.liftley.vodrop.db.VoDropDatabase
import com.liftley.vodrop.domain.repository.TranscriptionRepository
import com.liftley.vodrop.data.stt.createSpeechToTextEngine
import com.liftley.vodrop.domain.usecase.ManageHistoryUseCase
import com.liftley.vodrop.domain.usecase.TranscribeAudioUseCase
import com.liftley.vodrop.ui.main.MainViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for shared/common dependencies.
 *
 * This module defines dependencies that are shared across all platforms.
 * Platform-specific implementations are provided via platformModule.
 *
 * Dependency Graph:
 * ```
 * DatabaseDriverFactory ─────► SqlDriver ─────► VoDropDatabase
 *                                                    │
 *                              TranscriptionRepository ◄──┘
 *                                                    │
 *                              ManageHistoryUseCase ◄──┘
 *                                                    │
 * MainViewModel ◄────────────────────────────────────┤
 *     │                                              │
 *     ├── audioRecorder: AudioRecorder               │
 *     ├── sttEngine: SpeechToTextEngine              │
 *     ├── transcribeUseCase: TranscribeAudioUseCase ─┘
 *     └── historyUseCase: ManageHistoryUseCase
 *
 * TranscribeAudioUseCase ◄── sttEngine: SpeechToTextEngine (platform)
 *                        ◄── cleanupService: TextCleanupService (platform)
 * ```
 */
val appModule = module {

    // ═══════════ DATABASE ═══════════
    // DatabaseDriverFactory is provided by platformModule
    single { get<DatabaseDriverFactory>().createDriver() }
    single { VoDropDatabase(get()) }
    single<TranscriptionRepository> { TranscriptionRepositoryImpl(get()) }

    // ═══════════ SERVICES ═══════════
    // Platform-specific implementations created via expect/actual
    single { createAudioRecorder() }
    single { createSpeechToTextEngine() }

    // ═══════════ USE CASES ═══════════
    // TranscribeAudioUseCase dependencies:
    // - sttEngine: SpeechToTextEngine (from createSpeechToTextEngine())
    // - cleanupService: TextCleanupService (from platformModule)
    // v1: No PreferencesManager - CleanupStyle hardcoded to INFORMAL
    single { TranscribeAudioUseCase(get(), get()) }
    single { ManageHistoryUseCase(get()) }

    // ═══════════ VIEWMODEL ═══════════
    viewModel {
        MainViewModel(
            audioRecorder = get(),
            sttEngine = get(),
            transcribeUseCase = get(),
            historyUseCase = get(),
            platformAuth = get()
        )
    }
}