package com.liftley.vodrop.domain.model

/**
 * Common user model for cross-platform auth state
 */
data class User(
    val id: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
    val isPro: Boolean = false
)