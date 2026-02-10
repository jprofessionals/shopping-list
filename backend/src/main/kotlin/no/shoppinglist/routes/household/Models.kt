package no.shoppinglist.routes.household

import kotlinx.serialization.Serializable
import no.shoppinglist.service.MemberInfo

@Serializable
data class CreateHouseholdRequest(
    val name: String,
)

@Serializable
data class UpdateHouseholdRequest(
    val name: String,
)

@Serializable
data class HouseholdResponse(
    val id: String,
    val name: String,
    val createdAt: String,
    val memberCount: Int,
    val isOwner: Boolean,
)

@Serializable
data class HouseholdDetailResponse(
    val id: String,
    val name: String,
    val createdAt: String,
    val members: List<MemberResponse>,
)

@Serializable
data class MemberResponse(
    val accountId: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val role: String,
    val joinedAt: String,
)

@Serializable
data class AddMemberRequest(
    val email: String,
    val role: String = "MEMBER",
)

@Serializable
data class UpdateMemberRoleRequest(
    val role: String,
)

internal fun MemberInfo.toResponse() =
    MemberResponse(
        accountId = accountId.toString(),
        email = email,
        displayName = displayName,
        avatarUrl = avatarUrl,
        role = role.name,
        joinedAt = joinedAt.toString(),
    )
