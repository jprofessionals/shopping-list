package no.shoppinglist.shared.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class RecurringItemCreator(
    val id: String,
    val displayName: String,
)

@Serializable
data class RecurringItemResponse(
    val id: String,
    val name: String,
    val quantity: Double,
    val unit: String?,
    val frequency: String,
    val lastPurchased: String?,
    val isActive: Boolean,
    val pausedUntil: String?,
    val createdBy: RecurringItemCreator,
)

@Serializable
data class CreateRecurringItemRequest(
    val name: String,
    val quantity: Double = 1.0,
    val unit: String? = null,
    val frequency: String,
)

@Serializable
data class UpdateRecurringItemRequest(
    val name: String,
    val quantity: Double,
    val unit: String?,
    val frequency: String,
)

@Serializable
data class PauseRecurringItemRequest(
    val until: String? = null,
)
