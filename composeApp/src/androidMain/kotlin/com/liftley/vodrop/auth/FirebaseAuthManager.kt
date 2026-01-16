package com.liftley.vodrop.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
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

private const val TAG = "FirebaseAuthManager"

data class User(
    val id: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?
)

class FirebaseAuthManager(context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val credentialManager = CredentialManager.create(context)

    private var webClientId: String = ""

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Check if user is already logged in
        auth.currentUser?.let { firebaseUser ->
            _currentUser.value = firebaseUser.toUser()
        }

        // Listen for auth state changes
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser?.toUser()
        }
    }

    /**
     * Initialize with Web Client ID from google-services.json
     */
    fun initialize(webClientId: String) {
        this.webClientId = webClientId
    }

    /**
     * Sign in with Google using Credential Manager
     * Call this from Activity/Fragment context
     */
    suspend fun signInWithGoogle(activityContext: Context): Result<User> {
        if (webClientId.isEmpty()) {
            return Result.failure(Exception("Web Client ID not set. Call initialize() first."))
        }

        _isLoading.value = true

        return try {
            // Create Google ID option
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            // Create credential request
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            // Get credential
            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )

            handleSignInResult(result)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Get credential failed", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Sign in failed", e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
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

    /**
     * Sign out from both Firebase and clear credentials
     */
    suspend fun signOut() {
        try {
            auth.signOut()
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            _currentUser.value = null
            Log.d(TAG, "Sign out successful")
        } catch (e: Exception) {
            Log.e(TAG, "Sign out error", e)
        }
    }

    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean = auth.currentUser != null

    /**
     * Get current Firebase user ID
     */
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    private fun FirebaseUser.toUser() = User(
        id = uid,
        email = email,
        displayName = displayName,
        photoUrl = photoUrl?.toString()
    )
}