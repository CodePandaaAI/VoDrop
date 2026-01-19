package com.liftley.vodrop.auth

/**
 * User data model - shared across platforms
 */
data class User(
    val id: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?
)