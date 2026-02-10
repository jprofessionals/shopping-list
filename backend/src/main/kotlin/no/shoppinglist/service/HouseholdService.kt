package no.shoppinglist.service

import no.shoppinglist.domain.Account
import no.shoppinglist.domain.CommentTargetType
import no.shoppinglist.domain.Comments
import no.shoppinglist.domain.Household
import no.shoppinglist.domain.HouseholdMembership
import no.shoppinglist.domain.HouseholdMemberships
import no.shoppinglist.domain.Households
import no.shoppinglist.domain.MembershipRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

data class MemberInfo(
    val accountId: UUID,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val role: MembershipRole,
    val joinedAt: Instant,
)

class HouseholdService(
    private val db: Database,
) {
    fun create(
        name: String,
        ownerId: UUID,
    ): Household =
        transaction(db) {
            val household =
                Household.new {
                    this.name = name
                    this.createdAt = Instant.now()
                }

            val owner =
                Account.findById(ownerId)
                    ?: throw IllegalArgumentException("Account not found: $ownerId")

            HouseholdMembership.new {
                this.account = owner
                this.household = household
                this.role = MembershipRole.OWNER
                this.joinedAt = Instant.now()
            }

            household
        }

    fun findById(id: UUID): Household? =
        transaction(db) {
            Household.findById(id)
        }

    fun findByAccountId(accountId: UUID): List<Household> =
        transaction(db) {
            HouseholdMembership
                .find { HouseholdMemberships.account eq accountId }
                .map { it.household }
        }

    fun update(
        id: UUID,
        name: String,
    ): Household? {
        return transaction(db) {
            val household = Household.findById(id) ?: return@transaction null
            household.name = name
            household
        }
    }

    fun delete(id: UUID): Boolean =
        transaction(db) {
            Comments.deleteWhere { (Comments.targetType eq CommentTargetType.HOUSEHOLD) and (Comments.targetId eq id) }
            HouseholdMemberships.deleteWhere { household eq id }
            val deleted = Households.deleteWhere { Households.id eq id }
            deleted > 0
        }

    fun getMembers(householdId: UUID): List<MemberInfo> =
        transaction(db) {
            HouseholdMembership
                .find { HouseholdMemberships.household eq householdId }
                .map { membership ->
                    MemberInfo(
                        accountId = membership.account.id.value,
                        email = membership.account.email,
                        displayName = membership.account.displayName,
                        avatarUrl = membership.account.avatarUrl,
                        role = membership.role,
                        joinedAt = membership.joinedAt,
                    )
                }
        }

    fun isOwner(
        householdId: UUID,
        accountId: UUID,
    ): Boolean =
        transaction(db) {
            HouseholdMembership
                .find {
                    (HouseholdMemberships.household eq householdId) and
                        (HouseholdMemberships.account eq accountId) and
                        (HouseholdMemberships.role eq MembershipRole.OWNER)
                }.firstOrNull() != null
        }

    fun isMember(
        householdId: UUID,
        accountId: UUID,
    ): Boolean =
        transaction(db) {
            HouseholdMembership
                .find {
                    (HouseholdMemberships.household eq householdId) and
                        (HouseholdMemberships.account eq accountId)
                }.firstOrNull() != null
        }

    fun addMember(
        householdId: UUID,
        accountId: UUID,
        role: MembershipRole,
    ): HouseholdMembership? {
        return transaction(db) {
            val household = Household.findById(householdId) ?: return@transaction null
            val account = Account.findById(accountId) ?: return@transaction null

            if (isMemberInternal(householdId, accountId)) {
                return@transaction null
            }

            HouseholdMembership.new {
                this.account = account
                this.household = household
                this.role = role
                this.joinedAt = Instant.now()
            }
        }
    }

    fun removeMember(
        householdId: UUID,
        accountId: UUID,
    ): Boolean =
        transaction(db) {
            val deleted =
                HouseholdMemberships.deleteWhere {
                    (household eq householdId) and (account eq accountId)
                }
            deleted > 0
        }

    fun updateMemberRole(
        householdId: UUID,
        accountId: UUID,
        newRole: MembershipRole,
    ): Boolean {
        return transaction(db) {
            val membership =
                HouseholdMembership
                    .find {
                        (HouseholdMemberships.household eq householdId) and
                            (HouseholdMemberships.account eq accountId)
                    }.firstOrNull() ?: return@transaction false

            membership.role = newRole
            true
        }
    }

    private fun isMemberInternal(
        householdId: UUID,
        accountId: UUID,
    ): Boolean =
        HouseholdMembership
            .find {
                (HouseholdMemberships.household eq householdId) and
                    (HouseholdMemberships.account eq accountId)
            }.firstOrNull() != null
}
