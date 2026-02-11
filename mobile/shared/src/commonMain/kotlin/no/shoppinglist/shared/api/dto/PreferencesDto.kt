package no.shoppinglist.shared.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class PreferencesResponse(
    val smartParsingEnabled: Boolean,
    val defaultQuantity: Double,
    val theme: String,
    val notifyNewList: Boolean,
    val notifyItemAdded: Boolean,
    val notifyNewComment: Boolean,
)

@Serializable
data class UpdatePreferencesRequest(
    val smartParsingEnabled: Boolean? = null,
    val defaultQuantity: Double? = null,
    val theme: String? = null,
    val notifyNewList: Boolean? = null,
    val notifyItemAdded: Boolean? = null,
    val notifyNewComment: Boolean? = null,
)
