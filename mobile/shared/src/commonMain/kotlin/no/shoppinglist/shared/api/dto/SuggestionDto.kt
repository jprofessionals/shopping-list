package no.shoppinglist.shared.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SuggestionResponse(
    val name: String,
    val typicalQuantity: Double,
    val typicalUnit: String?,
    val useCount: Int,
)
