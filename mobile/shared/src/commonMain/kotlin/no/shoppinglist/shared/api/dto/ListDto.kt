package no.shoppinglist.shared.api.dto

import kotlinx.serialization.Serializable

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
)

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
data class ClearCheckedResponse(
    val deletedItemIds: List<String>,
)
