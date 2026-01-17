package com.liftley.vodrop.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitLogOut
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "SubscriptionManager"

/**
 * Manages subscriptions via RevenueCat (v1: Monthly only)
 */
class SubscriptionManager(private val context: Context) {

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var monthlyPackage: Package? = null

    /**
     * Initialize RevenueCat SDK
     */
    fun initialize(userId: String? = null) {
        Log.d(TAG, "Initializing RevenueCat with userId: $userId")

        Purchases.logLevel = LogLevel.DEBUG

        val configBuilder = PurchasesConfiguration.Builder(context, AuthConfig.REVENUECAT_API_KEY)
        if (userId != null) {
            configBuilder.appUserID(userId)
        }

        Purchases.configure(configBuilder.build())

        // Listen for customer info updates
        Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { customerInfo ->
            updateProStatus(customerInfo)
        }
    }

    /**
     * Check if user has Pro entitlement
     */
    suspend fun checkProStatus() {
        _isLoading.value = true
        try {
            val customerInfo = Purchases.sharedInstance.awaitCustomerInfo()
            updateProStatus(customerInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking pro status", e)
            _isPro.value = false
        } finally {
            _isLoading.value = false
        }
    }

    private fun updateProStatus(customerInfo: CustomerInfo) {
        val hasPro = customerInfo.entitlements[AuthConfig.ENTITLEMENT_PRO]?.isActive == true
        Log.d(TAG, "Pro status: $hasPro")
        _isPro.value = hasPro
    }

    /**
     * Purchase monthly subscription
     */
    suspend fun purchaseMonthly(activity: Activity): Boolean {
        val pkg = monthlyPackage
        if (pkg == null) {
            Log.e(TAG, "Monthly package not available")
            return false
        }

        return try {
            val params = PurchaseParams.Builder(activity, pkg).build()
            val result = Purchases.sharedInstance.awaitPurchase(params)
            updateProStatus(result.customerInfo)
            _isPro.value
        } catch (e: Exception) {
            Log.e(TAG, "Purchase error", e)
            false
        }
    }

    /**
     * Fetch available packages from RevenueCat.
     * TODO: Call this from MainActivity.initAuth() to get real prices
     */
    suspend fun fetchPackages() {}

    /**
     * Get formatted monthly price from RevenueCat.
     * TODO: Use this instead of hardcoded AuthConfig.PRICE_MONTHLY_USD
     */
    fun getMonthlyPrice() {}

    /**
     * Restore purchases (for users who reinstall).
     * TODO: Add "Restore Purchases" button in Settings screen post-v1
     */
    suspend fun restorePurchases() {}

    /**
     * Link RevenueCat with Firebase user
     */
    suspend fun loginWithFirebaseUser(firebaseUserId: String) {
        try {
            val result = Purchases.sharedInstance.awaitLogIn(firebaseUserId)
            updateProStatus(result.customerInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error logging in to RevenueCat", e)
        }
    }

    /**
     * Logout
     */
    suspend fun logout() {
        try {
            Purchases.sharedInstance.awaitLogOut()
            _isPro.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error logging out", e)
        }
    }
}