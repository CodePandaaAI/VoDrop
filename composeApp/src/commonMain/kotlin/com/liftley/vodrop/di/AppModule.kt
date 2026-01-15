package com.liftley.vodrop.di

import com.liftley.vodrop.audio.createAudioRecorder
import com.liftley.vodrop.db.VoDropDatabase
import com.liftley.vodrop.repository.TranscriptionRepository
import com.liftley.vodrop.stt.createSpeechToTextEngine
import com.liftley.vodrop.ui.MainViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { get<DatabaseDriverFactory>().createDriver() }
    single { VoDropDatabase(get()) }
    single { TranscriptionRepository(get()) }
    single { createAudioRecorder() }
    single { createSpeechToTextEngine() }
    viewModel { MainViewModel(get(), get(), get()) }
}