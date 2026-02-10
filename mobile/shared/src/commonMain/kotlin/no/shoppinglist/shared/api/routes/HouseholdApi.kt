package no.shoppinglist.shared.api.routes

import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import no.shoppinglist.shared.api.ApiClient
import no.shoppinglist.shared.api.dto.AddMemberRequest
import no.shoppinglist.shared.api.dto.CreateHouseholdRequest
import no.shoppinglist.shared.api.dto.HouseholdDetailResponse
import no.shoppinglist.shared.api.dto.HouseholdResponse
import no.shoppinglist.shared.api.dto.MemberResponse
import no.shoppinglist.shared.api.dto.UpdateHouseholdRequest
import no.shoppinglist.shared.api.dto.UpdateMemberRoleRequest

class HouseholdApi(private val apiClient: ApiClient) {

    suspend fun getHouseholds(): List<HouseholdResponse> =
        apiClient.authenticatedRequest {
            method = HttpMethod.Get
            url.appendPathSegments("households")
        }

    suspend fun getHousehold(id: String): HouseholdDetailResponse =
        apiClient.authenticatedRequest {
            method = HttpMethod.Get
            url.appendPathSegments("households", id)
        }

    suspend fun createHousehold(request: CreateHouseholdRequest): HouseholdResponse =
        apiClient.authenticatedRequest {
            method = HttpMethod.Post
            url.appendPathSegments("households")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun updateHousehold(
        id: String,
        request: UpdateHouseholdRequest,
    ): HouseholdResponse =
        apiClient.authenticatedRequest {
            method = HttpMethod.Patch
            url.appendPathSegments("households", id)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun deleteHousehold(id: String) {
        apiClient.authenticatedRequest<Unit> {
            method = HttpMethod.Delete
            url.appendPathSegments("households", id)
        }
    }

    suspend fun addMember(
        householdId: String,
        request: AddMemberRequest,
    ): MemberResponse =
        apiClient.authenticatedRequest {
            method = HttpMethod.Post
            url.appendPathSegments("households", householdId, "members")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun updateMemberRole(
        householdId: String,
        accountId: String,
        request: UpdateMemberRoleRequest,
    ): MemberResponse =
        apiClient.authenticatedRequest {
            method = HttpMethod.Patch
            url.appendPathSegments("households", householdId, "members", accountId)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun removeMember(householdId: String, accountId: String) {
        apiClient.authenticatedRequest<Unit> {
            method = HttpMethod.Delete
            url.appendPathSegments("households", householdId, "members", accountId)
        }
    }
}
