package no.shoppinglist.shared.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ShareResponse(
    val id: String,
    val type: String,
    val accountId: String?,
    val accountEmail: String?,
    val linkToken: String?,
    val permission: String,
    val expiresAt: String?,
    val createdAt: String,
)

@Serializable
data class CreateShareRequest(
    val type: String,
    val accountId: String? = null,
    val permission: String,
    val expirationHours: Int = 24,
)

@Serializable
data class SharedListResponse(
    val id: String,
    val name: String,
    val permission: String,
    val items: List<SharedItemResponse>,
)

@Serializable
data class SharedItemResponse(
    val id: String,
    val name: String,
    val quantity: Double,
    val unit: String?,
    val isChecked: Boolean,
)
