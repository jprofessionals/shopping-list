package no.shoppinglist.shared.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import no.shoppinglist.shared.api.dto.AddMemberRequest
import no.shoppinglist.shared.api.dto.CreateHouseholdRequest
import no.shoppinglist.shared.api.dto.HouseholdDetailResponse
import no.shoppinglist.shared.api.dto.MemberResponse
import no.shoppinglist.shared.api.dto.UpdateHouseholdRequest
import no.shoppinglist.shared.api.dto.UpdateMemberRoleRequest
import no.shoppinglist.shared.api.routes.HouseholdApi
import no.shoppinglist.shared.cache.HouseholdEntity
import no.shoppinglist.shared.cache.ShoppingListDatabase

class HouseholdRepository(
    private val householdApi: HouseholdApi,
    private val database: ShoppingListDatabase,
) {
    private val householdQueries = database.householdQueries

    val households: Flow<List<HouseholdEntity>> =
        householdQueries.selectAllHouseholds().asFlow().mapToList(Dispatchers.Default)

    suspend fun getAll() {
        try {
            val remoteHouseholds = householdApi.getHouseholds()
            val remoteIds = remoteHouseholds.map { it.id }
            for (household in remoteHouseholds) {
                householdQueries.insertHousehold(
                    id = household.id,
                    name = household.name,
                    createdAt = household.createdAt,
                    memberCount = household.memberCount.toLong(),
                    isOwner = household.isOwner,
                )
            }
            // Remove locally cached households the user no longer has access to
            if (remoteIds.isNotEmpty()) {
                householdQueries.deleteHouseholdsNotIn(remoteIds)
            } else {
                householdQueries.deleteAllHouseholds()
            }
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
        }
    }

    suspend fun getDetail(id: String): HouseholdDetailResponse? {
        return try {
            val detail = householdApi.getHousehold(id)
            // Cache household
            householdQueries.insertHousehold(
                id = detail.id,
                name = detail.name,
                createdAt = detail.createdAt,
                memberCount = detail.members.size.toLong(),
                isOwner = true, // If we can fetch detail, we have access
            )
            // Cache members
            householdQueries.deleteMembersByHouseholdId(id)
            for (member in detail.members) {
                householdQueries.insertMember(
                    id = "${id}_${member.accountId}",
                    householdId = id,
                    accountId = member.accountId,
                    email = member.email,
                    displayName = member.displayName,
                    avatarUrl = member.avatarUrl,
                    role = member.role,
                    joinedAt = member.joinedAt,
                )
            }
            detail
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
            null
        }
    }

    suspend fun create(name: String) {
        try {
            val response = householdApi.createHousehold(CreateHouseholdRequest(name = name))
            householdQueries.insertHousehold(
                id = response.id,
                name = response.name,
                createdAt = response.createdAt,
                memberCount = response.memberCount.toLong(),
                isOwner = response.isOwner,
            )
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
        }
    }

    suspend fun update(id: String, name: String) {
        try {
            val response = householdApi.updateHousehold(
                id = id,
                request = UpdateHouseholdRequest(name = name),
            )
            householdQueries.insertHousehold(
                id = response.id,
                name = response.name,
                createdAt = response.createdAt,
                memberCount = response.memberCount.toLong(),
                isOwner = response.isOwner,
            )
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
            // Optimistic update
            val existing = householdQueries.selectHouseholdById(id).executeAsOneOrNull() ?: return
            householdQueries.insertHousehold(
                id = existing.id,
                name = name,
                createdAt = existing.createdAt,
                memberCount = existing.memberCount,
                isOwner = existing.isOwner,
            )
        }
    }

    suspend fun delete(id: String) {
        try {
            householdApi.deleteHousehold(id)
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
        }
        householdQueries.deleteHouseholdById(id)
        householdQueries.deleteMembersByHouseholdId(id)
    }

    suspend fun addMember(
        householdId: String,
        email: String,
        role: String,
    ): MemberResponse? {
        return try {
            val member = householdApi.addMember(
                householdId = householdId,
                request = AddMemberRequest(email = email, role = role),
            )
            householdQueries.insertMember(
                id = "${householdId}_${member.accountId}",
                householdId = householdId,
                accountId = member.accountId,
                email = member.email,
                displayName = member.displayName,
                avatarUrl = member.avatarUrl,
                role = member.role,
                joinedAt = member.joinedAt,
            )
            member
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
            null
        }
    }

    suspend fun updateMemberRole(
        householdId: String,
        accountId: String,
        role: String,
    ): MemberResponse? {
        return try {
            val member = householdApi.updateMemberRole(
                householdId = householdId,
                accountId = accountId,
                request = UpdateMemberRoleRequest(role = role),
            )
            householdQueries.insertMember(
                id = "${householdId}_${member.accountId}",
                householdId = householdId,
                accountId = member.accountId,
                email = member.email,
                displayName = member.displayName,
                avatarUrl = member.avatarUrl,
                role = member.role,
                joinedAt = member.joinedAt,
            )
            member
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
            null
        }
    }

    suspend fun removeMember(householdId: String, accountId: String) {
        try {
            householdApi.removeMember(householdId, accountId)
        } catch (e: Exception) {
            if (!e.isNetworkError()) throw e
        }
        householdQueries.deleteMember(householdId = householdId, accountId = accountId)
    }
}
