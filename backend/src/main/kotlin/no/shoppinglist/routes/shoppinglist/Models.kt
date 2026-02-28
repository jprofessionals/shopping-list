package no.shoppinglist.routes.shoppinglist

import kotlinx.serialization.Serializable
import no.shoppinglist.domain.ListItem
import no.shoppinglist.domain.ListShare
import no.shoppinglist.domain.ShoppingList
import java.util.UUID

@Serializable
data class CreateListRequest(
    val name: String,
    val householdId: String? = null,
    val isPersonal: Boolean = false,
)

@Serializable
data class UpdateListRequest(
    val name: String,
    val isPersonal: Boolean,
)

@Serializable
data class ListResponse(
    val id: String,
    val name: String,
    val householdId: String?,
    val isPersonal: Boolean,
    val createdAt: String,
    val isOwner: Boolean,
    val itemCount: Int = 0,
    val uncheckedCount: Int = 0,
    val previewItems: List<String> = emptyList(),
    val isPinned: Boolean = false,
)

@Serializable
data class ListDetailResponse(
    val id: String,
    val name: String,
    val householdId: String?,
    val isPersonal: Boolean,
    val createdAt: String,
    val isOwner: Boolean,
    val items: List<ItemResponse>,
    val isPinned: Boolean = false,
)

@Serializable
data class ItemResponse(
    val id: String,
    val name: String,
    val quantity: Double,
    val unit: String?,
    val isChecked: Boolean,
    val checkedByName: String?,
    val createdAt: String,
    val recurringItemId: String? = null,
)

@Serializable
data class CreateItemRequest(
    val name: String,
    val quantity: Double = 1.0,
    val unit: String? = null,
)

@Serializable
data class BulkCreateItemsRequest(
    val items: List<CreateItemRequest>,
)

@Serializable
data class UpdateItemRequest(
    val name: String,
    val quantity: Double,
    val unit: String?,
)

@Serializable
data class CreateShareRequest(
    val type: String,
    val accountId: String? = null,
    val permission: String,
    val expirationHours: Int = 24,
)

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

internal const val NAME_MIN_LENGTH = 1
internal const val NAME_MAX_LENGTH = 255
internal const val ITEM_NAME_MIN_LENGTH = 1
internal const val ITEM_NAME_MAX_LENGTH = 1000

internal fun ShoppingList.toListResponse(accountId: UUID) =
    ListResponse(
        id = id.value.toString(),
        name = name,
        householdId = household?.id?.value?.toString(),
        isPersonal = isPersonal,
        createdAt = createdAt.toString(),
        isOwner = owner?.id?.value == accountId,
    )

internal fun ListItem.toResponse() =
    ItemResponse(
        id = id.value.toString(),
        name = name,
        quantity = quantity,
        unit = unit,
        isChecked = isChecked,
        checkedByName = checkedBy?.displayName,
        createdAt = createdAt.toString(),
        recurringItemId = recurringItem?.id?.value?.toString(),
    )

internal fun ListShare.toResponse() =
    ShareResponse(
        id = id.value.toString(),
        type = type.name,
        accountId = account?.id?.value?.toString(),
        accountEmail = account?.email,
        linkToken = linkToken,
        permission = permission.name,
        expiresAt = expiresAt?.toString(),
        createdAt = createdAt.toString(),
    )
