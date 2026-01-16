package com.liftley.vodrop.auth

import android.app.Activity
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

private const val TAG = "FirebaseAuthManager"

data class User(
    val id: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?
)

class FirebaseAuthManager {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private var webClientId: String = ""

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        auth.currentUser?.let { firebaseUser ->
            _currentUser.value = firebaseUser.toUser()
        }

        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser?.toUser()
        }
    }

    fun initialize(webClientId: String) {
        this.webClientId = webClientId
        Log.d(TAG, "Initialized with webClientId: $webClientId")
    }

    /**
     * Sign in with Google using Credential Manager.
     * IMPORTANT: activityContext MUST be an Activity, not Application context!
     */
    suspend fun signInWithGoogle(activity: Activity): Result<User> {
        if (webClientId.isEmpty()) {
            return Result.failure(Exception("Web Client ID not set. Call initialize() first."))
        }

        _isLoading.value = true

        return try {
            // Create CredentialManager with Activity context
            val credentialManager = CredentialManager.create(activity)

            // First try with authorized accounts (faster if user already signed in before)
            Log.d(TAG, "Attempting sign-in with authorized accounts first...")
            val result = tryGetCredential(credentialManager, activity, filterByAuthorized = true)
                ?: run {
                    Log.d(TAG, "No authorized accounts, trying all accounts...")
                    tryGetCredential(credentialManager, activity, filterByAuthorized = false)
                }

            if (result != null) {
                handleSignInResult(result)
            } else {
                Result.failure(Exception("Could not get Google credentials. Please try again."))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Sign in cancelled by user")
            Result.failure(Exception("Sign in cancelled"))
        } catch (e: Exception) {
            Log.e(TAG, "Sign in failed: ${e.message}", e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun tryGetCredential(
        credentialManager: CredentialManager,
        activity: Activity,
        filterByAuthorized: Boolean
    ): GetCredentialResponse? {
        return try {
            // Generate a unique nonce for each request (required for security)
            val nonce = UUID.randomUUID().toString()

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(filterByAuthorized)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(filterByAuthorized) // Auto-select only for authorized
                .setNonce(nonce) // CRITICAL: Must set nonce!
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            Log.d(TAG, "Making credential request (filterByAuthorized=$filterByAuthorized, nonce=$nonce)")

            credentialManager.getCredential(
                context = activity,  // MUST be Activity, not Context
                request = request
            )
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No credential found with filterByAuthorized=$filterByAuthorized: ${e.message}")
            null
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "User cancelled credential request")
            throw e // Re-throw to be handled by caller
        } catch (e: GetCredentialException) {
            Log.e(TAG, "GetCredentialException (type=${e.type}): ${e.message}", e)
            if (!filterByAuthorized) {
                // If this was the second attempt, throw to show error
                throw e
            }
            null
        }
    }

    private suspend fun handleSignInResult(result: GetCredentialResponse): Result<User> {
        return when (val credential = result.credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        Log.d(TAG, "Got Google ID token, authenticating with Firebase...")
                        firebaseAuthWithGoogle(googleIdTokenCredential.idToken)
                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e(TAG, "Invalid Google ID token", e)
                        Result.failure(e)
                    }
                } else {
                    Log.e(TAG, "Unexpected credential type: ${credential.type}")
                    Result.failure(Exception("Unexpected credential type"))
                }
            }
            else -> {
                Log.e(TAG, "Unexpected credential: ${credential::class.java}")
                Result.failure(Exception("Unexpected credential"))
            }
        }
    }

    private suspend fun firebaseAuthWithGoogle(idToken: String): Result<User> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user?.toUser()

            if (user != null) {
                _currentUser.value = user
                Log.d(TAG, "Sign in successful: ${user.displayName}")
                Result.success(user)
            } else {
                Result.failure(Exception("Failed to get user info"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase auth failed", e)
            Result.failure(e)
        }
    }

    suspend fun signOut(activity: Activity) {
        try {
            auth.signOut()
            val credentialManager = CredentialManager.create(activity)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            _currentUser.value = null
            Log.d(TAG, "Sign out successful")
        } catch (e: Exception) {
            Log.e(TAG, "Sign out error", e)
        }
    }

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    private fun FirebaseUser.toUser() = User(
        id = uid,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl?.toString()
    )
}