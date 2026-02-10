package no.shoppinglist.shared.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ActivityResponse(
    val type: String,
    val actorName: String,
    val targetName: String?,
    val timestamp: String,
)
