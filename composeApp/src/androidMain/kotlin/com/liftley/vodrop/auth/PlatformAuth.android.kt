package com.liftley.vodrop.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.liftley.vodrop.data.firestore.DeviceManager
import com.liftley.vodrop.data.firestore.FirestoreManager
import com.liftley.vodrop.data.firestore.UserData
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitLogOut
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference
import java.util.UUID

private const val TAG = "PlatformAuth"

/**
 * Android implementation of PlatformAuth.
 * Consolidates: FirebaseAuth + RevenueCat + Firestore access management.
 * SINGLE CLASS for all auth operations.
 */
actual class PlatformAuth(
    private val context: Context,
    private val firestoreManager: FirestoreManager,
    private val deviceManager: DeviceManager
) {
    // ═══════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════

    private val _accessState = MutableStateFlow(AccessState())
    actual val accessState: StateFlow<AccessState> = _accessState.asStateFlow()

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private var activityRef: WeakReference<Activity>? = null
    private var monthlyPackage: Package? = null

    private val activity: Activity? get() = activityRef?.get()

    fun setActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    // ═══════════════════════════════════════════════════════════════
    // INITIALIZE
    // ═══════════════════════════════════════════════════════════════

    actual fun initialize() {
        Log.d(TAG, "Initializing PlatformAuth...")

        // Initialize RevenueCat
        Purchases.logLevel = LogLevel.DEBUG
        val userId = auth.currentUser?.uid
        val configBuilder = PurchasesConfiguration.Builder(context, AuthConfig.REVENUECAT_API_KEY)
        if (userId != null) {
            configBuilder.appUserID(userId)
        }
        Purchases.configure(configBuilder.build())

        // Listen for RevenueCat updates
        Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { info ->
            updateAccessStateFromRevenueCat(info)
        }
    }
    /** Call this after Koin setup to load user state */
    suspend fun initializeAccess() {
        _accessState.value = _accessState.value.copy(isLoading = true)

        val isLoggedIn = auth.currentUser != null

        if (!isLoggedIn) {
            _accessState.value = AccessState(isLoading = false, isLoggedIn = false)
            return
        }

//        // Check Pro status from RevenueCat
//        val isPro = try {
//            val info = Purchases.sharedInstance.awaitCustomerInfo()
//            info.entitlements[AuthConfig.ENTITLEMENT_PRO]?.isActive == true
//        } catch (e: Exception) {
//            Log.e(TAG, "Error checking pro status", e)
//            false
//        }

        // ⚠️ TESTING: Force Pro status
        val isPro = true  // TODO: Revert before release

        // Load user data from Firestore (may fail if database doesn't exist)
        val deviceId = deviceManager.getDeviceId()
        val userData: UserData? = try {
            withTimeoutOrNull(15000) {  // 5 second timeout
                firestoreManager.getOrCreateUserData(deviceId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firestore error: ${e.message}")
            null
        }

        // UPDATE STATE EVEN IF FIRESTORE FAILS
        _accessState.value = AccessState(
            isLoading = false,
            isLoggedIn = true,  // ← Always set to true since auth.currentUser exists
            isPro = isPro,
            freeTrialsRemaining = userData?.freeTrialsRemaining ?: 3,
            usedMinutesThisMonth = userData?.getUsedMinutes() ?: 0,
            remainingMinutesThisMonth = userData?.getRemainingMinutes() ?: 120
        )

        Log.d(TAG, "✅ Initialized: ${_accessState.value}")
    }

    private fun updateAccessStateFromRevenueCat(info: CustomerInfo) {
        val isPro = info.entitlements[AuthConfig.ENTITLEMENT_PRO]?.isActive == true
        _accessState.value = _accessState.value.copy(isPro = isPro)
        Log.d(TAG, "RevenueCat update: isPro=$isPro")
    }

    // ═══════════════════════════════════════════════════════════════
    // SIGN IN
    // ═══════════════════════════════════════════════════════════════

    actual suspend fun signIn(): Result<User> {
        val act = activity ?: return Result.failure(Exception("No activity available"))

        return try {
            val credentialManager = CredentialManager.create(act)

            Log.d(TAG, "Starting sign-in with account chooser...")
            val result = tryGetCredential(credentialManager, act)

            if (result != null) {
                handleSignInResult(result)
            } else {
                Result.failure(Exception("No Google accounts found. Please add a Google account to your device."))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Sign in cancelled by user")
            Result.failure(Exception("Sign in cancelled"))
        } catch (e: GetCredentialException) {
            Log.e(TAG, "GetCredentialException: ${e.message}", e)
            Result.failure(Exception("Sign-in failed: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Sign in failed: ${e.message}", e)
            Result.failure(Exception("Sign-in failed: ${e.message}"))
        }
    }

    private suspend fun tryGetCredential(
        credentialManager: CredentialManager,
        activity: Activity
    ): GetCredentialResponse? {
        return try {
            val nonce = UUID.randomUUID().toString()

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)  // Show ALL accounts
                .setServerClientId(AuthConfig.WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)  // NEVER auto-select - always show chooser
                .setNonce(nonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            Log.d(TAG, "Showing account chooser...")
            credentialManager.getCredential(context = activity, request = request)
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No Google accounts on device")
            null
        } catch (e: GetCredentialCancellationException) {
            throw e
        } catch (e: GetCredentialException) {
            Log.e(TAG, "GetCredentialException: ${e.message}", e)
            throw e
        }
    }

    private suspend fun handleSignInResult(result: GetCredentialResponse): Result<User> {
        return when (val credential = result.credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e(TAG, "Invalid Google ID token", e)
                        Result.failure(e)
                    }
                } else {
                    Result.failure(Exception("Unexpected credential type"))
                }
            }
            else -> Result.failure(Exception("Unexpected credential"))
        }
    }

    private suspend fun firebaseAuthWithGoogle(idToken: String): Result<User> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user?.toUser()

            if (user != null) {
                Log.d(TAG, "Sign in successful: ${user.displayName}")

                // Login to RevenueCat
                try {
                    val rcResult = Purchases.sharedInstance.awaitLogIn(user.id)
                    updateAccessStateFromRevenueCat(rcResult.customerInfo)
                } catch (e: Exception) {
                    Log.e(TAG, "RevenueCat login error", e)
                }

                // Load user data
                initializeAccess()

                Result.success(user)
            } else {
                Result.failure(Exception("Failed to get user info"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase auth failed", e)
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SIGN OUT
    // ═══════════════════════════════════════════════════════════════

    actual suspend fun signOut() {
        // 1. Clear local state first (UI updates immediately)
        _accessState.value = AccessState(isLoading = false, isLoggedIn = false)

        // 2. Logout RevenueCat
        try { Purchases.sharedInstance.awaitLogOut() } catch (_: Exception) {}

        // 3. Sign out Firebase + clear credentials
        try {
            auth.signOut()
            activity?.let {
                val credentialManager = CredentialManager.create(it)
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            }
        } catch (_: Exception) {}

        Log.d(TAG, "Sign out complete")
    }

    // ═══════════════════════════════════════════════════════════════
    // PURCHASE
    // ═══════════════════════════════════════════════════════════════

    actual suspend fun purchaseMonthly(): Boolean {
        val act = activity ?: return false
        val pkg = monthlyPackage ?: return false

        return try {
            val params = PurchaseParams.Builder(act, pkg).build()
            val result = Purchases.sharedInstance.awaitPurchase(params)
            updateAccessStateFromRevenueCat(result.customerInfo)
            _accessState.value.isPro
        } catch (e: Exception) {
            Log.e(TAG, "Purchase error", e)
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // USAGE TRACKING
    // ═══════════════════════════════════════════════════════════════

    actual suspend fun recordUsage(durationSeconds: Long) {
        val state = _accessState.value
        if (!state.isLoggedIn) {
            Log.w(TAG, "Cannot record usage: not logged in")
            return
        }

        val deviceId = deviceManager.getDeviceId()

        if (state.isPro) {
            val success = firestoreManager.addUsage(durationSeconds)
            if (success) {
                Log.d(TAG, "Updated Pro usage: +${durationSeconds}s")
                refreshUserData(deviceId)
            }
        } else {
            val success = firestoreManager.decrementFreeTrial()
            if (success) {
                Log.d(TAG, "Decremented free trial")
                refreshUserData(deviceId)
            }
        }
    }

    private suspend fun refreshUserData(deviceId: String) {
        val userData = firestoreManager.getOrCreateUserData(deviceId) ?: return
        _accessState.value = _accessState.value.copy(
            freeTrialsRemaining = userData.freeTrialsRemaining,
            usedMinutesThisMonth = userData.getUsedMinutes(),
            remainingMinutesThisMonth = userData.getRemainingMinutes()
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    private fun FirebaseUser.toUser() = User(
        id = uid,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl?.toString()
    )
}