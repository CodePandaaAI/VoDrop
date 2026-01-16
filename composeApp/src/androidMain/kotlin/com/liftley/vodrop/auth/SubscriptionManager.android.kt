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
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitLogOut
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "SubscriptionManager"

/**
 * Manages subscriptions via RevenueCat
 */
class SubscriptionManager(private val context: Context) {

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var monthlyPackage: Package? = null
    private var yearlyPackage: Package? = null

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
     * Fetch available packages
     */
    suspend fun fetchPackages() {
        try {
            val offerings = Purchases.sharedInstance.awaitOfferings()
            val currentOffering = offerings.current

            monthlyPackage = currentOffering?.monthly
            yearlyPackage = currentOffering?.annual

            Log.d(TAG, "Monthly package: ${monthlyPackage?.identifier}")
            Log.d(TAG, "Yearly package: ${yearlyPackage?.identifier}")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching packages", e)
        }
    }

    /**
     * Get formatted prices
     */
    fun getMonthlyPrice(): String {
        return monthlyPackage?.product?.price?.formatted ?: "₹129/month"
    }

    fun getYearlyPrice(): String {
        return yearlyPackage?.product?.price?.formatted ?: "₹999/year"
    }

    /**
     * Purchase monthly subscription
     */
    suspend fun purchaseMonthly(activity: Activity): Boolean {
        val pkg = monthlyPackage ?: return false
        return purchase(activity, pkg)
    }

    /**
     * Purchase yearly subscription
     */
    suspend fun purchaseYearly(activity: Activity): Boolean {
        val pkg = yearlyPackage ?: return false
        return purchase(activity, pkg)
    }

    private suspend fun purchase(activity: Activity, pkg: Package): Boolean {
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
     * Restore purchases (for users who reinstall)
     */
    suspend fun restorePurchases(): Boolean {
        return try {
            val customerInfo = Purchases.sharedInstance.awaitCustomerInfo()
            updateProStatus(customerInfo)
            _isPro.value
        } catch (e: Exception) {
            Log.e(TAG, "Restore error", e)
            false
        }
    }

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