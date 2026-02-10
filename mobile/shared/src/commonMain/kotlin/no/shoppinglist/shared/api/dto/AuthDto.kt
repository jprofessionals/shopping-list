package no.shoppinglist.shared.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthConfigResponse(
    val googleEnabled: Boolean,
    val localEnabled: Boolean,
    val googleClientId: String?,
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
)

@Serializable
data class LoginResponse(
    val token: String,
    val refreshToken: String,
    val user: UserResponse,
)

@Serializable
data class RefreshResponse(
    val token: String,
    val refreshToken: String,
)

@Serializable
data class LocalLoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class LocalRegisterRequest(
    val email: String,
    val displayName: String,
    val password: String,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)
