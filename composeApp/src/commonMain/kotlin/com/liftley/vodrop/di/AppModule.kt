package com.liftley.vodrop.di

import com.liftley.vodrop.data.audio.createAudioRecorder
import com.liftley.vodrop.db.VoDropDatabase
import com.liftley.vodrop.domain.repository.TranscriptionRepository
import com.liftley.vodrop.data.stt.GroqConfig
import com.liftley.vodrop.data.stt.GroqWhisperService
import com.liftley.vodrop.data.stt.createSpeechToTextEngine
import com.liftley.vodrop.ui.main.MainViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { get<DatabaseDriverFactory>().createDriver() }
    single { VoDropDatabase(get()) }
    single { TranscriptionRepository(get()) }
    single { createAudioRecorder() }
    single { createSpeechToTextEngine() }
    single { GroqWhisperService(GroqConfig.API_KEY, get()) }
    viewModel { MainViewModel(get(), get(), get(), get(), get()) }  // Added 5th parameter
}