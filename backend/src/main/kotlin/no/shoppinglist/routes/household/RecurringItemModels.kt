package no.shoppinglist.routes.household

import kotlinx.serialization.Serializable
import no.shoppinglist.domain.RecurringItem

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
data class RecurringItemCreator(
    val id: String,
    val displayName: String,
)

internal fun RecurringItem.toResponse() =
    RecurringItemResponse(
        id = id.value.toString(),
        name = name,
        quantity = quantity,
        unit = unit,
        frequency = frequency.name,
        lastPurchased = lastPurchased?.toString(),
        isActive = isActive,
        pausedUntil = pausedUntil?.toString(),
        createdBy =
            RecurringItemCreator(
                id = createdBy.id.value.toString(),
                displayName = createdBy.displayName,
            ),
    )
