package no.shoppinglist.shared.repository

import no.shoppinglist.shared.api.dto.CreateShareRequest
import no.shoppinglist.shared.api.dto.ShareResponse
import no.shoppinglist.shared.api.dto.SharedListResponse
import no.shoppinglist.shared.api.routes.ShareApi

class ShareRepository(
    private val shareApi: ShareApi,
) {
    suspend fun getShares(listId: String): List<ShareResponse> =
        shareApi.getShares(listId)

    suspend fun createShare(
        listId: String,
        type: String,
        permission: String,
        accountId: String? = null,
        expirationHours: Int = 24,
    ): ShareResponse =
        shareApi.createShare(
            listId = listId,
            request = CreateShareRequest(
                type = type,
                accountId = accountId,
                permission = permission,
                expirationHours = expirationHours,
            ),
        )

    suspend fun deleteShare(listId: String, shareId: String) =
        shareApi.deleteShare(listId, shareId)

    suspend fun getSharedList(token: String): SharedListResponse =
        shareApi.getSharedList(token)
}
