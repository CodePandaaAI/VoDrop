package com.liftley.vodrop.auth

/**
 * Authentication and subscription configuration constants.
 *
 * SECURITY WARNING:
 * API keys are currently hardcoded for v1 closed launch.
 * Before public release:
 * - Move to secure backend
 * - Or use BuildConfig with local.properties
 */
object AuthConfig {

    // ═══════════ REVENUECAT ═══════════

    /** RevenueCat public API key (safe to include in app) */
    const val REVENUECAT_API_KEY = "test_VdhpzgqMRstecnlYlWMrmEypIkh"

    /** Product ID for monthly subscription (must match Play Console) */
    const val PRODUCT_PRO_MONTHLY = "vodrop_pro_monthly"
    // TODO: Add PRODUCT_PRO_YEARLY when yearly plan is implemented post-v1

    /** Entitlement ID (must match RevenueCat dashboard) */
    const val ENTITLEMENT_PRO = "pro"

    // ═══════════ PRICING (Display Only) ═══════════

    /** Monthly price in USD (actual price comes from Play Console) */
    const val PRICE_MONTHLY_USD = "$2.99"

    /** Monthly price in INR (approximate, actual from Play Console) */
    const val PRICE_MONTHLY_INR = "₹252"

    // ═══════════ LIMITS ═══════════

    /** Number of free transcriptions for new users */
    const val FREE_TRIALS = 3

    /** Pro plan monthly limit in minutes */
    const val PRO_MONTHLY_MINUTES = 120

    const val WEB_CLIENT_ID = "808998462431-v1mec4tnrgbosfkskedeb4kouodb8qm6.apps.googleusercontent.com"
}