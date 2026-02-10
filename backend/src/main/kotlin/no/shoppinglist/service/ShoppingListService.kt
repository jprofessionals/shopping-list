package no.shoppinglist.service

import no.shoppinglist.domain.Account
import no.shoppinglist.domain.CommentTargetType
import no.shoppinglist.domain.Comments
import no.shoppinglist.domain.Household
import no.shoppinglist.domain.HouseholdMembership
import no.shoppinglist.domain.HouseholdMemberships
import no.shoppinglist.domain.ListItem
import no.shoppinglist.domain.ListItems
import no.shoppinglist.domain.ListShare
import no.shoppinglist.domain.ListShares
import no.shoppinglist.domain.SharePermission
import no.shoppinglist.domain.ShareType
import no.shoppinglist.domain.ShoppingList
import no.shoppinglist.domain.ShoppingLists
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class ShoppingListService(
    private val db: Database,
) {
    fun create(
        name: String,
        ownerId: UUID,
        householdId: UUID?,
        isPersonal: Boolean,
    ): ShoppingList =
        transaction(db) {
            val owner =
                Account.findById(ownerId)
                    ?: throw IllegalArgumentException("Account not found: $ownerId")
            val household =
                householdId?.let {
                    Household.findById(it) ?: throw IllegalArgumentException("Household not found: $it")
                }

            ShoppingList.new {
                this.name = name
                this.owner = owner
                this.household = household
                this.isPersonal = isPersonal
                this.createdAt = Instant.now()
            }
        }

    fun findById(id: UUID): ShoppingList? =
        transaction(db) {
            ShoppingList.findById(id)
        }

    @Suppress("ReturnCount")
    fun getPermission(
        listId: UUID,
        accountId: UUID?,
        linkToken: String?,
    ): SharePermission? =
        transaction(db) {
            val list = ShoppingList.findById(listId) ?: return@transaction null

            checkOwnerAccess(list, accountId)?.let { return@transaction it }
            checkHouseholdAccess(list, accountId)?.let { return@transaction it }
            checkUserShareAccess(listId, accountId)?.let { return@transaction it }
            checkLinkShareAccess(listId, linkToken)?.let { return@transaction it }

            null
        }

    private fun checkOwnerAccess(
        list: ShoppingList,
        accountId: UUID?,
    ): SharePermission? = if (accountId != null && list.owner.id.value == accountId) SharePermission.WRITE else null

    private fun checkHouseholdAccess(
        list: ShoppingList,
        accountId: UUID?,
    ): SharePermission? {
        if (accountId == null || list.household == null || list.isPersonal) return null
        val isMember =
            HouseholdMembership
                .find {
                    (HouseholdMemberships.household eq list.household!!.id) and
                        (HouseholdMemberships.account eq accountId)
                }.firstOrNull() != null
        return if (isMember) SharePermission.WRITE else null
    }

    private fun checkUserShareAccess(
        listId: UUID,
        accountId: UUID?,
    ): SharePermission? {
        if (accountId == null) return null
        return ListShare
            .find {
                (ListShares.list eq listId) and
                    (ListShares.type eq ShareType.USER) and
                    (ListShares.account eq accountId)
            }.firstOrNull()
            ?.permission
    }

    @Suppress("ReturnCount")
    private fun checkLinkShareAccess(
        listId: UUID,
        linkToken: String?,
    ): SharePermission? {
        if (linkToken == null) return null
        val linkShare =
            ListShare
                .find {
                    (ListShares.list eq listId) and
                        (ListShares.type eq ShareType.LINK) and
                        (ListShares.linkToken eq linkToken)
                }.firstOrNull() ?: return null
        val isValid = linkShare.expiresAt == null || linkShare.expiresAt!! > Instant.now()
        return if (isValid) linkShare.permission else null
    }

    fun update(
        id: UUID,
        name: String,
        isPersonal: Boolean,
    ): ShoppingList? =
        transaction(db) {
            val list = ShoppingList.findById(id) ?: return@transaction null
            list.name = name
            list.isPersonal = isPersonal
            list
        }

    fun delete(id: UUID): Boolean =
        transaction(db) {
            Comments.deleteWhere { (Comments.targetType eq CommentTargetType.LIST) and (Comments.targetId eq id) }
            ListShares.deleteWhere { list eq id }
            ListItems.deleteWhere { ListItems.list eq id }
            val deleted = ShoppingLists.deleteWhere { ShoppingLists.id eq id }
            deleted > 0
        }

    fun findAccessibleByAccount(accountId: UUID): List<ShoppingList> =
        transaction(db) {
            val owned = ShoppingList.find { ShoppingLists.owner eq accountId }.toList()

            val householdIds =
                HouseholdMembership
                    .find { HouseholdMemberships.account eq accountId }
                    .map { it.household.id.value }

            val householdLists =
                if (householdIds.isNotEmpty()) {
                    ShoppingList
                        .find {
                            (ShoppingLists.household inList householdIds) and
                                (ShoppingLists.isPersonal eq false) and
                                (ShoppingLists.owner neq accountId)
                        }.toList()
                } else {
                    emptyList()
                }

            val sharedLists =
                ListShare
                    .find {
                        (ListShares.type eq ShareType.USER) and
                            (ListShares.account eq accountId)
                    }.map { it.list }

            (owned + householdLists + sharedLists).distinctBy { it.id.value }
        }

    fun findAccessibleByAccountWithSummary(
        accountId: UUID,
        pinnedListIds: Set<UUID>,
    ): List<ListSummary> =
        transaction(db) {
            val lists = findAccessibleByAccount(accountId)
            lists.map { list ->
                val items = ListItem.find { ListItems.list eq list.id }.toList()
                ListSummary(
                    list = list,
                    itemCount = items.size,
                    uncheckedCount = items.count { !it.isChecked },
                    previewItems = items.filter { !it.isChecked }.take(3).map { it.name },
                    isPinned = pinnedListIds.contains(list.id.value),
                )
            }
        }
}

data class ListSummary(
    val list: ShoppingList,
    val itemCount: Int,
    val uncheckedCount: Int,
    val previewItems: List<String>,
    val isPinned: Boolean,
)
