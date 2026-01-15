package com.liftley.vodrop.di

import org.koin.dsl.module

val platformModule = module {
    single { DatabaseDriverFactory() }
}