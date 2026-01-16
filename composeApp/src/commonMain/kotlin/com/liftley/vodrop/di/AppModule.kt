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

val appModule = module {
    // Database
    single { get<DatabaseDriverFactory>().createDriver() }
    single { VoDropDatabase(get()) }
    single<TranscriptionRepository> { TranscriptionRepositoryImpl(get()) }

    // Services
    single { createAudioRecorder() }
    single { createSpeechToTextEngine() }

    // Use Cases
    // TranscribeAudioUseCase now needs: sttEngine, textCleanupService, preferencesManager
    single { TranscribeAudioUseCase(get(), get(), get()) }
    single { ManageHistoryUseCase(get()) }

    // ViewModel
    viewModel {
        MainViewModel(
            audioRecorder = get(),
            sttEngine = get(),
            transcribeUseCase = get(),
            historyUseCase = get()
        )
    }
}