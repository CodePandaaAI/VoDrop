package com.liftley.vodrop.di

import com.liftley.vodrop.auth.AccessManager
import com.liftley.vodrop.auth.FirebaseAuthManager
import com.liftley.vodrop.auth.SubscriptionManager
import com.liftley.vodrop.data.firestore.DeviceManager
import com.liftley.vodrop.data.firestore.FirestoreManager
import com.liftley.vodrop.data.llm.GeminiCleanupService
import com.liftley.vodrop.data.llm.LLMConfig
import com.liftley.vodrop.data.llm.TextCleanupService
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module for Android-specific dependencies.
 *
 * Provides:
 * - Database driver (SQLDelight)
 * - Auth/Subscription managers (Firebase, RevenueCat)
 * - Firestore managers (user data, device ID)
 * - Access manager (unified access control)
 * - Text cleanup service (Gemini API)
 * - Preferences manager (SharedPreferences)
 * - HTTP client (OkHttp)
 *
 * Dependency Graph (Android-specific):
 * ```
 * FirebaseAuthManager ─────────┐
 * SubscriptionManager ─────────┼──► AccessManager
 * FirestoreManager ────────────┤
 * DeviceManager ───────────────┘
 *
 * TextCleanupService ◄── GeminiCleanupService
 * ```
 *
 * @see appModule for common dependencies
 */
val platformModule = module {

    // ═══════════ DATABASE ═══════════
    single { DatabaseDriverFactory(androidContext()) }

    // ═══════════ AUTH & SUBSCRIPTION ═══════════
    single { FirebaseAuthManager() }
    single { SubscriptionManager(androidContext()) }

    // ═══════════ FIRESTORE ═══════════
    single { FirestoreManager() }
    single { DeviceManager(androidContext()) }

    // ═══════════ ACCESS MANAGER ═══════════
    // Combines: auth state + subscription + device restriction + usage tracking
    single { AccessManager(get(), get(), get()) }

    // ═══════════ TEXT CLEANUP (Gemini) ═══════════
    // TODO: Move API key to secure backend before production release
    single<TextCleanupService> { GeminiCleanupService(LLMConfig.GEMINI_API_KEY) }

    // ═══════════ HTTP CLIENT ═══════════
    single { HttpClient(OkHttp) }
}