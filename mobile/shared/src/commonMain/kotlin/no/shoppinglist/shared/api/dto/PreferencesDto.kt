package no.shoppinglist.shared.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class PreferencesResponse(
    val smartParsingEnabled: Boolean,
    val defaultQuantity: Double,
    val theme: String,
)

@Serializable
data class UpdatePreferencesRequest(
    val smartParsingEnabled: Boolean? = null,
    val defaultQuantity: Double? = null,
    val theme: String? = null,
)
